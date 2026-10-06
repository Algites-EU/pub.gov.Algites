package eu.algites.pltf.modustro.builder.publication;

import org.testng.annotations.Test;

/** Runs the portable finalization barrier scenarios in the normal TestNG suite. */
public final class AItcFinalizationBarriersTest {
    @Test public void AIcChecksFinalizationBarriers() throws Exception { AIcFinalizationBarrierChecks.run(); }
}
