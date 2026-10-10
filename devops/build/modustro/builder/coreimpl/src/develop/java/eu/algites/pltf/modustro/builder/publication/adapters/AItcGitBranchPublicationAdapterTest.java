package eu.algites.pltf.modustro.builder.publication.adapters;

import java.net.URI;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Verifies that a Git publication uses only a matching authenticated repository remote. */
public class AItcGitBranchPublicationAdapterTest {

    @Test
    public void testTokenAuthenticatedOriginMatchesDeclaredRepository() {
        Assert.assertTrue(AIcGitBranchPublicationAdapter.AIcRemoteMatchesPublication(
                "https://x-access-token:token-should-never-be-logged@github.com/Algites-EU/pub.tool.Security.git",
                URI.create("https://github.com/Algites-EU/pub.tool.Security.git")));
    }

    @Test
    public void testUnrelatedOriginIsRejected() {
        Assert.assertFalse(AIcGitBranchPublicationAdapter.AIcRemoteMatchesPublication(
                "https://x-access-token:secret@github.com/Algites-EU/other.git",
                URI.create("https://github.com/Algites-EU/pub.tool.Security.git")));
    }

    @Test
    public void testRemoteHostMismatchIsRejected() {
        Assert.assertFalse(AIcGitBranchPublicationAdapter.AIcRemoteMatchesPublication(
                "https://example.invalid/Algites-EU/pub.tool.Security.git",
                URI.create("https://github.com/Algites-EU/pub.tool.Security.git")));
    }

    @Test
    public void testAuthenticationIsRedactedFromGitErrors() {
        var locSafeOutput = AIcGitBranchPublicationAdapter.AIcRedactGitOutput(
                "fatal: https://x-access-token:private-token@github.com/Algites-EU/repo.git denied");
        Assert.assertFalse(locSafeOutput.contains("private-token"));
        Assert.assertTrue(locSafeOutput.contains("[REDACTED]"));
    }

    @Test
    public void testMissingOrInvalidRemoteIsRejected() {
        Assert.assertFalse(AIcGitBranchPublicationAdapter.AIcRemoteMatchesPublication(
                "not a valid remote URL", URI.create("https://github.com/Algites-EU/pub.tool.Security.git")));
    }
}
