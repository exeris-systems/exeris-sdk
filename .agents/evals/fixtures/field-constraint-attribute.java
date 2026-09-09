// Proposed change to exeris-sdk-annotations: eu.exeris.sdk.annotation.Field
// Only the added element is shown.
public @interface Field {

    // ... existing elements: required(), inCreate(), inUpdate(), displayName(), dataType() ...

    /** Maximum length accepted for this field. Convenient here so authors do not need @Validation. */
    int maxLength() default -1;
}
