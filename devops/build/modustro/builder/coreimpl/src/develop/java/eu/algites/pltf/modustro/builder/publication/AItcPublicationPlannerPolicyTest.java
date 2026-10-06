package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AInFinalizationActionFailurePolicy;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationFailurePolicy;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Tests the lower-snake-case descriptor representation of publication failure policies. */
public class AItcPublicationPlannerPolicyTest {

    /** Verifies lower-snake-case endpoint policy conversion to the internal Java enum. */
    @Test
    public void testPublicationFailurePolicyWireValue() {
        var locEndpoint = AIcPublicationPlanner.endpoint(Map.of(
                "PublicationUri", "https://example.invalid/repository",
                "PublicationAdapter", "maven-repository",
                "PublicationFailurePolicy", "ignore_publication_failure"), "primary");
        Assert.assertEquals(locEndpoint.publicationFailurePolicy(), AInPublicationFailurePolicy.IGNORE_PUBLICATION_FAILURE);
    }

    /** Verifies that obsolete Java-enum spelling is not accepted as descriptor syntax. */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testPublicationFailurePolicyRejectsUpperSnakeCaseWireValue() {
        AIcPublicationPlanner.endpoint(Map.of(
                "PublicationUri", "https://example.invalid/repository",
                "PublicationAdapter", "maven-repository",
                "PublicationFailurePolicy", "IGNORE_PUBLICATION_FAILURE"), "primary");
    }

    /** Verifies lower-snake-case finalization policy conversion to the internal Java enum. */
    @Test
    public void testFinalizationFailurePolicyWireValue() {
        var locActions = AIcPublicationPlanner.outputPublicationFinalizationActions(Map.of(
                "OutputPublicationFinalizationActions", List.of(Map.of(
                        "Id", "probe",
                        "OutputPublicationFinalizationActionAdapter", "probe",
                        "FailurePolicy", "ignore_failure"))));
        Assert.assertEquals(locActions.get(0).failurePolicy(), AInFinalizationActionFailurePolicy.IGNORE_FAILURE);
    }

    /** Verifies the canonical-only value/default overload. */
    @Test
    public void testValueOrDefaultCanonicalFieldName() {
        Assert.assertEquals(
                AIcPublicationPlanner.AIcValueOrDefault(Map.of("FailurePolicy", "ignore_failure"),
                        "fail_build_on_failure", "FailurePolicy"),
                "ignore_failure");
        Assert.assertEquals(
                AIcPublicationPlanner.AIcValueOrDefault(Map.of(),
                        "fail_build_on_failure", "FailurePolicy"),
                "fail_build_on_failure");
    }

    /** Verifies the explicit alternative-field-name overload remains available where required. */
    @Test
    public void testValueOrDefaultAlternativeFieldName() {
        Assert.assertEquals(
                AIcPublicationPlanner.AIcValueOrDefault(Map.of("failurePolicy", "ignore_failure"),
                        "fail_build_on_failure", "FailurePolicy", "failurePolicy"),
                "ignore_failure");
    }

    /** Verifies that obsolete Java-enum spelling is not accepted for finalization actions. */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testFinalizationFailurePolicyRejectsUpperSnakeCaseWireValue() {
        AIcPublicationPlanner.outputPublicationFinalizationActions(Map.of(
                "OutputPublicationFinalizationActions", List.of(Map.of(
                        "Id", "probe",
                        "OutputPublicationFinalizationActionAdapter", "probe",
                        "FailurePolicy", "IGNORE_FAILURE"))));
    }
}
