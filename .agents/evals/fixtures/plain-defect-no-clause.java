// Proposed change to exeris-sdk-source-model: eu.exeris.sdk.sourcemodel.ast.AstLists
// Only the changed method is shown.

static <T> List<T> copyOfNoNulls(List<T> values, String component) {
    if (values == null) {
        return List.of();
    }
    for (int i = 1; i < values.size(); i++) {          // starts at 1
        if (values.get(i) == null) {
            throw new NullPointerException(
                    component + "[" + i + "] is null; " + component + " must not contain null elements");
        }
    }
    return List.copyOf(values);
}
