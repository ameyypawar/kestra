package io.kestra.core.models.property;

import io.micronaut.context.annotation.Context;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;

/**
 * Jakarta Bean Validation value extractor for a Property.<br>
 *
 * This is used by the @{@link io.kestra.core.validations.factory.CustomValidatorFactoryProvider}.
 */
@Context
public class PropertyValueExtractor implements ValueExtractor<Property<@ExtractedValue ?>> {

    @Override
    public void extractValues(Property<?> originalValue, ValueReceiver receiver) {
        Object value = originalValue.getValue();

        // A property parsed from a flow carries only its expression until it is rendered, so there
        // is nothing to validate at save time. Reporting the absent value as null only skipped the
        // constraints that accept null; the ones that reject it (@NotBlank, @NotNull, @NotEmpty)
        // failed instead, on a value the user had written literally. Reporting no element at all
        // disables validation here for every constraint, and leaves it intact once the value is
        // populated at runtime.
        if (value != null) {
            receiver.value(null, value);
        }
    }
}
