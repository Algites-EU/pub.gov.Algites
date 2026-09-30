package eu.algites.pltf.modustro.builder.model.dependency;

import java.util.Arrays;

/**
 * Portable dependency usages understood by Modustro Builder technology handlers.
 */
public enum AInDependencyUsage {
    PRODUCT_API("product_api"),
    PRODUCT_IMPLEMENTATION("product_implementation"),
    PRODUCT_COMPILE_ONLY("product_compile_only"),
    PRODUCT_COMPILE_ONLY_API("product_compile_only_api"),
    PRODUCT_RUNTIME_ONLY("product_runtime_only"),
    PRODUCT_ANNOTATION_PROCESSOR("product_annotation_processor"),
    DEVELOP_IMPLEMENTATION("develop_implementation"),
    DEVELOP_COMPILE_ONLY("develop_compile_only"),
    DEVELOP_RUNTIME_ONLY("develop_runtime_only"),
    DEVELOP_ANNOTATION_PROCESSOR("develop_annotation_processor");

    private final String wireValue;

    AInDependencyUsage(String aWireValue) {
        wireValue = aWireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static AInDependencyUsage fromWireValue(String aWireValue) {
        return Arrays.stream(values())
            .filter(locValue -> locValue.wireValue.equals(aWireValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported dependency usage '" + aWireValue + "'."));
    }
}
