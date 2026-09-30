package eu.algites.pltf.modustro.builder.inheritance;

import eu.algites.pltf.modustro.builder.model.inheritance.AIcInheritedItems;
import eu.algites.pltf.modustro.builder.model.inheritance.AIcInheritedValue;
import eu.algites.pltf.modustro.builder.model.inheritance.AIiInheritanceResolver;
import eu.algites.pltf.modustro.builder.model.inheritance.AIiItemIdentity;
import eu.algites.pltf.modustro.builder.model.inheritance.AIiItemMerger;
import eu.algites.pltf.modustro.builder.model.inheritance.AInInheritedValueState;
import eu.algites.pltf.modustro.builder.model.inheritance.AInItemsInheritancePolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default Gradle-independent implementation of Modustro hierarchical inheritance.
 */
public final class AIcDefaultInheritanceResolver implements AIiInheritanceResolver {

    @Override
    public <T> AIcInheritedValue<T> resolveScalar(AIcInheritedValue<T> aInheritedValue, AIcInheritedValue<T> aLocalValue) {
        Objects.requireNonNull(aInheritedValue, "inheritedValue");
        Objects.requireNonNull(aLocalValue, "localValue");
        if (aLocalValue.state() == AInInheritedValueState.ABSENT) {
            return aInheritedValue;
        }
        return aLocalValue;
    }

    @Override
    public <T, K> List<T> resolveItems(
        List<T> aInheritedItems,
        AIcInheritedItems<T> aLocalItems,
        AIiItemIdentity<T, K> aIdentity,
        AIiItemMerger<T> aMerger
    ) {
        Objects.requireNonNull(aInheritedItems, "inheritedItems");
        Objects.requireNonNull(aLocalItems, "localItems");
        Objects.requireNonNull(aIdentity, "identity");
        Objects.requireNonNull(aMerger, "merger");
        if (!aLocalItems.defined()) {
            return List.copyOf(aInheritedItems);
        }

        Map<K, T> locInheritedById = indexed(aInheritedItems, aIdentity, "inherited");
        Map<K, T> locLocalById = indexed(aLocalItems.items(), aIdentity, "local");
        LinkedHashMap<K, T> locResolved = new LinkedHashMap<>();

        locLocalById.forEach((locId, locLocalItem) -> {
            T locInheritedItem = locInheritedById.get(locId);
            locResolved.put(locId, locInheritedItem == null ? locLocalItem : aMerger.merge(locInheritedItem, locLocalItem));
        });

        if (aLocalItems.policy() == AInItemsInheritancePolicy.MERGE_MISSING_ITEMS) {
            locInheritedById.forEach(locResolved::putIfAbsent);
        }

        return List.copyOf(locResolved.values());
    }

    @Override
    public <T, K> List<T> mergeOnlyItems(List<T> aInheritedItems, List<T> aLocalItems, AIiItemIdentity<T, K> aIdentity) {
        Objects.requireNonNull(aInheritedItems, "inheritedItems");
        Objects.requireNonNull(aLocalItems, "localItems");
        Objects.requireNonNull(aIdentity, "identity");
        LinkedHashMap<K, T> locResolved = new LinkedHashMap<>(indexed(aInheritedItems, aIdentity, "inherited"));
        indexed(aLocalItems, aIdentity, "local").forEach(locResolved::put);
        return List.copyOf(locResolved.values());
    }

    private static <T, K> Map<K, T> indexed(List<T> aItems, AIiItemIdentity<T, K> aIdentity, String aSource) {
        LinkedHashMap<K, T> locResult = new LinkedHashMap<>();
        for (T locItem : aItems) {
            K locId = Objects.requireNonNull(aIdentity.identityOf(locItem), "item identity");
            if (locResult.putIfAbsent(locId, locItem) != null) {
                throw new IllegalArgumentException("Duplicate " + aSource + " item identity '" + locId + "'.");
            }
        }
        return locResult;
    }
}
