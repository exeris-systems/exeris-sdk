package eu.exeris.sdk.brief;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.RuleMetadata;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Builds the set of known source elements {@link BriefReadiness} resolves references against,
 * the way a consumer holding the project's emitted metadata would. It lives in the tests because
 * the brief package may use only enums from the source model; the caller owns this mapping.
 */
final class SourceReferences {

    private SourceReferences() {
    }

    static Set<FulfilledBy> of(Collection<DomainMetadata> entities) {
        Set<FulfilledBy> out = new LinkedHashSet<>();
        for (DomainMetadata entity : entities) {
            String qualified = entity.packageName() + "." + entity.entityName();
            out.add(new FulfilledBy(FulfilledBy.Kind.ENTITY, qualified));
            for (ActionMetadata action : entity.actions()) {
                out.add(new FulfilledBy(FulfilledBy.Kind.ACTION, qualified + "#" + action.name()));
            }
            for (RuleMetadata rule : entity.rules()) {
                out.add(new FulfilledBy(FulfilledBy.Kind.RULE, qualified + "#" + rule.name()));
            }
            if (entity.sagaMetadata() != null) {
                out.add(new FulfilledBy(FulfilledBy.Kind.SAGA, qualified + "#" + entity.sagaMetadata().name()));
            }
        }
        return out;
    }
}
