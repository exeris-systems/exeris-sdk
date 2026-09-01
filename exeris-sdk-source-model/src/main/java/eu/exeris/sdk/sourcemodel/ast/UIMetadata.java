package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Metadata for UI rendering including Grid Layout, component types, and groups.
 * Supports Atom v3 Enterprise UI features.
 *
 * @param icon the icon representing the entity in generated navigation
 * @param color the accent colour applied to the entity's generated surfaces
 * @param listView whether a list view is generated
 * @param detailView whether a detail view is generated
 * @param createForm whether a create form is generated
 * @param editForm whether an edit form is generated
 * @param searchable whether the generated list view offers a search box
 * @param filterable whether the generated list view offers filters
 * @param exportable whether the generated list view offers an export action
 * @param bulkActions whether the generated list view allows acting on a selection
 * @param columns the number of columns the generated form grid is laid out on
 * @param defaultLayout the layout the generated views start in
 * @param groups the field groupings the generated forms are organised into
 * @param fieldOverrides per-field presentation overrides, keyed by field name
 * @author Exeris SDK Team
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UIMetadata(
        String icon,
        String color,
        boolean listView,
        boolean detailView,
        boolean createForm,
        boolean editForm,
        boolean searchable,
        boolean filterable,
        boolean exportable,
        boolean bulkActions,
        int columns,
        String defaultLayout,
        List<UIGroupMetadata> groups,
        List<UIFieldMetadata> fieldOverrides
) {
    /**
     * The default {@code UIMetadata}.
     *
     * @return the {@code UIMetadata}
     */
    public static UIMetadata defaults() {
        return new UIMetadata(null, null, true, true, true, true, true, true, false, false, 12, "grid", List.of(), List.of());
    }

    /**
     * Starts a builder for a {@code Builder}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Metadata for UI field groups (card/section grouping).
     * @param name the group's identity, referenced by the fields that belong to it
     * @param label the heading a generated form shows for the group
     * @param description prose shown beneath the group heading
     * @param order the group's position among its siblings
     * @param collapsible whether a reader may collapse the group
     * @param collapsed whether the group starts collapsed
     * @param icon the icon shown beside the group heading
     * @param gridSpan how many grid columns the group occupies
     * @param fields the names of the fields placed in this group, in order
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UIGroupMetadata(
            String name,
            String label,
            String description,
            int order,
            boolean collapsible,
            boolean collapsed,
            String icon,
            int gridSpan,
            List<String> fields
    ) {
        /**
         * Creates a minimal {@code UIGroupMetadata}, with only the essentials set.
         *
         * @param name the {@code name} the result carries
         * @param label the {@code label} the result carries
         * @param fields the {@code fields} the result carries
         * @return the {@code UIGroupMetadata}
         */
        public static UIGroupMetadata simple(String name, String label, List<String> fields) {
            return new UIGroupMetadata(name, label, null, 1, false, false, null, 12, fields);
        }
    }

    /**
     * Per-field UI configuration overrides.
     * @param fieldName the field this override applies to
     * @param componentType the input component the field is rendered with
     * @param gridSpan how many grid columns the field occupies
     * @param displayOrder the field's position within its group
     * @param displayInList whether the field appears in the generated list view
     * @param displayInDetail whether the field appears in the generated detail view
     * @param editableInForm whether the field may be edited in generated forms
     * @param placeholder the placeholder text shown in an empty input
     * @param helpText prose shown beneath the input
     * @param format the display format applied to the value
     * @param width the input's width, as a CSS length
     * @param cssClass extra CSS classes applied to the input
     * @param mask the input mask applied while typing
     * @param autocomplete the autocomplete configuration, when the field is rendered as a lookup
     * @param select the option-list configuration, when the field is rendered as a select
     * @param customComponent the component to render instead of the built-in one
     * @param placeholderKey the message-bundle key for the placeholder; the literal is the fallback
     * @param helpTextKey the message-bundle key for the help text, on the same terms
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UIFieldMetadata(
            String fieldName,
            ComponentType componentType,
            int gridSpan,
            int displayOrder,
            boolean displayInList,
            boolean displayInDetail,
            boolean editableInForm,
            String placeholder,
            String helpText,
            String format,
            String width,
            String cssClass,
            String mask,
            AutocompleteConfig autocomplete,
            SelectConfig select,
            // Custom-component escape hatch: meaningful only when componentType
            // is CUSTOM; names the application-supplied control. Null otherwise.
            String customComponent,
            // i18n message keys (optional). Pair with placeholder / helpText:
            // when present, the key resolves against the app message bundle and
            // the literal placeholder / helpText is the fallback. Null when unset.
            String placeholderKey,
            String helpTextKey
    ) {
        // Normalize blank -> null for the additive escape-hatch / i18n key fields,
        // so their @UI "" defaults are dropped by @JsonInclude(NON_NULL) instead of
        // serializing as ""-valued fields. UIFieldMetadata has no builder, so the
        // record itself owns the guard (mirrors FieldMetadata.Builder). Existing
        // string fields keep their prior (un-normalized) behavior intentionally.
        /**
         * Compact constructor; applies this record's normalization rules.
         */
        public UIFieldMetadata {
            customComponent = (customComponent == null || customComponent.isBlank()) ? null : customComponent;
            placeholderKey = (placeholderKey == null || placeholderKey.isBlank()) ? null : placeholderKey;
            helpTextKey = (helpTextKey == null || helpTextKey.isBlank()) ? null : helpTextKey;
        }

        /**
         * Creates a minimal {@code UIFieldMetadata}, with only the essentials set.
         *
         * @param fieldName the {@code fieldName} the result carries
         * @param componentType the {@code componentType} the result carries
         * @return the {@code UIFieldMetadata}
         */
        public static UIFieldMetadata simple(String fieldName, ComponentType componentType) {
            return new UIFieldMetadata(fieldName, componentType, 6, 0, true, true, true, null, null, null, null, null, null, null, null, null, null, null);
        }

        /**
         * Creates a {@code UIFieldMetadata}.
         *
         * @param fieldName the {@code fieldName} the result carries
         * @param componentType the {@code componentType} the result carries
         * @return the {@code UIFieldMetadata}
         */
        public static UIFieldMetadata fullWidth(String fieldName, ComponentType componentType) {
            return new UIFieldMetadata(fieldName, componentType, 12, 0, true, true, true, null, null, null, null, null, null, null, null, null, null, null);
        }

        /**
         * Field rendered by an application-supplied custom component
         * ({@link ComponentType#CUSTOM}); {@code customComponent} names the control.
                  *
         * @param fieldName the {@code fieldName} the result carries
         * @param customComponent the {@code customComponent} the result carries
         * @return the {@code UIFieldMetadata}
        */
        public static UIFieldMetadata custom(String fieldName, String customComponent) {
            return new UIFieldMetadata(fieldName, ComponentType.CUSTOM, 6, 0, true, true, true, null, null, null, null, null, null, null, null, customComponent, null, null);
        }
    }

    /**
     * Component types for form fields.
     */
    public enum ComponentType {
        /** Let the generator choose from the field's type. */
        AUTO,
        /** A single-line text box. */
        TEXT_INPUT,
        /** A multi-line text box. */
        TEXT_AREA,
        /** A numeric input. */
        NUMBER_INPUT,
        /** A calendar date. */
        DATE_PICKER,
        /** A date and a time. */
        DATETIME_PICKER,
        /** A time of day. */
        TIME_PICKER,
        /** A single on/off box. */
        CHECKBOX,
        /** An on/off switch. */
        TOGGLE,
        /** One choice from a drop-down list. */
        SELECT,
        /** Several choices from a list. */
        MULTI_SELECT,
        /** One choice from a small visible set. */
        RADIO_GROUP,
        /** A type-ahead lookup against another entity. */
        AUTOCOMPLETE,
        /** A file chooser. */
        FILE_UPLOAD,
        /** An image chooser, with a preview. */
        IMAGE_UPLOAD,
        /** A formatted-text editor. */
        RICH_TEXT_EDITOR,
        /** A code editor with syntax highlighting. */
        CODE_EDITOR,
        /** A colour. */
        COLOR_PICKER,
        /** A slider over a bounded range. */
        SLIDER,
        /** A star-style rating control. */
        RATING,
        /** Several free-form values, each shown as a removable chip. */
        CHIPS,
        /** A masked text box. */
        PASSWORD,
        /** A text box validated as an email address. */
        EMAIL,
        /** A text box formatted as a telephone number. */
        PHONE,
        /** A text box validated as a URL. */
        URL,
        /** A numeric input formatted as money. */
        CURRENCY,
        /** Carried in the form but not shown. */
        HIDDEN,
        /**
         * Application-supplied custom component — the escape hatch out of this
         * closed enum. The concrete control is named by
         * {@link UIFieldMetadata#customComponent()}.
         */
        CUSTOM
    }

    /**
     * Configuration for autocomplete fields (relationships).
     * @param targetEntity the entity the lookup searches
     * @param displayField the target's field shown to the reader
     * @param valueField the target's field carried as the stored value
     * @param searchEndpoint the endpoint the lookup queries
     * @param minChars how many characters must be typed before a query is issued
     * @param maxResults the maximum number of suggestions returned
     * @param allowCreate whether the reader may create a new target from the lookup
     * @param createAction the action invoked when they do
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AutocompleteConfig(
            String targetEntity,
            String displayField,
            String valueField,
            String searchEndpoint,
            int minChars,
            int maxResults,
            boolean allowCreate,
            String createAction
    ) {
        /**
         * Creates a {@code AutocompleteConfig}.
         *
         * @param targetEntity the {@code targetEntity} the result carries
         * @param displayField the {@code displayField} the result carries
         * @return the {@code AutocompleteConfig}
         */
        public static AutocompleteConfig forEntity(String targetEntity, String displayField) {
            return new AutocompleteConfig(targetEntity, displayField, "id", null, 2, 20, false, null);
        }
    }

    /**
     * Configuration for select/dropdown fields.
     * @param optionsSource where the options come from — a static list, or an endpoint
     * @param optionsEndpoint the endpoint the options are fetched from, when they are not static
     * @param staticOptions the options carried inline, when they are
     * @param multiple whether more than one option may be chosen
     * @param clearable whether the selection may be cleared back to empty
     * @param searchable whether the option list offers a search box
     * @param groupBy the option attribute the list is grouped under
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SelectConfig(
            String optionsSource,
            String optionsEndpoint,
            List<SelectOption> staticOptions,
            boolean multiple,
            boolean clearable,
            boolean searchable,
            String groupBy
    ) {
        /**
         * Creates a {@code SelectConfig}.
         *
         * @param enumType the {@code enumType} the result carries
         * @return the {@code SelectConfig}
         */
        public static SelectConfig fromEnum(String enumType) {
            return new SelectConfig("enum:" + enumType, null, null, false, true, false, null);
        }

        /**
         * Creates a {@code SelectConfig}.
         *
         * @param endpoint the {@code endpoint} the result carries
         * @return the {@code SelectConfig}
         */
        public static SelectConfig fromEndpoint(String endpoint) {
            return new SelectConfig("api", endpoint, null, false, true, true, null);
        }
    }

    /**
     * Static option for select fields.
     * @param value the value stored when the option is chosen
     * @param label the text shown to the reader
     * @param icon the icon shown beside the label
     * @param group the heading this option is listed under
     * @param disabled whether the option is shown but cannot be chosen
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SelectOption(
            String value,
            String label,
            String icon,
            String group,
            boolean disabled
    ) {
        /**
         * Creates a minimal {@code SelectOption}, with only the essentials set.
         *
         * @param value the {@code value} the result carries
         * @param label the {@code label} the result carries
         * @return the {@code SelectOption}
         */
        public static SelectOption simple(String value, String label) {
            return new SelectOption(value, label, null, null, false);
        }
    }

    /**
     * A mutable builder for {@code SelectOption}.
     *
     * <p>Each setter sets the record component of the same name. Those components are
     * documented by the record's own {@code @param} tags and are deliberately not restated
     * here — a per-setter repetition of the component's meaning is filler, and filler is what
     * makes generated javadoc worth less than none.
     */
    public static final class Builder {
        /**
         * Creates an empty builder. Declared rather than left implicit so it carries a
         * comment; it stays {@code public} because the implicit constructor of a public
         * class is public, and narrowing it would be a binary break.
         */
        public Builder() {
        }

        private String icon;
        private String color;
        private boolean listView = true;
        private boolean detailView = true;
        private boolean createForm = true;
        private boolean editForm = true;
        private boolean searchable = true;
        private boolean filterable = true;
        private boolean exportable = false;
        private boolean bulkActions = false;
        private int columns = 12;
        private String defaultLayout = "grid";
        private List<UIGroupMetadata> groups = List.of();
        private List<UIFieldMetadata> fieldOverrides = List.of();

        public Builder icon(String v) { this.icon = v; return this; }
        public Builder color(String v) { this.color = v; return this; }
        public Builder listView(boolean v) { this.listView = v; return this; }
        public Builder detailView(boolean v) { this.detailView = v; return this; }
        public Builder createForm(boolean v) { this.createForm = v; return this; }
        public Builder editForm(boolean v) { this.editForm = v; return this; }
        public Builder searchable(boolean v) { this.searchable = v; return this; }
        public Builder filterable(boolean v) { this.filterable = v; return this; }
        public Builder exportable(boolean v) { this.exportable = v; return this; }
        public Builder bulkActions(boolean v) { this.bulkActions = v; return this; }
        public Builder columns(int v) { this.columns = v; return this; }
        public Builder defaultLayout(String v) { this.defaultLayout = v; return this; }
        public Builder groups(List<UIGroupMetadata> v) { this.groups = v; return this; }
        public Builder fieldOverrides(List<UIFieldMetadata> v) { this.fieldOverrides = v; return this; }

        /**
         * Builds the {@code UIMetadata} from this builder's current state.
         *
         * @return the built {@code UIMetadata}
         */
        public UIMetadata build() {
            return new UIMetadata(icon, color, listView, detailView, createForm, editForm,
                    searchable, filterable, exportable, bulkActions, columns, defaultLayout, groups, fieldOverrides);
        }
    }
}

