package eu.algites.pltf.modustro.builder.gradleinit;

import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Ensures publication failures reveal effective profile IDs without leaking secret values. */
public final class AItcModustroPublicationCredentialDiagnosticsTest {
    @Test
    public void AIcMissingSourceProfileShowsPreparedAndExecutionInputs() {
        Map<String, Object> locSourceEndpoint = Map.of(
            "id", "python-binaries-public-snapshot",
            "publicationAdapter", "python-repository",
            "publicationCredentialProfile", "python-public-snapshot"
        );
        Map<String, Object> locSourcePlan = Map.of("publicationEndpoints", List.of(locSourceEndpoint));
        Map<String, Object> locProfiles = Map.of("unrelated-profile", Map.of("type", "basic", "password", "DO_NOT_LOG_THIS_SECRET"));
        Map<String, Object> locPreparedProfiles = Map.of("python-public-snapshot", Map.of("type", "basic", "password", "DO_NOT_LOG_THIS_SECRET"));

        String locDiagnostic = AIcModustroPublicationCredentialDiagnostics.AIcDescribe(
            "native:python:sources", "pub.lib.General_naming.convention.intf", "python", "NATIVE_PRODUCT_SOURCES",
            locSourcePlan, locProfiles, locSourcePlan, locPreparedProfiles,
            List.of("native:python:binaries: outputKind=NATIVE_PRODUCT_BINARIES, availableProfileIds=[python-public-snapshot]"));

        Assert.assertTrue(locDiagnostic.contains("executionMissingProfileIds: [python-public-snapshot]"));
        Assert.assertTrue(locDiagnostic.contains("preparedProfileIdsMatchExecution: false"));
        Assert.assertTrue(locDiagnostic.contains("preparedSiblingOutputs:"));
        Assert.assertFalse(locDiagnostic.contains("publicationCredentialProfile"));
        Assert.assertFalse(locDiagnostic.contains("DO_NOT_LOG_THIS_SECRET"));
    }

    @Test
    public void AIcMatchingProfilesAreRecognized() {
        Map<String, Object> locPlan = Map.of("publicationEndpoints", List.of(Map.of(
            "id", "python-repo", "publicationCredentialProfile", "python-public-snapshot")));
        Map<String, Object> locProfiles = Map.of("python-public-snapshot", Map.of("type", "basic"));
        String locDiagnostic = AIcModustroPublicationCredentialDiagnostics.AIcDescribe(
            "native:python:binary", "example", "python", "NATIVE_PRODUCT_BINARIES",
            locPlan, locProfiles, locPlan, locProfiles, List.of());
        Assert.assertTrue(locDiagnostic.contains("executionMissingProfileIds: []"));
        Assert.assertTrue(locDiagnostic.contains("preparedProfileIdsMatchExecution: true"));
        Assert.assertTrue(locDiagnostic.contains("preparedPlanMatchesExecution: true"));
    }
}
