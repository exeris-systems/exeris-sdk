package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.mutation.MutationOp;
import eu.exeris.sdk.sourcemodel.mutation.MutationPath;
import eu.exeris.sdk.sourcemodel.mutation.MutationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AST-level three-way drift detection (ADR-042 slice 2). Decides, for a
 * {@link MutationOp} a tool wants to apply, whether a user edit since the last
 * codegen run collides with it — by comparing the <em>current</em> source model
 * (typically {@code SourceModelReader.read(currentSource)}) against the
 * <em>baseline</em> the op was computed against (the last-codegen
 * {@code exeris-metadata/<entity>.json}, deserialized into a
 * {@link DomainMetadata}).
 *
 * <h2>The rule (ADR-042, obligation 4)</h2>
 * <p>A path has <strong>drifted</strong> when its current value differs from the
 * baseline. An op <strong>conflicts</strong> iff its target path drifted to a
 * value that differs from <em>both</em> the baseline and the op's own intent. A
 * <strong>convergent</strong> edit — the user already landed on what the op
 * wants — is a {@link MutationResult.Success}, not a conflict. Sibling paths
 * never conflict.
 *
 * <h2>Scope of this slice</h2>
 * <p>Drift is detected at <strong>member granularity</strong> — the field /
 * relationship / action paths the slice-1 ops target (the path grammar in
 * {@link MutationPath}). Conflict is decided at the op's target path: this is
 * the member-only reduction of the ADR ancestor-or-descendant rule, and it is
 * correct here because no other addressable path overlaps a member path. An op
 * targeting a member does not intend to change domain-level attributes, so an
 * entity-root attribute edit is orthogonal (never a conflict for a member op),
 * and a structural change that <em>does</em> affect the member — e.g. the user
 * removing the whole entity — surfaces as member-level drift (baseline present,
 * current absent). The ancestor-or-descendant generalization
 * ({@link MutationPath#isSameOrAncestorOf}) becomes load-bearing only when
 * nesting ops arrive (an entity-root op, or capability sub-paths). Coarse
 * whole-file / domain-attribute drift is the {@code sourceDigest} baseline-trust
 * mechanism's job (slice 3): any edit the digest can't reconcile yields
 * {@code NO_BASELINE} before detection runs.
 *
 * <p>This class is <strong>pure over two {@link DomainMetadata} values</strong>:
 * it does not read source, recompute digests, or decide {@code NO_BASELINE}
 * (slice 3 wires those in), and it does not mutate source (slice 4 applies).
 * It reports {@code SUCCESS} / {@code CONFLICT}, and {@code VALIDATION_ERROR}
 * for the structural rejections it can see without a baseline comparison
 * (unparseable path, path-kind/op mismatch, path targeting a different entity).
 * The full {@code VALIDATION_ERROR} trigger set is fixed in slice 4.
 *
 * @since 0.5.0
 */
public final class SourceModelConflictDetector {

    /**
     * Detect whether {@code op} conflicts with a user edit, comparing
     * {@code current} (the live source model) against {@code baseline} (what the
     * op was computed against).
     *
     * @return {@link MutationResult.Success} when the op applies without colliding
     *         with a non-convergent user edit; {@link MutationResult.Conflict}
     *         when it does; {@link MutationResult.ValidationError} when the op is
     *         structurally inapplicable to this comparison.
     */
    public MutationResult detect(MutationOp op, DomainMetadata baseline, DomainMetadata current) {
        Objects.requireNonNull(op, "op is required");
        Objects.requireNonNull(baseline, "baseline is required");
        Objects.requireNonNull(current, "current is required");

        MutationPath path;
        try {
            path = MutationPath.parse(op.path());
        } catch (IllegalArgumentException invalid) {
            return new MutationResult.ValidationError(op.path(), "unresolvable target path: " + invalid.getMessage());
        }

        if (!path.entity().equals(current.entityName())) {
            return new MutationResult.ValidationError(op.path(),
                    "path targets entity '" + path.entity() + "' but the comparison is for '"
                            + current.entityName() + "'");
        }

        MutationPath.TargetKind expected = expectedKind(op);
        if (path.kind() != expected) {
            return new MutationResult.ValidationError(op.path(),
                    op.getClass().getSimpleName() + " requires a " + expected + " path, got " + path.kind());
        }

        return switch (op) {
            case MutationOp.AddField a -> field(op, baseline, current,
                    cur -> cur.isPresent() && cur.get().equals(a.field()), render(a.field()));
            case MutationOp.RemoveField ignored -> field(op, baseline, current,
                    Optional::isEmpty, null);
            case MutationOp.ChangeFieldType c -> field(op, baseline, current,
                    cur -> cur.isPresent() && cur.get().type().equals(c.newType()), "type=" + c.newType());
            case MutationOp.RenameField r -> renameField(r, baseline, current);
            case MutationOp.AddRelationship a -> relationship(op, baseline, current,
                    cur -> cur.isPresent() && cur.get().equals(a.relationship()), render(a.relationship()));
            case MutationOp.RemoveRelationship ignored -> relationship(op, baseline, current,
                    Optional::isEmpty, null);
            case MutationOp.ChangeRelationshipCardinality c -> relationship(op, baseline, current,
                    cur -> cur.isPresent() && cur.get().type() == c.newCardinality(),
                    "type=" + c.newCardinality());
            case MutationOp.AddAction a -> action(op, baseline, current,
                    cur -> cur.isPresent() && cur.get().equals(a.action()), render(a.action()));
            case MutationOp.RemoveAction ignored -> action(op, baseline, current,
                    Optional::isEmpty, null);
        };
    }

    /** Detect each op independently against the same baseline/current pair, preserving order. */
    public List<MutationResult> detect(List<MutationOp> ops, DomainMetadata baseline, DomainMetadata current) {
        Objects.requireNonNull(ops, "ops is required");
        List<MutationResult> results = new ArrayList<>(ops.size());
        for (MutationOp op : ops) {
            results.add(detect(op, baseline, current));
        }
        return results;
    }

    // ---- per-member-kind drift checks ------------------------------------

    private MutationResult field(MutationOp op, DomainMetadata baseline, DomainMetadata current,
                                 ConvergencePredicate<FieldMetadata> convergent, String intended) {
        String name = MutationPath.parse(op.path()).member();
        Optional<FieldMetadata> base = baseline.findField(name);
        Optional<FieldMetadata> cur = current.findField(name);
        return verdict(op.path(), base, cur, convergent.test(cur), intended);
    }

    private MutationResult relationship(MutationOp op, DomainMetadata baseline, DomainMetadata current,
                                        ConvergencePredicate<RelationshipMetadata> convergent, String intended) {
        String name = MutationPath.parse(op.path()).member();
        Optional<RelationshipMetadata> base = findRelationship(baseline, name);
        Optional<RelationshipMetadata> cur = findRelationship(current, name);
        return verdict(op.path(), base, cur, convergent.test(cur), intended);
    }

    private MutationResult action(MutationOp op, DomainMetadata baseline, DomainMetadata current,
                                  ConvergencePredicate<ActionMetadata> convergent, String intended) {
        String name = MutationPath.parse(op.path()).member();
        Optional<ActionMetadata> base = findAction(baseline, name);
        Optional<ActionMetadata> cur = findAction(current, name);
        return verdict(op.path(), base, cur, convergent.test(cur), intended);
    }

    /**
     * Rename spans two paths: the field leaves its old name and reappears under
     * the new one. Convergent iff the current source already reflects the rename
     * (old name gone, new name carries the old field's shape). A conflict arises
     * when the old field drifted in a way the rename can't absorb, or the target
     * name is already occupied by a different field.
     */
    private MutationResult renameField(MutationOp.RenameField op, DomainMetadata baseline, DomainMetadata current) {
        String oldName = MutationPath.parse(op.path()).member();
        String newName = op.newName();
        Optional<FieldMetadata> baseOld = baseline.findField(oldName);
        Optional<FieldMetadata> curOld = current.findField(oldName);
        Optional<FieldMetadata> curNew = current.findField(newName);
        Optional<FieldMetadata> renamed = baseOld.map(f -> withName(f, newName));

        boolean reflectsRename = curOld.isEmpty() && curNew.isPresent() && curNew.equals(renamed);
        if (reflectsRename) {
            return new MutationResult.Success(op.path());
        }

        boolean oldDrift = !Objects.equals(baseOld, curOld);
        boolean targetOccupied = baseline.findField(newName).isEmpty() && curNew.isPresent();
        if (!oldDrift && !targetOccupied) {
            return new MutationResult.Success(op.path());
        }

        // Non-convergent: surface the more specific collision.
        if (targetOccupied) {
            return new MutationResult.Conflict(MutationPath.field(current.entityName(), newName).toString(),
                    null, render(curNew), "rename '" + oldName + "' to '" + newName + "'");
        }
        return new MutationResult.Conflict(op.path(), render(baseOld), render(curOld), "name=" + newName);
    }

    // ---- shared verdict + helpers ----------------------------------------

    private <T> MutationResult verdict(String path, Optional<T> baseline, Optional<T> current,
                                       boolean convergent, String intended) {
        boolean drifted = !baseline.equals(current);
        if (!drifted || convergent) {
            return new MutationResult.Success(path);
        }
        return new MutationResult.Conflict(path, render(baseline), render(current), intended);
    }

    private MutationPath.TargetKind expectedKind(MutationOp op) {
        return switch (op) {
            case MutationOp.AddField ignored -> MutationPath.TargetKind.FIELD;
            case MutationOp.RemoveField ignored -> MutationPath.TargetKind.FIELD;
            case MutationOp.RenameField ignored -> MutationPath.TargetKind.FIELD;
            case MutationOp.ChangeFieldType ignored -> MutationPath.TargetKind.FIELD;
            case MutationOp.AddRelationship ignored -> MutationPath.TargetKind.RELATIONSHIP;
            case MutationOp.RemoveRelationship ignored -> MutationPath.TargetKind.RELATIONSHIP;
            case MutationOp.ChangeRelationshipCardinality ignored -> MutationPath.TargetKind.RELATIONSHIP;
            case MutationOp.AddAction ignored -> MutationPath.TargetKind.ACTION;
            case MutationOp.RemoveAction ignored -> MutationPath.TargetKind.ACTION;
        };
    }

    private Optional<RelationshipMetadata> findRelationship(DomainMetadata domain, String fieldName) {
        return domain.relationships().stream()
                .filter(r -> fieldName.equals(r.fieldName()))
                .findFirst();
    }

    private Optional<ActionMetadata> findAction(DomainMetadata domain, String name) {
        return domain.actions().stream()
                .filter(a -> name.equals(a.name()))
                .findFirst();
    }

    private FieldMetadata withName(FieldMetadata field, String newName) {
        return new FieldMetadata(newName, field.type(), field.columnName(), field.displayName(),
                field.description(), field.required(), field.unique(), field.indexed(), field.searchable(),
                field.sortable(), field.filterable(), field.audited(), field.readOnly(), field.hidden(),
                field.defaultValue(), field.minLength(), field.maxLength(), field.min(), field.max(),
                field.pattern(), field.format(), field.enumType(), field.computed(), field.computedFrom(),
                field.inCreate(), field.inUpdate());
    }

    private String render(Optional<?> member) {
        return member.map(Object::toString).orElse(null);
    }

    private String render(Object member) {
        return member == null ? null : member.toString();
    }

    @FunctionalInterface
    private interface ConvergencePredicate<T> {
        boolean test(Optional<T> currentMember);
    }
}
