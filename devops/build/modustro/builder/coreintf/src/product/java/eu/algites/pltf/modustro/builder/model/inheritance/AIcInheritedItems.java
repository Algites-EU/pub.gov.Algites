package eu.algites.pltf.modustro.builder.model.inheritance;

import java.util.List;
import java.util.Objects;

/**
 * A keyed collection contribution at one hierarchy level.
 *
 * @param <T> item type
 */
public final class AIcInheritedItems<T> {

    private final boolean defined;
    private final AInItemsInheritancePolicy policy;
    private final List<T> items;

    private AIcInheritedItems(boolean aDefined, AInItemsInheritancePolicy aPolicy, List<T> aItems) {
        defined = aDefined;
        policy = Objects.requireNonNull(aPolicy, "policy");
        items = List.copyOf(aItems);
    }

    /**
     * Creates an absent inheritance contribution.
 * @param <T> inherited value/item type
     * @return absent inheritance contribution
 */
    public static <T> AIcInheritedItems<T> absent() {
        return new AIcInheritedItems<>(false, AInItemsInheritancePolicy.MERGE_MISSING_ITEMS, List.of());
    }

    /**
     * Creates an explicit inheritance contribution.
     *
 * @param <T> inherited value/item type
     * @param aItems items
     * @return inheritance contribution
 */
    public static <T> AIcInheritedItems<T> of(List<T> aItems) {
        return of(AInItemsInheritancePolicy.MERGE_MISSING_ITEMS, aItems);
    }

    /**
     * Creates an explicit inheritance contribution.
     *
 * @param <T> inherited value/item type
     * @param aPolicy items inheritance policy
     * @param aItems items
     * @return inheritance contribution
 */
    public static <T> AIcInheritedItems<T> of(AInItemsInheritancePolicy aPolicy, List<T> aItems) {
        return new AIcInheritedItems<>(true, aPolicy, Objects.requireNonNull(aItems, "items"));
    }

    /**
     * Returns whether the collection was explicitly defined at this hierarchy level.
     * @return whether the collection is defined
     */
    public boolean defined() {
        return defined;
    }

    /**
     * Returns the items inheritance policy.
     * @return items inheritance policy
     */
    public AInItemsInheritancePolicy policy() {
        return policy;
    }

    /**
     * Returns the immutable item list.
     * @return immutable items
     */
    public List<T> items() {
        return items;
    }
}
