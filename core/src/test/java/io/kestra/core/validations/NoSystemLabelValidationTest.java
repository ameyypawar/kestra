package io.kestra.core.validations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.types.MaxDurationSLA;
import io.kestra.core.models.validations.ModelValidator;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class NoSystemLabelValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldReportAViolation() {
        var sla = MaxDurationSLA.builder()
            .duration(Duration.ofSeconds(1))
            .id("id")
            .behavior(SLA.Behavior.CANCEL)
            .type(SLA.Type.MAX_DURATION)
            .labels(List.of(new Label("system.sla", "violated")))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);

        assertThat(valid.isPresent()).isTrue();
        assertThat(valid.get().getMessage()).isEqualTo("labels[0].<list element>: System labels can only be set by Kestra itself, offending label: system.sla=violated.\n");
    }

    @Test
    void shouldReportAViolationForTheBareSystemLabel() {
        // `system` is reserved on its own, not only as a prefix: labels reach expressions through
        // Label.toNestedMap, which nests on '.', so a user label named `system` collides with the
        // system.* labels every execution already carries (#8089)
        var sla = MaxDurationSLA.builder()
            .duration(Duration.ofSeconds(1))
            .id("id")
            .behavior(SLA.Behavior.CANCEL)
            .type(SLA.Type.MAX_DURATION)
            .labels(List.of(new Label("system", "mine")))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);

        assertThat(valid.isPresent()).isTrue();
        assertThat(valid.get().getMessage()).isEqualTo("labels[0].<list element>: System labels can only be set by Kestra itself, offending label: system=mine.\n");
    }

    @Test
    void shouldAcceptAKeyThatMerelyStartsWithSystem() {
        // only `system` and `system.*` are reserved - `systemic` is an ordinary label
        var sla = MaxDurationSLA.builder()
            .duration(Duration.ofSeconds(1))
            .id("id")
            .behavior(SLA.Behavior.CANCEL)
            .type(SLA.Type.MAX_DURATION)
            .labels(List.of(new Label("systemic", "fine")))
            .build();

        assertThat(modelValidator.isValid(sla).isEmpty()).isTrue();
    }

    @Test
    void shouldSuccess() {
        var sla = MaxDurationSLA.builder()
            .duration(Duration.ofSeconds(1))
            .id("id")
            .behavior(SLA.Behavior.CANCEL)
            .type(SLA.Type.MAX_DURATION)
            .labels(List.of(new Label("sla", "violated")))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);

        assertThat(valid.isEmpty()).isTrue();
    }
}
