package eu.algites.pltf.modustro.builder.model.dependency;

import java.util.Arrays;

/**
 * Portable dependency usages understood by Modustro Builder technology handlers.
 */
public enum AInDependencyUsage {
    /**
     * Product API dependency.
     */
    PRODUCT_API("product_api"),
    /**
     * Product implementation dependency.
     */
    PRODUCT_IMPLEMENTATION("product_implementation"),
    /**
     * Product compile-only dependency.
     */
    PRODUCT_COMPILE_ONLY("product_compile_only"),
    /**
     * Product API compile-only dependency.
     */
    PRODUCT_COMPILE_ONLY_API("product_compile_only_api"),
    /**
     * Product runtime-only dependency.
     */
    PRODUCT_RUNTIME_ONLY("product_runtime_only"),
    /**
     * Product annotation-processor dependency.
     */
    PRODUCT_ANNOTATION_PROCESSOR("product_annotation_processor"),
    /**
     * Development implementation dependency.
     */
    DEVELOP_IMPLEMENTATION("develop_implementation"),
    /**
     * Development compile-only dependency.
     */
    DEVELOP_COMPILE_ONLY("develop_compile_only"),
    /**
     * Development runtime-only dependency.
     */
    DEVELOP_RUNTIME_ONLY("develop_runtime_only"),
    /**
     * Development annotation-processor dependency.
     */
    DEVELOP_ANNOTATION_PROCESSOR("develop_annotation_processor");

    private final String wireValue;

    AInDependencyUsage(String aWireValue) {
        wireValue = aWireValue;
    }

    /**
     * Returns the serialized wire value.
     * @return serialized wire value
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Resolves the enum constant represented by a serialized wire value.
     *
     * @param aWireValue serialized wire value
     * @return matching enum constant
     * @throws IllegalArgumentException if the wire value is unsupported
     */
    public static AInDependencyUsage fromWireValue(String aWireValue) {
        return Arrays.stream(values())
            .filter(locValue -> locValue.wireValue.equals(aWireValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported dependency usage '" + aWireValue + "'."));
    }
}
