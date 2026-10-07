package eu.algites.pltf.modustro.builder.publication;

import org.testng.annotations.Test;

/** Exercises Maven snapshot metadata and S3 diagnostics against real local HTTP endpoints. */
public final class AItcPublicationMetadataTest {
    @Test public void AIcPublishesMetadataAndReportsS3Failure() throws Exception { AIcPublicationMetadataChecks.run(); }
}
