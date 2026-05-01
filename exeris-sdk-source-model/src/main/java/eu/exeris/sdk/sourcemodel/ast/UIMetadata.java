package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Metadata for UI rendering including Grid Layout, component types, and groups.
 * Supports Atom v3 Enterprise UI features.
 *
 * @author Exeris Team
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
    public static UIMetadata defaults() {
        return new UIMetadata(null, null, true, true, true, true, true, true, false, false, 12, "grid", List.of(), List.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Metadata for UI field groups (card/section grouping).
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
        public static UIGroupMetadata simple(String name, String label, List<String> fields) {
            return new UIGroupMetadata(name, label, null, 1, false, false, null, 12, fields);
        }
    }

    /**
     * Per-field UI configuration overrides.
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
            SelectConfig select
    ) {
        public static UIFieldMetadata simple(String fieldName, ComponentType componentType) {
            return new UIFieldMetadata(fieldName, componentType, 6, 0, true, true, true, null, null, null, null, null, null, null, null);
        }

        public static UIFieldMetadata fullWidth(String fieldName, ComponentType componentType) {
            return new UIFieldMetadata(fieldName, componentType, 12, 0, true, true, true, null, null, null, null, null, null, null, null);
        }
    }

    /**
     * Component types for form fields.
     */
    public enum ComponentType {
        AUTO,
        TEXT_INPUT,
        TEXT_AREA,
        NUMBER_INPUT,
        DATE_PICKER,
        DATETIME_PICKER,
        TIME_PICKER,
        CHECKBOX,
        TOGGLE,
        SELECT,
        MULTI_SELECT,
        RADIO_GROUP,
        AUTOCOMPLETE,
        FILE_UPLOAD,
        IMAGE_UPLOAD,
        RICH_TEXT_EDITOR,
        CODE_EDITOR,
        COLOR_PICKER,
        SLIDER,
        RATING,
        CHIPS,
        PASSWORD,
        EMAIL,
        PHONE,
        URL,
        CURRENCY,
        HIDDEN
    }

    /**
     * Configuration for autocomplete fields (relationships).
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
        public static AutocompleteConfig forEntity(String targetEntity, String displayField) {
            return new AutocompleteConfig(targetEntity, displayField, "id", null, 2, 20, false, null);
        }
    }

    /**
     * Configuration for select/dropdown fields.
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
        public static SelectConfig fromEnum(String enumType) {
            return new SelectConfig("enum:" + enumType, null, null, false, true, false, null);
        }

        public static SelectConfig fromEndpoint(String endpoint) {
            return new SelectConfig("api", endpoint, null, false, true, true, null);
        }
    }

    /**
     * Static option for select fields.
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
        public static SelectOption simple(String value, String label) {
            return new SelectOption(value, label, null, null, false);
        }
    }

    public static final class Builder {
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

        public UIMetadata build() {
            return new UIMetadata(icon, color, listView, detailView, createForm, editForm,
                    searchable, filterable, exportable, bulkActions, columns, defaultLayout, groups, fieldOverrides);
        }
    }
}

