package eu.algites.tool.build.credentials.coreimpl;

import eu.algites.tool.build.credentials.coreintf.AIcCredential;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialProfile;
import eu.algites.tool.build.credentials.coreintf.AInCredentialField;
import eu.algites.tool.build.credentials.coreintf.AInCredentialType;
import eu.algites.tool.build.credentials.coreintf.AIxCredentialException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AItcEnvironmentCredentialProviderTest {
    @Test
    public void testResolvesCompleteBasicProfile() {
        AIcEnvironmentCredentialProvider locProvider = new AIcEnvironmentCredentialProvider(
            Map.of(
                "ALGITES_CREDENTIAL_REPSY_PRIVATE_BASIC_USERNAME", "user",
                "ALGITES_CREDENTIAL_REPSY_PRIVATE_BASIC_PASSWORD", "secret"
            )
        );
        AIcCredentialProfile locProfile = new AIcCredentialProfile("repsy-private", AInCredentialType.BASIC);
        Optional<AIcCredential> locCredential = locProvider.resolve(locProfile);
        Assert.assertTrue(locCredential.isPresent());
        try (AIcCredential locValue = locCredential.orElseThrow()) {
            Assert.assertEquals(new String(locValue.getValue(AInCredentialField.USERNAME).orElseThrow()), "user");
            Assert.assertEquals(new String(locValue.getValue(AInCredentialField.PASSWORD).orElseThrow()), "secret");
        }
    }

    @Test
    public void testBearerVariableContract() {
        AIcCredentialProfile locProfile = new AIcCredentialProfile("algites-java-private-release-download", AInCredentialType.BEARER);
        Assert.assertEquals(
            AIcEnvironmentCredentialProvider.getRequiredEnvironmentVariables(locProfile),
            List.of("ALGITES_CREDENTIAL_ALGITES_JAVA_PRIVATE_RELEASE_DOWNLOAD_BEARER_TOKEN")
        );
    }

    @Test(expectedExceptions = AIxCredentialException.class)
    public void testRejectsPartialProfile() {
        AIcEnvironmentCredentialProvider locProvider = new AIcEnvironmentCredentialProvider(
            Map.of("ALGITES_CREDENTIAL_REPSY_PRIVATE_BASIC_USERNAME", "user")
        );
        locProvider.resolve(new AIcCredentialProfile("repsy-private", AInCredentialType.BASIC));
    }
}
