package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AInPublicationOutputKind;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Regression checks for record outputs configured independently of native publication bundles. */
public final class AItcSystemRecordOutputSelectorsTest {
    @Test
    public void AIcArtifactBuildRecordIsAnIndependentOutput() {
        Assert.assertEquals(AIcPublicationConfiguration.outputs("modustro_artifact_build_record"),
                List.of("modustro_artifact_build_record"));
        Assert.assertEquals(AInPublicationOutputKind.MODUSTRO_ARTIFACT_BUILD_RECORD.descriptorName(),
                "modustro_artifact_build_record");
    }

    @Test
    public void AIcVersionScopePublicationRecordIsAnIndependentOutput() {
        Assert.assertEquals(AIcPublicationConfiguration.outputs("modustro_version_scope_publication_record"),
                List.of("modustro_version_scope_publication_record"));
        Assert.assertEquals(AInPublicationOutputKind.MODUSTRO_VERSION_SCOPE_PUBLICATION_RECORD.descriptorName(),
                "modustro_version_scope_publication_record");
    }
}
