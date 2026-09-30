package eu.algites.pltf.modustro.builder.model.inheritance;

import java.util.Arrays;

/**
 * Controls membership of inherited keyed item collections.
 */
public enum AInItemsInheritancePolicy {
    MERGE_MISSING_ITEMS("merge_missing_items"),
    REMOVE_MISSING_ITEMS("remove_missing_items");

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
