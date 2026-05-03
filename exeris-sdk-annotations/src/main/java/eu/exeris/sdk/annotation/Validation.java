package eu.exeris.sdk.annotation;

import java.lang.annotation.*;

/**
 * Defines validation rules for Exeris domain entity fields.
 * <p>Validation rules are enforced in both backend (Spring Validation) and
 * frontend (Angular Forms), ensuring consistent data integrity across the stack.
 *
 * <h2>Basic Usage:</h2>
 * <pre>{@code
 * @Field(
 *     label = "Email Address",
 *     validation = @Validation(
 *         required = true,
 *         email = true,
 *         message = "Please enter a valid email address"
 *     )
 * )
 * private String email;
 * }</pre>
 *
 * <h2>String Length Validation:</h2>
 * <pre>{@code
 * @Field(
 *     label = "Username",
 *     validation = @Validation(
 *         required = true,
 *         minLength = 3,
 *         maxLength = 20,
 *         pattern = "^[a-zA-Z0-9_]+$",
 *         message = "Username must be 3-20 characters, alphanumeric and underscore only"
 *     )
 * )
 * private String username;
 * }</pre>
 *
 * <h2>Numeric Range Validation:</h2>
 * <pre>{@code
 * @Field(
 *     label = "Age",
 *     validation = @Validation(
 *         required = true,
 *         min = 18,
 *         max = 120,
 *         message = "Age must be between 18 and 120"
 *     )
 * )
 * private Integer age;
 * }</pre>
 *
 * <h2>Decimal Precision Validation:</h2>
 * <pre>{@code
 * @Field(
 *     label = "Price",
 *     validation = @Validation(
 *         required = true,
 *         min = 0,
 *         decimalMin = "0.01",
 *         decimalMax = "999999.99",
 *         message = "Price must be between 0.01 and 999,999.99"
 *     )
 * )
 * private BigDecimal price;
 * }</pre>
 *
 * <h2>Pattern (Regex) Validation:</h2>
 * <pre>{@code
 * @Field(
 *     label = "Phone Number",
 *     validation = @Validation(
 *         pattern = "^\\+?[1-9]\\d{1,14}$",
 *         message = "Please enter a valid international phone number"
 *     )
 * )
 * private String phoneNumber;
 * }</pre>
 *
 * <h2>URL and Email Validation:</h2>
 * <pre>{@code
 * @Field(
 *     label = "Website",
 *     validation = @Validation(
 *         url = true,
 *         message = "Please enter a valid URL"
 *     )
 * )
 * private String website;
 *
 * @Field(
 *     label = "Email",
 *     validation = @Validation(
 *         email = true,
 *         message = "Invalid email format"
 *     )
 * )
 * private String email;
 * }</pre>
 *
 * <h2>Custom Validation:</h2>
 * <pre>{@code
 * @Field(
 *     label = "Coupon Code",
 *     validation = @Validation(
 *         customValidator = "couponCodeValidator",
 *         message = "Invalid or expired coupon code"
 *     )
 * )
 * private String couponCode;
 * }</pre>
 *
 * <h2>Multiple Validations:</h2>
 * <pre>{@code
 * @Field(
 *     label = "Password",
 *     validation = @Validation(
 *         required = true,
 *         minLength = 8,
 *         maxLength = 128,
 *         pattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
 *         message = "Password must be 8+ characters with uppercase, lowercase, number, and special character"
 *     )
 * )
 * private String password;
 * }</pre>
 *
 * <h2>Generated Validation Code:</h2>
 * <p>The SDK generates validation code for both backend and frontend:
 *
 * <p><strong>Backend (Spring Validation):</strong>
 * <pre>{@code
 * @NotNull(message = "Email is required")
 * @Email(message = "Please enter a valid email address")
 * private String email;
 * }</pre>
 *
 * <p><strong>Frontend (Angular Validators):</strong>
 * <pre>{@code
 * email: ['', [Validators.required, Validators.email]],
 * }</pre>
 *
 * @author Exeris SDK Team
 * @version 1.0.0
 * @since 1.0.0
 * @see Field
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Validation {

    /**
     * Whether the field is required (cannot be null or empty).
     *
     * @deprecated Use {@link Field#required()} instead. {@code @Field.required}
     *     is the canonical location for this concept — required-ness is a
     *     field-shape property, not a validation rule. The processor reads
     *     {@code @Field.required} when populating {@code FieldMetadata.required}
     *     and ignores this alias. Will be removed in 1.0.0.
     * @return true if field is required
     */
    @Deprecated(since = "0.2.0", forRemoval = true)
    boolean required() default false;

    /**
     * Minimum numeric value (inclusive).
     * <p>Applies to: Integer, Long, Float, Double, BigDecimal
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Validation(min = 0, message = "Value must be positive")
     * }</pre>
     *
     * @return minimum value
     */
    long min() default Long.MIN_VALUE;

    /**
     * Maximum numeric value (inclusive).
     * <p>Applies to: Integer, Long, Float, Double, BigDecimal
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Validation(max = 100, message = "Value cannot exceed 100")
     * }</pre>
     *
     * @return maximum value
     */
    long max() default Long.MAX_VALUE;

    /**
     * Minimum decimal value as string (for precise decimal validation).
     * <p>Use this for {@code BigDecimal} fields where precision matters.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Validation(decimalMin = "0.01", message = "Minimum value is 0.01")
     * }</pre>
     *
     * @return minimum decimal value
     */
    String decimalMin() default "";

    /**
     * Maximum decimal value as string (for precise decimal validation).
     * <p>Use this for {@code BigDecimal} fields where precision matters.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Validation(decimalMax = "999999.99", message = "Maximum value is 999,999.99")
     * }</pre>
     *
     * @return maximum decimal value
     */
    String decimalMax() default "";

    /**
     * Minimum string length.
     * <p>Applies to: String, CharSequence
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Validation(minLength = 3, message = "Minimum 3 characters")
     * }</pre>
     *
     * @return minimum length
     */
    int minLength() default 0;

    /**
     * Maximum string length.
     * <p>Applies to: String, CharSequence
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Validation(maxLength = 255, message = "Maximum 255 characters")
     * }</pre>
     *
     * @return maximum length
     */
    int maxLength() default Integer.MAX_VALUE;

    /**
     * Regular expression pattern for string validation.
     * <p>Applied as Java regex (backend) and JavaScript regex (frontend).
     *
     * <p><strong>Common Patterns:</strong>
     * <ul>
     *   <li>Alphanumeric: {@code "^[a-zA-Z0-9]+$"}</li>
     *   <li>Phone (US): {@code "^\\d{3}-\\d{3}-\\d{4}$"}</li>
     *   <li>Postal Code (US): {@code "^\\d{5}(-\\d{4})?$"}</li>
     *   <li>Hex Color: {@code "^#[0-9A-Fa-f]{6}$"}</li>
     * </ul>
     *
     * @return regex pattern
     */
    String pattern() default "";

    /**
     * Whether to validate as email address.
     * <p>When {@code true}:
     * <ul>
     *   <li>Backend: Generates {@code @Email} annotation</li>
     *   <li>Frontend: Adds {@code Validators.email}</li>
     *   <li>Validates standard email format (RFC 5322)</li>
     * </ul>
     *
     * @return true for email validation
     */
    boolean email() default false;

    /**
     * Whether to validate as URL.
     * <p>When {@code true}:
     * <ul>
     *   <li>Backend: Generates {@code @URL} annotation</li>
     *   <li>Frontend: Adds URL pattern validator</li>
     *   <li>Accepts http://, https://, ftp:// protocols</li>
     * </ul>
     *
     * @return true for URL validation
     */
    boolean url() default false;

    /**
     * Whether to validate as UUID.
     * <p>When {@code true}, validates UUID format (8-4-4-4-12 hex digits).
     *
     * <p><strong>Example:</strong>
     * {@code "550e8400-e29b-41d4-a716-446655440000"}
     *
     * @return true for UUID validation
     */
    boolean uuid() default false;

    /**
     * Whether the value must be positive (greater than 0).
     * <p>Applies to: numeric types
     *
     * <p>Equivalent to {@code @Validation(min = 1)}
     *
     * @return true if value must be positive
     */
    boolean positive() default false;

    /**
     * Whether the value must be positive or zero (greater than or equal to 0).
     * <p>Applies to: numeric types
     *
     * <p>Equivalent to {@code @Validation(min = 0)}
     *
     * @return true if value must be positive or zero
     */
    boolean positiveOrZero() default false;

    /**
     * Whether the value must be negative (less than 0).
     * <p>Applies to: numeric types
     *
     * @return true if value must be negative
     */
    boolean negative() default false;

    /**
     * Whether the value must be negative or zero (less than or equal to 0).
     * <p>Applies to: numeric types
     *
     * @return true if value must be negative or zero
     */
    boolean negativeOrZero() default false;

    /**
     * Whether to validate as past date (before current date/time).
     * <p>Applies to: Date, LocalDate, LocalDateTime, Instant
     *
     * <p>Useful for birth dates, creation dates, etc.
     *
     * @return true if date must be in the past
     */
    boolean past() default false;

    /**
     * Whether to validate as past or present date.
     * <p>Applies to: Date, LocalDate, LocalDateTime, Instant
     *
     * @return true if date must be in the past or present
     */
    boolean pastOrPresent() default false;

    /**
     * Whether to validate as future date (after current date/time).
     * <p>Applies to: Date, LocalDate, LocalDateTime, Instant
     *
     * <p>Useful for expiration dates, scheduled dates, etc.
     *
     * @return true if date must be in the future
     */
    boolean future() default false;

    /**
     * Whether to validate as future or present date.
     * <p>Applies to: Date, LocalDate, LocalDateTime, Instant
     *
     * @return true if date must be in the future or present
     */
    boolean futureOrPresent() default false;

    /**
     * Custom validator class name or Spring bean name.
     * <p>Used for complex validation logic that cannot be expressed
     * with standard validators.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Validation(
     *     customValidator = "uniqueEmailValidator",
     *     message = "Email already exists"
     * )
     * private String email;
     * }</pre>
     *
     * <p>The validator class must implement {@code ConstraintValidator}:
     * <pre>{@code
     * @Component("uniqueEmailValidator")
     * public class UniqueEmailValidator implements ConstraintValidator<Validation, String> {
     *     @Override
     *     public boolean isValid(String value, ConstraintValidatorContext context) {
     *         // Custom validation logic
     *         return !emailRepository.existsByEmail(value);
     *     }
     * }
     * }</pre>
     *
     * @return custom validator name
     */
    String customValidator() default "";

    /**
     * Error message displayed when validation fails.
     * <p>This message is shown in:
     * <ul>
     *   <li>Backend validation errors (API responses)</li>
     *   <li>Frontend form validation errors</li>
     *   <li>Toast notifications</li>
     * </ul>
     *
     * <p><strong>Message Placeholders:</strong>
     * <ul>
     *   <li>{@code {field}} - Field label</li>
     *   <li>{@code {min}} - Minimum value</li>
     *   <li>{@code {max}} - Maximum value</li>
     *   <li>{@code {minLength}} - Minimum length</li>
     *   <li>{@code {maxLength}} - Maximum length</li>
     * </ul>
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * message = "{field} must be between {min} and {max}"
     * }</pre>
     *
     * @return validation error message
     */
    String message() default "";

    /**
     * Whether to validate only on specific operations.
     *
     * @deprecated Use {@link Field#inCreate()} / {@link Field#inUpdate()}
     *     instead. The form-lifecycle scope of a field belongs on
     *     {@code @Field}, not on {@code @Validation}: a field that isn't
     *     present on the create form ({@code inCreate=false}) shouldn't have
     *     create-scoped validation rules to begin with. Will be removed in
     *     1.0.0. The processor will not honour this attribute going forward;
     *     drive operation-specific behaviour from {@code @Field.inCreate} /
     *     {@code @Field.inUpdate}.
     * @return operation type for validation
     */
    @Deprecated(since = "0.2.0", forRemoval = true)
    String validateOn() default "";

    /**
     * Validation groups for conditional validation.
     * <p>Used with JSR-303 validation groups to apply different
     * validation rules in different contexts.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Validation(
     *     required = true,
     *     groups = {"BasicInfo", "FullProfile"}
     * )
     * private String name;
     *
     * @Validation(
     *     required = true,
     *     groups = {"FullProfile"}
     * )
     * private String bio;
     * }</pre>
     *
     * @return validation group names
     */
    String[] groups() default {};

    /**
     * Async validator class name or Spring bean name.
     * <p>Used for validation that requires asynchronous operations
     * (e.g., checking uniqueness in database, calling external APIs).
     *
     * <p>In frontend (Angular), generates async validator that returns Observable.
     * In backend, validation is performed synchronously but may involve
     * async operations internally.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * @Field(
     *     label = "Email",
     *     validation = @Validation(
     *         email = true,
     *         asyncValidator = "uniqueEmailValidator",
     *         message = "Email already exists"
     *     )
     * )
     * private String email;
     * }</pre>
     *
     * <p>The async validator must implement appropriate interface:
     * <pre>{@code
     * // Frontend (Angular)
     * @Injectable()
     * export class UniqueEmailValidator implements AsyncValidator {
     *   validate(control: AbstractControl): Observable<ValidationErrors | null> {
     *     return this.http.get(`/api/validate/email/${control.value}`).pipe(
     *       map(exists => exists ? { emailTaken: true } : null)
     *     );
     *   }
     * }
     *
     * // Backend (Spring)
     * @Component("uniqueEmailValidator")
     * public class UniqueEmailValidator implements ConstraintValidator<Validation, String> {
     *     @Async
     *     public boolean isValid(String value, ConstraintValidatorContext context) {
     *         return !emailRepository.existsByEmail(value);
     *     }
     * }
     * }</pre>
     *
     * @return async validator name
     */
    String asyncValidator() default "";
}




