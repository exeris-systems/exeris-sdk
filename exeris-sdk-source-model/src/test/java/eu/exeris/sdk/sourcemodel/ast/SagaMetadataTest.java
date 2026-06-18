package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SagaMetadata + SagaStepMetadata + nested config")
class SagaMetadataTest {

    @Test
    void simpleSagaFactory() {
        SagaMetadata s = SagaMetadata.simple("OrderApproval");
        assertThat(s.name()).isEqualTo("OrderApproval");
        assertThat(s.version()).isEqualTo(1);
        assertThat(s.compensationStrategy()).isEqualTo(SagaMetadata.CompensationStrategy.ALL_OR_NOTHING);
        assertThat(s.compensationOrder()).isEqualTo(SagaMetadata.CompensationOrder.REVERSE);
        assertThat(s.timeout()).isEqualTo("PT30M");
        assertThat(s.compensationTimeout()).isEqualTo("PT10M");
        assertThat(s.maxRetries()).isEqualTo(3);
        assertThat(s.retryBackoff()).isEqualTo("PT1S");
        assertThat(s.persistent()).isTrue();
        assertThat(s.hasSteps()).isFalse();
        assertThat(s.hasTrigger()).isFalse();
    }

    @Test
    void hasStepsAndTriggerReflectsPresence() {
        SagaMetadata withBoth = SagaMetadata.builder("S")
                .steps(List.of(SagaStepMetadata.simple("a", 0, "cmd")))
                .trigger(SagaMetadata.SagaTrigger.manual())
                .build();
        assertThat(withBoth.hasSteps()).isTrue();
        assertThat(withBoth.hasTrigger()).isTrue();
    }

    @Test
    void enumsAreComplete() {
        assertThat(SagaMetadata.CompensationStrategy.values())
                .containsExactly(
                        SagaMetadata.CompensationStrategy.ALL_OR_NOTHING,
                        SagaMetadata.CompensationStrategy.BEST_EFFORT,
                        SagaMetadata.CompensationStrategy.CUSTOM);
        assertThat(SagaMetadata.CompensationOrder.values())
                .containsExactly(
                        SagaMetadata.CompensationOrder.REVERSE,
                        SagaMetadata.CompensationOrder.FORWARD,
                        SagaMetadata.CompensationOrder.PARALLEL);
        assertThat(SagaMetadata.TriggerType.values())
                .containsExactly(
                        SagaMetadata.TriggerType.EVENT,
                        SagaMetadata.TriggerType.SCHEDULED,
                        SagaMetadata.TriggerType.MANUAL,
                        SagaMetadata.TriggerType.API);
    }

    @Test
    void triggerFactoriesPopulateExpectedFields() {
        SagaMetadata.SagaTrigger ev = SagaMetadata.SagaTrigger.onEvent("OrderCreated", "orders.created");
        assertThat(ev.type()).isEqualTo(SagaMetadata.TriggerType.EVENT);
        assertThat(ev.source()).isEqualTo("OrderCreated");
        assertThat(ev.topic()).isEqualTo("orders.created");

        SagaMetadata.SagaTrigger sched = SagaMetadata.SagaTrigger.scheduled("0 0 * * *");
        assertThat(sched.type()).isEqualTo(SagaMetadata.TriggerType.SCHEDULED);
        assertThat(sched.cronExpression()).isEqualTo("0 0 * * *");

        SagaMetadata.SagaTrigger man = SagaMetadata.SagaTrigger.manual();
        assertThat(man.type()).isEqualTo(SagaMetadata.TriggerType.MANUAL);
        assertThat(man.source()).isNull();
        assertThat(man.topic()).isNull();
        assertThat(man.cronExpression()).isNull();
    }

    @Test
    void monitoringEnabledFactory() {
        SagaMetadata.MonitoringConfig m = SagaMetadata.MonitoringConfig.enabled();
        assertThat(m.metricsEnabled()).isTrue();
        assertThat(m.tracingEnabled()).isTrue();
        assertThat(m.alertOnFailure()).isNull();
        assertThat(m.slaThreshold()).isNull();
    }

