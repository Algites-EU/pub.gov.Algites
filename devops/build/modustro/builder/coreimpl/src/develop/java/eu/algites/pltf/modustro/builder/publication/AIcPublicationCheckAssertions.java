package eu.algites.pltf.modustro.builder.publication;

import java.util.Objects;
import java.math.BigDecimal;

/** Assertion helpers shared by dependency-free executable regression scenarios. */
final class AIcPublicationCheckAssertions {
    private AIcPublicationCheckAssertions() { }
    static void assertTrue(boolean aCondition, String... aMessage) { if (!aCondition) throw new AssertionError(aMessage.length == 0 ? "Expected true." : aMessage[0]); }
    static void assertFalse(boolean aCondition, String... aMessage) { assertTrue(!aCondition, aMessage); }
    static void assertEquals(Object aActual, Object aExpected, String... aMessage) {
        boolean locEqual = aActual instanceof Number && aExpected instanceof Number
                ? new BigDecimal(aActual.toString()).compareTo(new BigDecimal(aExpected.toString())) == 0
                : Objects.deepEquals(aActual, aExpected);
        if (!locEqual) throw new AssertionError("Expected " + aExpected + ", actual " + aActual);
    }
}
