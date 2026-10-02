package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import eu.algites.pltf.modustro.builder.model.publication.AIcGlobalPublicationDeployMetadata;
import eu.algites.pltf.modustro.builder.model.publication.AInGlobalPublicationState;
import java.time.Instant;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests the server-controlled global publication deploy-metadata lifecycle.
 */
public final class AItcGlobalPublicationDeployMetadataResolverTest {

    /** Verifies draft replacement and release transition semantics. */
    @Test
    public void AIcAdvancesDraftRevisionAndFreezesReleaseRevision() {
        AIcGlobalPublicationDeployMetadataResolver locResolver = new AIcGlobalPublicationDeployMetadataResolver();
        Instant locT1 = Instant.parse("2026-10-02T08:00:00Z");
        Instant locT2 = Instant.parse("2026-10-02T09:00:00Z");
        Instant locT3 = Instant.parse("2026-10-02T10:00:00Z");

        AIcGlobalPublicationDeployMetadata locDraft = locResolver.initial(
            "eu/algites/example/example_1.yamldef.schema.json",
            AInGlobalPublicationState.DRAFT,
            locT1,
            "ci:test"
        );
        Assert.assertEquals(locDraft.publicationRevision(), 1L);

        AIcGlobalPublicationDeployMetadata locReplaced = locResolver.transition(
            locDraft,
            AInGlobalPublicationState.DRAFT,
            true,
            locT2,
            "ci:test"
        );
        Assert.assertEquals(locReplaced.publicationRevision(), 2L);

        AIcGlobalPublicationDeployMetadata locReleased = locResolver.transition(
            locReplaced,
            AInGlobalPublicationState.RELEASE,
            false,
            locT3,
            "ci:test"
        );
        Assert.assertEquals(locReleased.publicationRevision(), 2L);
        Assert.assertEquals(locReleased.releasedAt(), locT3);
    }

    /** Verifies that released content cannot be replaced. */
    @Test(expectedExceptions = AIxModelValidationException.class)
    public void AIcRejectsReleasedContentReplacement() {
        AIcGlobalPublicationDeployMetadataResolver locResolver = new AIcGlobalPublicationDeployMetadataResolver();
        AIcGlobalPublicationDeployMetadata locReleased = locResolver.initial(
            "eu/algites/example/example_1.yamldef.schema.json",
            AInGlobalPublicationState.RELEASE,
            Instant.parse("2026-10-02T08:00:00Z"),
            null
        );
        locResolver.transition(
            locReleased,
            AInGlobalPublicationState.RELEASE,
            true,
            Instant.parse("2026-10-02T09:00:00Z"),
            null
        );
    }
}