    @Test
    void builderPropagatesEverySetter() {
        SagaStepMetadata step = SagaStepMetadata.simple("validate", 0, "ValidateOrder");
        SagaMetadata.SagaTrigger trigger = SagaMetadata.SagaTrigger.manual();
        SagaMetadata.MonitoringConfig monitoring = SagaMetadata.MonitoringConfig.enabled();

        SagaMetadata s = SagaMetadata.builder("OrderSaga")
                .description("Order processing saga")
                .version(2)
                .steps(List.of(step))
                .compensationStrategy(SagaMetadata.CompensationStrategy.BEST_EFFORT)
                .compensationOrder(SagaMetadata.CompensationOrder.FORWARD)
                .timeout("PT15M")
                .compensationTimeout("PT5M")
                .maxRetries(5)
                .retryBackoff("PT2S")
                .trigger(trigger)
                .persistent(false)
                .stateClass("OrderSagaState")
                .permissions(List.of("ROLE_OPS"))
                .monitoring(monitoring)
                .transitions(List.of(SagaMetadata.SagaTransition.success("validate", null)))
                .build();

        assertThat(s.transitions()).hasSize(1);
        assertThat(s.hasTransitions()).isTrue();
        assertThat(s.description()).isEqualTo("Order processing saga");
        assertThat(s.version()).isEqualTo(2);
        assertThat(s.steps()).containsExactly(step);
        assertThat(s.compensationStrategy()).isEqualTo(SagaMetadata.CompensationStrategy.BEST_EFFORT);
        assertThat(s.compensationOrder()).isEqualTo(SagaMetadata.CompensationOrder.FORWARD);
        assertThat(s.timeout()).isEqualTo("PT15M");
        assertThat(s.compensationTimeout()).isEqualTo("PT5M");
        assertThat(s.maxRetries()).isEqualTo(5);
        assertThat(s.retryBackoff()).isEqualTo("PT2S");
        assertThat(s.trigger()).isEqualTo(trigger);
        assertThat(s.persistent()).isFalse();
        assertThat(s.stateClass()).isEqualTo("OrderSagaState");
        assertThat(s.permissions()).containsExactly("ROLE_OPS");
        assertThat(s.monitoring()).isEqualTo(monitoring);
    }

    // ----- SagaStepMetadata -----

    @Test
    void stepSimpleFactoryHasSafeDefaults() {
        SagaStepMetadata step = SagaStepMetadata.simple("validate", 0, "ValidateOrder");
        assertThat(step.name()).isEqualTo("validate");
        assertThat(step.order()).isZero();
        assertThat(step.command()).isEqualTo("ValidateOrder");
        assertThat(step.timeout()).isEqualTo("PT5M");
        assertThat(step.maxRetries()).isEqualTo(3);
        assertThat(step.retryBackoff()).isEqualTo("PT1S");
        assertThat(step.required()).isTrue();
        assertThat(step.parallel()).isFalse();
        assertThat(step.skipOnConditionFalse()).isFalse();
        assertThat(step.hasCompensation()).isFalse();
        assertThat(step.hasDependencies()).isFalse();
        assertThat(step.hasCondition()).isFalse();
    }

    @Test
    void stepPredicatesReflectPopulation() {
        SagaStepMetadata full = SagaStepMetadata.builder("compensate", 1)
                .compensation("UndoOrder")
                .dependsOn(List.of("validate"))
                .condition("amount > 100")
                .build();
        assertThat(full.hasCompensation()).isTrue();
        assertThat(full.hasDependencies()).isTrue();
        assertThat(full.hasCondition()).isTrue();

        SagaStepMetadata blank = SagaStepMetadata.builder("a", 0)
                .compensation("   ")
                .condition("   ")
                .build();
        assertThat(blank.hasCompensation()).isFalse();
        assertThat(blank.hasCondition()).isFalse();
    }

