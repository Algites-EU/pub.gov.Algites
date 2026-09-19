package eu.algites.tool.build.credentials.coreimpl;

import eu.algites.tool.build.credentials.coreintf.AIcCredential;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialProfile;
import eu.algites.tool.build.credentials.coreintf.AInCredentialField;
import eu.algites.tool.build.credentials.coreintf.AInCredentialType;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialStoreAvailability;
import eu.algites.tool.build.credentials.coreintf.AIiCredentialStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AItcCredentialDocumentProviderTest {
    @Test
    public void testResolvesDirectAndSecretContent() {
        AIcCredentialDocumentProvider locProvider = new AIcCredentialDocumentProvider(
            Map.of(
                "ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS",
                "{\"profile\":{\"basic\":{\"username\":{\"source\":\"DIRECT_VALUE\",\"value\":\"user\"}," +
                    "\"password\":{\"source\":\"SECRET_CONTENT\",\"value\":\"PASSWORD_SECRET\"}}}}",
                "ALGITES_CREDENTIAL_SECRETS_JSON",
                "{\"PASSWORD_SECRET\":\"secret\"}"
            ),
            Path.of(".")
        );

        AIcCredentialProfile locProfile = new AIcCredentialProfile("profile", AInCredentialType.BASIC);
        try (AIcCredential locCredential = locProvider.resolve(locProfile).orElseThrow()) {
            Assert.assertEquals(new String(locCredential.getValue(AInCredentialField.USERNAME).orElseThrow()), "user");
            Assert.assertEquals(new String(locCredential.getValue(AInCredentialField.PASSWORD).orElseThrow()), "secret");
        }
    }

    @Test
    public void testResolvesFileAndEnvironmentContent() throws Exception {
        Path locDirectory = Files.createTempDirectory("algites-credential-document-test");
        Path locCertificate = locDirectory.resolve("certificate.pem");
        Files.writeString(locCertificate, "certificate-content");
        try {
            AIcCredentialDocumentProvider locProvider = new AIcCredentialDocumentProvider(
                Map.of(
                    "ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS",
                    "{\"profile\":{\"certificate\":{\"certificate\":{\"source\":\"FILE_CONTENT\",\"value\":\"certificate.pem\"}," +
                        "\"privateKeyPassword\":{\"source\":\"ENVIRONMENT_VARIABLE_CONTENT\",\"value\":\"KEY_PASSWORD\"}}}}",
                    "KEY_PASSWORD",
                    "password"
                ),
                locDirectory
            );

            AIcCredentialProfile locProfile = new AIcCredentialProfile("profile", AInCredentialType.CERTIFICATE);
            try (AIcCredential locCredential = locProvider.resolve(locProfile).orElseThrow()) {
                Assert.assertEquals(new String(locCredential.getValue(AInCredentialField.CERTIFICATE).orElseThrow()), "certificate-content");
                Assert.assertEquals(new String(locCredential.getValue(AInCredentialField.PRIVATE_KEY_PASSWORD).orElseThrow()), "password");
                Assert.assertFalse(locCredential.contains(AInCredentialField.PRIVATE_KEY));
            }
        } finally {
            Files.deleteIfExists(locCertificate);
            Files.deleteIfExists(locDirectory);
        }
    }
    @Test
    public void testResolvesSecretContentFromLocalSecureStore() {
        AIcTestCredentialStore locStore = new AIcTestCredentialStore();
        locStore.write("secret/PASSWORD_SECRET", "local-secret".getBytes(StandardCharsets.UTF_8));
        AIcCredentialDocumentProvider locProvider = new AIcCredentialDocumentProvider(
            Map.of(
                "ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS",
                "{\"profile\":{\"basic\":{\"username\":{\"source\":\"DIRECT_VALUE\",\"value\":\"user\"}," +
                    "\"password\":{\"source\":\"SECRET_CONTENT\",\"value\":\"PASSWORD_SECRET\"}}}}"
            ),
            Path.of("."),
            new AIcCredentialStoreProvider(List.of(locStore))
        );

        AIcCredentialProfile locProfile = new AIcCredentialProfile("profile", AInCredentialType.BASIC);
        try (AIcCredential locCredential = locProvider.resolve(locProfile).orElseThrow()) {
            Assert.assertEquals(new String(locCredential.getValue(AInCredentialField.PASSWORD).orElseThrow()), "local-secret");
        }
    }

    @Test
    public void testLoadsCredentialDocumentFromLocalSecureStore() {
        AIcTestCredentialStore locStore = new AIcTestCredentialStore();
        locStore.write(
            AIcCredentialStoreProvider.CREDENTIAL_DOCUMENT_STORAGE_KEY,
            ("{\"profile\":{\"basic\":{\"username\":{\"source\":\"DIRECT_VALUE\",\"value\":\"user\"}," +
                "\"password\":{\"source\":\"SECRET_CONTENT\",\"value\":\"PASSWORD_SECRET\"}}}}")
                .getBytes(StandardCharsets.UTF_8)
        );
        locStore.write("secret/PASSWORD_SECRET", "local-secret".getBytes(StandardCharsets.UTF_8));
        AIcCredentialDocumentProvider locProvider = new AIcCredentialDocumentProvider(
            Map.of(),
            Path.of("."),
            new AIcCredentialStoreProvider(List.of(locStore))
        );

        AIcCredentialProfile locProfile = new AIcCredentialProfile("profile", AInCredentialType.BASIC);
        try (AIcCredential locCredential = locProvider.resolve(locProfile).orElseThrow()) {
            Assert.assertEquals(new String(locCredential.getValue(AInCredentialField.USERNAME).orElseThrow()), "user");
            Assert.assertEquals(new String(locCredential.getValue(AInCredentialField.PASSWORD).orElseThrow()), "local-secret");
        }
    }

    private static final class AIcTestCredentialStore implements AIiCredentialStore {
        private final Map<String, byte[]> values = new HashMap<>();

        @Override
        public String getId() {
            return "test";
        }

        @Override
        public int getPriority() {
            return 1;
        }

        @Override
        public AIcCredentialStoreAvailability getAvailability() {
            return AIcCredentialStoreAvailability.available("available");
        }

        @Override
        public Optional<byte[]> read(String aStorageKey) {
            byte[] locValue = values.get(aStorageKey);
            return locValue == null ? Optional.empty() : Optional.of(locValue.clone());
        }

        @Override
        public void write(String aStorageKey, byte[] aValue) {
            values.put(aStorageKey, aValue.clone());
        }

        @Override
        public void delete(String aStorageKey) {
            values.remove(aStorageKey);
        }
    }

}
