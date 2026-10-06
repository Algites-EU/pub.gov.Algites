package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.execution.AIngBuildExecutionFailurePolicy_1;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Tests the canonical descriptor representation of build execution failure policies. */
public class AItcPublicationPlannerPolicyTest {

    /** Verifies lower-snake-case endpoint policy conversion to the generated Java enum. */
    @Test
    public void testExecutionFailurePolicyWireValue() {
        var locEndpoint = AIcPublicationPlanner.endpoint(Map.of(
                "PublicationUri", "https://example.invalid/repository",
                "PublicationAdapter", "maven-repository",
                "ExecutionFailurePolicy", AIngBuildExecutionFailurePolicy_1.IGNORE_FAILURE.wireValue()), "primary");
        Assert.assertEquals(locEndpoint.executionFailurePolicy(), AIngBuildExecutionFailurePolicy_1.IGNORE_FAILURE);
    }

    /** Verifies propagation policy conversion to the generated Java enum. */
    @Test
    public void testExecutionFailurePropagationPolicyWireValue() {
        var locEndpoint = AIcPublicationPlanner.endpoint(Map.of(
                "PublicationUri", "https://example.invalid/repository",
                "PublicationAdapter", "maven-repository",
                "ExecutionFailurePolicy", AIngBuildExecutionFailurePolicy_1.PROPAGATE_FAILURE.wireValue()), "primary");
        Assert.assertEquals(locEndpoint.executionFailurePolicy(), AIngBuildExecutionFailurePolicy_1.PROPAGATE_FAILURE);
    }

    /** Verifies that Java-enum spelling is not accepted as descriptor syntax. */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testExecutionFailurePolicyRejectsUpperSnakeCaseWireValue() {
        AIcPublicationPlanner.endpoint(Map.of(
                "PublicationUri", "https://example.invalid/repository",
                "PublicationAdapter", "maven-repository",
                "ExecutionFailurePolicy", "IGNORE_FAILURE"), "primary");
    }

    /** Verifies lower-snake-case finalization policy conversion to the generated Java enum. */
    @Test
    public void testFinalizationExecutionFailurePolicyWireValue() {
        var locActions = AIcPublicationPlanner.outputPublicationFinalizationActions(Map.of(
                "OutputPublicationFinalizationActions", List.of(Map.of(
                        "Id", "probe",
                        "OutputPublicationFinalizationActionAdapter", "probe",
                        "ExecutionFailurePolicy", AIngBuildExecutionFailurePolicy_1.IGNORE_FAILURE.wireValue()))));
        Assert.assertEquals(locActions.get(0).executionFailurePolicy(), AIngBuildExecutionFailurePolicy_1.IGNORE_FAILURE);
    }

    /** Verifies the canonical-only value/default overload. */
    @Test
    public void testValueOrDefaultCanonicalFieldName() {
        Assert.assertEquals(
                AIcPublicationPlanner.AIcValueOrDefault(Map.of("CanonicalField", "configured"),
                        "default", "CanonicalField"),
                "configured");
        Assert.assertEquals(
                AIcPublicationPlanner.AIcValueOrDefault(Map.of(),
                        "default", "CanonicalField"),
                "default");
    }

    /** Verifies the explicit alternative-field-name overload remains available where required. */
    @Test
    public void testValueOrDefaultAlternativeFieldName() {
        Assert.assertEquals(
                AIcPublicationPlanner.AIcValueOrDefault(Map.of("alternativeField", "configured"),
                        "default", "CanonicalField", "alternativeField"),
                "configured");
    }

    /** Verifies that Java-enum spelling is not accepted for finalization actions. */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testFinalizationExecutionFailurePolicyRejectsUpperSnakeCaseWireValue() {
        AIcPublicationPlanner.outputPublicationFinalizationActions(Map.of(
                "OutputPublicationFinalizationActions", List.of(Map.of(
                        "Id", "probe",
                        "OutputPublicationFinalizationActionAdapter", "probe",
                        "ExecutionFailurePolicy", "IGNORE_FAILURE"))));
    }
}
