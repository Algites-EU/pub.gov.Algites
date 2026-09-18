package eu.algites.tool.build.credentials.coreintf;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public final class AItcCredentialProfileTest {
    @Test
    public void testCanonicalEnvironmentPrefixIncludesCredentialType() {
        AIcCredentialProfile locProfile = new AIcCredentialProfile("repsy-private-download", AInCredentialType.BASIC);
        Assert.assertEquals(
            locProfile.getEnvironmentPrefix(),
            "ALGITES_CREDENTIAL_REPSY_PRIVATE_DOWNLOAD_BASIC"
        );
        Assert.assertEquals(
            locProfile.getEnvironmentVariable(AInCredentialField.USERNAME),
            "ALGITES_CREDENTIAL_REPSY_PRIVATE_DOWNLOAD_BASIC_USERNAME"
        );
        Assert.assertEquals(locProfile.getStorageKey(), "repsy-private-download/basic");
    }

    @Test
    public void testCredentialTypeFieldContract() {
        Assert.assertEquals(AInCredentialType.BASIC.getRequiredFields(), List.of(AInCredentialField.USERNAME, AInCredentialField.PASSWORD));
        Assert.assertEquals(AInCredentialType.BEARER.getRequiredFields(), List.of(AInCredentialField.TOKEN));
        Assert.assertEquals(AInCredentialType.API_KEY.getRequiredFields(), List.of(AInCredentialField.API_KEY));
        Assert.assertEquals(
            AInCredentialType.CLIENT_CERTIFICATE.getRequiredFields(),
            List.of(AInCredentialField.CERTIFICATE, AInCredentialField.PRIVATE_KEY)
        );
        Assert.assertEquals(
            AInCredentialType.CLIENT_CERTIFICATE.getOptionalFields(),
            List.of(AInCredentialField.PRIVATE_KEY_PASSWORD)
        );
    }

    @Test
    public void testDifferentTypesHaveDifferentStorageKeys() {
        AIcCredentialProfile locBasic = new AIcCredentialProfile("repository-profile", AInCredentialType.BASIC);
        AIcCredentialProfile locBearer = new AIcCredentialProfile("repository-profile", AInCredentialType.BEARER);
        Assert.assertNotEquals(locBasic.getStorageKey(), locBearer.getStorageKey());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectsNonCanonicalProfileId() {
        new AIcCredentialProfile("Repsy.Private", AInCredentialType.BASIC);
    }
}