    @Test
    void stepBuilderPropagatesEverySetter() {
        SagaStepMetadata.InputMapping in = SagaStepMetadata.InputMapping.expression("$.input");
        SagaStepMetadata.OutputMapping out = SagaStepMetadata.OutputMapping.expression("$.output");

        SagaStepMetadata step = SagaStepMetadata.builder("charge", 2)
                .description("Charge customer")
                .service("payments")
                .command("ChargeCustomer")
                .compensation("RefundCustomer")
                .timeout("PT2M")
                .maxRetries(7)
                .retryBackoff("PT5S")
                .parallel(true)
                .required(false)
                .condition("amount > 0")
                .skipOnConditionFalse(true)
                .dependsOn(List.of("validate"))
                .producesEvents(List.of("CustomerCharged"))
                .inputMapping(in)
                .outputMapping(out)
                .errorHandler("ChargeErrorHandler")
                .kind(SagaStepMetadata.StepKind.INVOKE)
                .build();

        assertThat(step.kind()).isEqualTo(SagaStepMetadata.StepKind.INVOKE);
        assertThat(step.description()).isEqualTo("Charge customer");
        assertThat(step.service()).isEqualTo("payments");
        assertThat(step.command()).isEqualTo("ChargeCustomer");
        assertThat(step.compensation()).isEqualTo("RefundCustomer");
        assertThat(step.timeout()).isEqualTo("PT2M");
        assertThat(step.maxRetries()).isEqualTo(7);
        assertThat(step.retryBackoff()).isEqualTo("PT5S");
        assertThat(step.parallel()).isTrue();
        assertThat(step.required()).isFalse();
        assertThat(step.condition()).isEqualTo("amount > 0");
        assertThat(step.skipOnConditionFalse()).isTrue();
        assertThat(step.dependsOn()).containsExactly("validate");
        assertThat(step.producesEvents()).containsExactly("CustomerCharged");
        assertThat(step.inputMapping()).isEqualTo(in);
        assertThat(step.outputMapping()).isEqualTo(out);
        assertThat(step.errorHandler()).isEqualTo("ChargeErrorHandler");
    }

    @Test
    void inputMappingFactories() {
        SagaStepMetadata.InputMapping byExpr = SagaStepMetadata.InputMapping.expression("$.payload");
        assertThat(byExpr.expression()).isEqualTo("$.payload");
        assertThat(byExpr.fieldMappings()).isNull();

        SagaStepMetadata.FieldMapping m = SagaStepMetadata.FieldMapping.direct("src", "tgt");
        SagaStepMetadata.InputMapping byFields = SagaStepMetadata.InputMapping.fields(List.of(m));
        assertThat(byFields.expression()).isNull();
        assertThat(byFields.fieldMappings()).containsExactly(m);
    }

    @Test
    void outputMappingExpressionFactory() {
        SagaStepMetadata.OutputMapping om = SagaStepMetadata.OutputMapping.expression("$.result");
        assertThat(om.expression()).isEqualTo("$.result");
        assertThat(om.fieldMappings()).isNull();
    }

    @Test
    void fieldMappingDirectFactoryLeavesTransformNull() {
        SagaStepMetadata.FieldMapping fm = SagaStepMetadata.FieldMapping.direct("a", "b");
        assertThat(fm.source()).isEqualTo("a");
        assertThat(fm.target()).isEqualTo("b");
        assertThat(fm.transform()).isNull();
    }

    // ----- Step kind + typed transitions (0.7.0) -----

    @Test
    void stepKindAndTransitionOutcomeEnumsAreComplete() {
        assertThat(SagaStepMetadata.StepKind.values())
                .containsExactly(
                        SagaStepMetadata.StepKind.INVOKE,
                        SagaStepMetadata.StepKind.COMPENSATE,
                        SagaStepMetadata.StepKind.AWAIT_EVENT,
                        SagaStepMetadata.StepKind.AWAIT_TIMER);
        assertThat(SagaMetadata.TransitionOutcome.values())
                .containsExactly(
                        SagaMetadata.TransitionOutcome.SUCCESS,
                        SagaMetadata.TransitionOutcome.FAILURE,
                        SagaMetadata.TransitionOutcome.TIMEOUT,
                        SagaMetadata.TransitionOutcome.COMPENSATED);
    }

    @Test
    void effectiveKindReturnsExplicitKindWhenSet() {
        SagaStepMetadata step = SagaStepMetadata.builder("await", 0)
                .kind(SagaStepMetadata.StepKind.AWAIT_TIMER)
                .build();
        assertThat(step.kind()).isEqualTo(SagaStepMetadata.StepKind.AWAIT_TIMER);
        assertThat(step.effectiveKind()).isEqualTo(SagaStepMetadata.StepKind.AWAIT_TIMER);
    }

