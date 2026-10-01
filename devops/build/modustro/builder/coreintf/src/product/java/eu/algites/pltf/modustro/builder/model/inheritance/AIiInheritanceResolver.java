package eu.algites.pltf.modustro.builder.model.inheritance;

import java.util.List;

/**
 * Resolves portable hierarchical scalar and item semantics independently of Gradle.
 */
public interface AIiInheritanceResolver {

    /**
     * Resolves a local tri-state scalar contribution against an inherited value.
     *
     * @param <T> scalar value type
     * @param aInheritedValue inherited scalar contribution
     * @param aLocalValue local scalar contribution
     * @return effective scalar contribution
     */
    <T> AIcInheritedValue<T> resolveScalar(AIcInheritedValue<T> aInheritedValue, AIcInheritedValue<T> aLocalValue);

    /**
     * Resolves a keyed local item collection against inherited items.
     *
     * @param <T> item type
     * @param <K> stable item-identity type
     * @param aInheritedItems inherited items
     * @param aLocalItems local item contribution including membership policy
     * @param aIdentity stable item-identity resolver
     * @param aMerger merger for same-identity inherited and local items
     * @return immutable effective item list
     */
    <T, K> List<T> resolveItems(
        List<T> aInheritedItems,
        AIcInheritedItems<T> aLocalItems,
        AIiItemIdentity<T, K> aIdentity,
        AIiItemMerger<T> aMerger
    );

    /**
     * Merges keyed local items into inherited items without removing inherited-only members.
     *
     * @param <T> item type
     * @param <K> stable item-identity type
     * @param aInheritedItems inherited items
     * @param aLocalItems local items
     * @param aIdentity stable item-identity resolver
     * @return immutable merged item list
     */
    <T, K> List<T> mergeOnlyItems(
        List<T> aInheritedItems,
        List<T> aLocalItems,
        AIiItemIdentity<T, K> aIdentity
    );
}
