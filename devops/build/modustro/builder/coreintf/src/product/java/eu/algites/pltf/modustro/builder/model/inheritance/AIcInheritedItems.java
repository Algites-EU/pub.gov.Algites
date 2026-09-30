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

    public static <T> AIcInheritedItems<T> absent() {
        return new AIcInheritedItems<>(false, AInItemsInheritancePolicy.MERGE_MISSING_ITEMS, List.of());
    }

    public static <T> AIcInheritedItems<T> of(List<T> aItems) {
        return of(AInItemsInheritancePolicy.MERGE_MISSING_ITEMS, aItems);
    }

    public static <T> AIcInheritedItems<T> of(AInItemsInheritancePolicy aPolicy, List<T> aItems) {
        return new AIcInheritedItems<>(true, aPolicy, Objects.requireNonNull(aItems, "items"));
    }

    public boolean defined() {
        return defined;
    }

    public AInItemsInheritancePolicy policy() {
        return policy;
    }

    public List<T> items() {
        return items;
    }
}
