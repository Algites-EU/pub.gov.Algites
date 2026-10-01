package eu.algites.pltf.modustro.builder.model.inheritance;

import java.util.Arrays;

/**
 * Controls membership of inherited keyed item collections.
 */
public enum AInItemsInheritancePolicy {
    /**
     * Keep inherited-only items while merging matching local items.
     */
    MERGE_MISSING_ITEMS("merge_missing_items"),
    /**
     * Remove inherited-only items and retain only locally selected membership.
     */
    REMOVE_MISSING_ITEMS("remove_missing_items");

    private final String wireValue;

    AInItemsInheritancePolicy(String aWireValue) {
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
    public static AInItemsInheritancePolicy fromWireValue(String aWireValue) {
        return Arrays.stream(values())
            .filter(locValue -> locValue.wireValue.equals(aWireValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported ItemsInheritancePolicy '" + aWireValue + "'."));
    }
}
