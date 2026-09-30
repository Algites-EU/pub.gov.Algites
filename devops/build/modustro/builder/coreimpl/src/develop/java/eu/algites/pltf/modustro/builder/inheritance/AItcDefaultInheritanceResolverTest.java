package eu.algites.pltf.modustro.builder.inheritance;

import eu.algites.pltf.modustro.builder.model.inheritance.AIcInheritedItems;
import eu.algites.pltf.modustro.builder.model.inheritance.AIcInheritedValue;
import eu.algites.pltf.modustro.builder.model.inheritance.AInInheritedValueState;
import eu.algites.pltf.modustro.builder.model.inheritance.AInItemsInheritancePolicy;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests portable scalar and keyed-item inheritance semantics.
 */
public final class AItcDefaultInheritanceResolverTest {

    @Test
    public void inheritedScalarIsKeptWhenLocalValueIsAbsent() {
        AIcDefaultInheritanceResolver locResolver = new AIcDefaultInheritanceResolver();
        AIcInheritedValue<String> locResolved = locResolver.resolveScalar(AIcInheritedValue.of("parent"), AIcInheritedValue.absent());
        Assert.assertEquals(locResolved.state(), AInInheritedValueState.VALUE);
        Assert.assertEquals(locResolved.value(), "parent");
    }

    @Test
    public void explicitNullClearsInheritedScalar() {
        AIcDefaultInheritanceResolver locResolver = new AIcDefaultInheritanceResolver();
        AIcInheritedValue<String> locResolved = locResolver.resolveScalar(AIcInheritedValue.of("parent"), AIcInheritedValue.clear());
        Assert.assertEquals(locResolved.state(), AInInheritedValueState.CLEAR);
        Assert.assertNull(locResolved.valueOrNull());
    }

    @Test
    public void mergeMissingItemsRetainsInheritedMembershipAndMergesMatchingItems() {
        AIcDefaultInheritanceResolver locResolver = new AIcDefaultInheritanceResolver();
        List<String> locResolved = locResolver.resolveItems(
            List.of("a:parent", "b:parent"),
            AIcInheritedItems.of(AInItemsInheritancePolicy.MERGE_MISSING_ITEMS, List.of("a:local", "c:local")),
            locItem -> locItem.substring(0, 1),
            (locInherited, locLocal) -> locInherited + "+" + locLocal
        );
        Assert.assertEquals(locResolved, List.of("a:parent+a:local", "c:local", "b:parent"));
    }

    @Test
    public void removeMissingItemsDropsInheritedOnlyMembershipButStillMergesMatchingItems() {
        AIcDefaultInheritanceResolver locResolver = new AIcDefaultInheritanceResolver();
        List<String> locResolved = locResolver.resolveItems(
            List.of("a:parent", "b:parent"),
            AIcInheritedItems.of(AInItemsInheritancePolicy.REMOVE_MISSING_ITEMS, List.of("a:local")),
            locItem -> locItem.substring(0, 1),
            (locInherited, locLocal) -> locInherited + "+" + locLocal
        );
        Assert.assertEquals(locResolved, List.of("a:parent+a:local"));
    }

    @Test
    public void mergeOnlyItemsNeverRemoveInheritedMembership() {
        AIcDefaultInheritanceResolver locResolver = new AIcDefaultInheritanceResolver();
        Assert.assertEquals(
            locResolver.mergeOnlyItems(List.of("product_api"), List.of("develop_implementation"), locItem -> locItem),
            List.of("product_api", "develop_implementation")
        );
    }
}
