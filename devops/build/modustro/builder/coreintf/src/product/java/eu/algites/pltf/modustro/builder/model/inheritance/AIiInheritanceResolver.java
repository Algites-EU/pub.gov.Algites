package eu.algites.pltf.modustro.builder.model.inheritance;

import java.util.List;

/**
 * Resolves portable hierarchical scalar and item semantics independently of Gradle.
 */
public interface AIiInheritanceResolver {

    <T> AIcInheritedValue<T> resolveScalar(AIcInheritedValue<T> aInheritedValue, AIcInheritedValue<T> aLocalValue);

    <T, K> List<T> resolveItems(
        List<T> aInheritedItems,
        AIcInheritedItems<T> aLocalItems,
        AIiItemIdentity<T, K> aIdentity,
        AIiItemMerger<T> aMerger
    );

    <T, K> List<T> mergeOnlyItems(
        List<T> aInheritedItems,
        List<T> aLocalItems,
        AIiItemIdentity<T, K> aIdentity
    );
}
