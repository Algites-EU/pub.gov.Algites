package eu.algites.pltf.modustro.builder.model.inheritance;

import java.util.Arrays;

/**
 * Controls membership of inherited keyed item collections.
 */
public enum AInItemsInheritancePolicy {
    MERGE_MISSING_ITEMS("mergeMissingItems"),
    REMOVE_MISSING_ITEMS("removeMissingItems");

    private final String wireValue;

    AInItemsInheritancePolicy(String aWireValue) {
        wireValue = aWireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static AInItemsInheritancePolicy fromWireValue(String aWireValue) {
        return Arrays.stream(values())
            .filter(locValue -> locValue.wireValue.equals(aWireValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported ItemsInheritancePolicy '" + aWireValue + "'."));
    }
}