    @Test
    void effectiveKindInfersFromStructureWhenUnset() {
        // forward command → INVOKE
        assertThat(SagaStepMetadata.simple("charge", 0, "ChargeCard").effectiveKind())
                .isEqualTo(SagaStepMetadata.StepKind.INVOKE);
        // target service only → INVOKE
        assertThat(SagaStepMetadata.builder("call", 0).service("payments").build().effectiveKind())
                .isEqualTo(SagaStepMetadata.StepKind.INVOKE);
        // compensation only → COMPENSATE
        assertThat(SagaStepMetadata.builder("undo", 0).compensation("RefundCard").build().effectiveKind())
                .isEqualTo(SagaStepMetadata.StepKind.COMPENSATE);
        // a bare step (no command/service/compensation) cannot be inferred → null
        assertThat(SagaStepMetadata.builder("bare", 0).build().effectiveKind()).isNull();
        // a blank command is not a forward command
        assertThat(SagaStepMetadata.builder("blank", 0).command("   ").build().effectiveKind()).isNull();
    }

    @Test
    void transitionFactoriesSetOutcomeAndLeaveGuardNull() {
        assertThat(SagaMetadata.SagaTransition.success("a", "b").on())
                .isEqualTo(SagaMetadata.TransitionOutcome.SUCCESS);
        assertThat(SagaMetadata.SagaTransition.failure("a", "b").on())
                .isEqualTo(SagaMetadata.TransitionOutcome.FAILURE);
        assertThat(SagaMetadata.SagaTransition.timeout("a", "b").on())
                .isEqualTo(SagaMetadata.TransitionOutcome.TIMEOUT);
        SagaMetadata.SagaTransition t = SagaMetadata.SagaTransition.on("a", "b",
                SagaMetadata.TransitionOutcome.COMPENSATED);
        assertThat(t.on()).isEqualTo(SagaMetadata.TransitionOutcome.COMPENSATED);
        assertThat(t.guard()).isNull();
        assertThat(t.hasGuard()).isFalse();
        assertThat(t.isTerminal()).isFalse();
    }

    @Test
    void transitionCompactConstructorNormalizes() {
        // null outcome defaults to SUCCESS
        assertThat(new SagaMetadata.SagaTransition("a", "b", null, null).on())
                .isEqualTo(SagaMetadata.TransitionOutcome.SUCCESS);
        // blank guard normalizes to null
        assertThat(new SagaMetadata.SagaTransition("a", "b", SagaMetadata.TransitionOutcome.SUCCESS, "   ").guard())
                .isNull();
        // non-blank guard kept; hasGuard true
        SagaMetadata.SagaTransition guarded =
                new SagaMetadata.SagaTransition("a", "b", SagaMetadata.TransitionOutcome.FAILURE, "state.retryable");
        assertThat(guarded.guard()).isEqualTo("state.retryable");
        assertThat(guarded.hasGuard()).isTrue();
        // null / blank target is terminal
        assertThat(SagaMetadata.SagaTransition.on("a", null, SagaMetadata.TransitionOutcome.COMPENSATED).isTerminal())
                .isTrue();
        assertThat(SagaMetadata.SagaTransition.on("a", "   ", SagaMetadata.TransitionOutcome.SUCCESS).isTerminal())
                .isTrue();
        // from is required
        assertThatThrownBy(() -> new SagaMetadata.SagaTransition(null, "b",
                SagaMetadata.TransitionOutcome.SUCCESS, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("from");
    }

    @Test
    void sagaTransitionsNormalizeAndCopyDefensively() {
        // simple() and a null positional both yield an empty, present list
        assertThat(SagaMetadata.simple("S").transitions()).isEmpty();
        assertThat(SagaMetadata.simple("S").hasTransitions()).isFalse();
        SagaMetadata nullTransitions = new SagaMetadata("S", null, 1, List.of(),
                SagaMetadata.CompensationStrategy.ALL_OR_NOTHING, SagaMetadata.CompensationOrder.REVERSE,
                "PT30M", "PT10M", 3, "PT1S", null, true, null, List.of(), null, null);
        assertThat(nullTransitions.transitions()).isEmpty();

        // builder defensively copies — mutating the source list afterwards does not leak in
        List<SagaMetadata.SagaTransition> src = new ArrayList<>();
        src.add(SagaMetadata.SagaTransition.success("a", "b"));
        SagaMetadata s = SagaMetadata.builder("S").transitions(src).build();
        src.add(SagaMetadata.SagaTransition.failure("b", "a"));
        assertThat(s.transitions()).hasSize(1);
    }
}
