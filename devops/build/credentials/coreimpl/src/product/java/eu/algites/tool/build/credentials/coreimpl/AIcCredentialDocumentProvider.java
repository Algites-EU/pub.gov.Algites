package eu.algites.tool.build.credentials.coreimpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.algites.tool.build.credentials.coreintf.AIcCredential;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialProfile;
import eu.algites.tool.build.credentials.coreintf.AIiCredentialProvider;
import eu.algites.tool.build.credentials.coreintf.AInCredentialField;
import eu.algites.tool.build.credentials.coreintf.AInCredentialValueSource;
import eu.algites.tool.build.credentials.coreintf.AIxCredentialException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the universal Algites credential document supplied through ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS.
 */
public final class AIcCredentialDocumentProvider implements AIiCredentialProvider {
    public static final String CREDENTIALS_VARIABLE = "ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS";
    public static final String SECRET_CONTEXT_VARIABLE = "_TMP_ALGITES_CREDENTIAL_SECRETS_JSON";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, String> environment;
    private final Path baseDirectory;
    private final AIcCredentialStoreProvider storeProvider;

    public AIcCredentialDocumentProvider() {
        this(System.getenv(), Path.of(System.getProperty("user.dir")), new AIcCredentialStoreProvider());
    }

    AIcCredentialDocumentProvider(Map<String, String> aEnvironment, Path aBaseDirectory) {
        this(aEnvironment, aBaseDirectory, new AIcCredentialStoreProvider());
    }

    AIcCredentialDocumentProvider(
        Map<String, String> aEnvironment,
        Path aBaseDirectory,
        AIcCredentialStoreProvider aStoreProvider
    ) {
        environment = Map.copyOf(Objects.requireNonNull(aEnvironment, "Environment must not be null."));
        baseDirectory = Objects.requireNonNull(aBaseDirectory, "Base directory must not be null.").toAbsolutePath().normalize();
        storeProvider = Objects.requireNonNull(aStoreProvider, "Credential store provider must not be null.");
    }

    @Override
    public Optional<AIcCredential> resolve(AIcCredentialProfile aProfile) {
        Objects.requireNonNull(aProfile, "Credential profile must not be null.");
        String locDocument = loadCredentialDocument();
        if (locDocument == null || locDocument.isBlank()) {
            return Optional.empty();
        }

        JsonNode locRoot = parseObject(locDocument, CREDENTIALS_VARIABLE);
        JsonNode locProfile = locRoot.get(aProfile.getId());
        if (locProfile == null || locProfile.isNull()) {
            return Optional.empty();
        }
        if (!locProfile.isObject()) {
            throw new AIxCredentialException("Credential profile '" + aProfile.getId() + "' must be a JSON object.");
        }

        JsonNode locTypedCredential = locProfile.get(aProfile.getType().getId());
        if (locTypedCredential == null || locTypedCredential.isNull()) {
            return Optional.empty();
        }
        if (!locTypedCredential.isObject()) {
            throw new AIxCredentialException(
                "Credential profile '" + aProfile.getId() + "' type '" + aProfile.getType().getId() + "' must be a JSON object."
            );
        }

        JsonNode locSecrets = parseOptionalSecretContext();
        EnumMap<AInCredentialField, char[]> locValues = new EnumMap<>(AInCredentialField.class);
        try {
            for (AInCredentialField locField : aProfile.getType().getSupportedFields()) {
                JsonNode locFieldNode = locTypedCredential.get(locField.getId());
                if (locFieldNode == null || locFieldNode.isNull()) {
                    if (aProfile.getType().getRequiredFields().contains(locField)) {
                        throw new AIxCredentialException(
                            "Credential profile '" + aProfile.getId() + "' type '" + aProfile.getType().getId() +
                                "' is missing required field '" + locField.getId() + "'."
                        );
                    }
                    continue;
                }
                locValues.put(locField, resolveValue(aProfile, locField, locFieldNode, locSecrets));
            }

            AIcCredential locCredential = new AIcCredential(locValues);
            if (!locCredential.satisfies(aProfile)) {
                locCredential.close();
                throw new AIxCredentialException(
                    "Credential profile '" + aProfile.getId() + "' type '" + aProfile.getType().getId() + "' is incomplete."
                );
            }
            return Optional.of(locCredential);
        } finally {
            locValues.values().forEach(locValue -> Arrays.fill(locValue, '\0'));
        }
    }


    private String loadCredentialDocument() {
        String locEnvironmentDocument = environment.get(CREDENTIALS_VARIABLE);
        if (locEnvironmentDocument != null && !locEnvironmentDocument.isBlank()) {
            return locEnvironmentDocument;
        }

        Optional<byte[]> locStoredDocument = storeProvider.readCredentialDocument();
        if (locStoredDocument.isEmpty()) {
            return null;
        }
        byte[] locEncoded = locStoredDocument.get();
        try {
            return new String(locEncoded, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(locEncoded, (byte) 0);
        }
    }

    private char[] resolveValue(
        AIcCredentialProfile aProfile,
        AInCredentialField aField,
        JsonNode aFieldNode,
        JsonNode aSecrets
    ) {
        if (!aFieldNode.isObject()) {
            throw fieldError(aProfile, aField, "must be an object containing source and value.");
        }
        JsonNode locSourceNode = aFieldNode.get("source");
        JsonNode locValueNode = aFieldNode.get("value");
        if (locSourceNode == null || !locSourceNode.isTextual()) {
            throw fieldError(aProfile, aField, "is missing string property 'source'.");
        }
        if (locValueNode == null || !locValueNode.isTextual()) {
            throw fieldError(aProfile, aField, "is missing string property 'value'.");
        }

        AInCredentialValueSource locSource;
        try {
            locSource = AInCredentialValueSource.fromId(locSourceNode.textValue());
        } catch (IllegalArgumentException aException) {
            throw fieldError(aProfile, aField, aException.getMessage());
        }
        String locValue = locValueNode.textValue();

        return switch (locSource) {
            case DIRECT_VALUE -> locValue.toCharArray();
            case FILE_CONTENT -> readFileContent(aProfile, aField, locValue);
            case SECRET_CONTENT -> readSecretContent(aProfile, aField, locValue, aSecrets);
            case ENVIRONMENT_VARIABLE_CONTENT -> readEnvironmentVariableContent(aProfile, aField, locValue);
        };
    }

    private char[] readFileContent(AIcCredentialProfile aProfile, AInCredentialField aField, String aReference) {
        if (aReference.isBlank()) {
            throw fieldError(aProfile, aField, "FILE_CONTENT path must not be empty.");
        }
        Path locPath = Path.of(aReference);
        if (!locPath.isAbsolute()) {
            locPath = baseDirectory.resolve(locPath);
        }
        locPath = locPath.normalize();
        try {
            if (!Files.isRegularFile(locPath)) {
                throw fieldError(aProfile, aField, "references missing file '" + locPath + "'.");
            }
            return Files.readString(locPath, StandardCharsets.UTF_8).toCharArray();
        } catch (IOException aException) {
            throw new AIxCredentialException("Cannot read credential file '" + locPath + "'.", aException);
        }
    }

    private char[] readSecretContent(
        AIcCredentialProfile aProfile,
        AInCredentialField aField,
        String aReference,
        JsonNode aSecrets
    ) {
        if (aReference.isBlank()) {
            throw fieldError(aProfile, aField, "SECRET_CONTENT key must not be empty.");
        }
        JsonNode locSecret = aSecrets.get(aReference);
        if (locSecret != null && !locSecret.isNull()) {
            return locSecret.asText().toCharArray();
        }

        Optional<byte[]> locStoredSecret = storeProvider.readNamedSecret(aReference);
        if (locStoredSecret.isEmpty()) {
            throw fieldError(aProfile, aField, "references unavailable secret '" + aReference + "'.");
        }
        byte[] locEncoded = locStoredSecret.get();
        try {
            return StandardCharsets.UTF_8.decode(java.nio.ByteBuffer.wrap(locEncoded)).toString().toCharArray();
        } finally {
            Arrays.fill(locEncoded, (byte) 0);
        }
    }

    private char[] readEnvironmentVariableContent(
        AIcCredentialProfile aProfile,
        AInCredentialField aField,
        String aReference
    ) {
        if (aReference.isBlank()) {
            throw fieldError(aProfile, aField, "ENVIRONMENT_VARIABLE_CONTENT variable name must not be empty.");
        }
        String locValue = environment.get(aReference);
        if (locValue == null) {
            throw fieldError(aProfile, aField, "references unavailable environment variable '" + aReference + "'.");
        }
        return locValue.toCharArray();
    }

    private JsonNode parseOptionalSecretContext() {
        String locRaw = environment.get(SECRET_CONTEXT_VARIABLE);
        if (locRaw == null || locRaw.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        return parseObject(locRaw, SECRET_CONTEXT_VARIABLE);
    }

    private static JsonNode parseObject(String aJson, String aLabel) {
        try {
            JsonNode locNode = OBJECT_MAPPER.readTree(aJson);
            if (locNode == null || !locNode.isObject()) {
                throw new AIxCredentialException(aLabel + " must contain a JSON object.");
            }
            return locNode;
        } catch (IOException aException) {
            throw new AIxCredentialException(aLabel + " does not contain valid JSON.", aException);
        }
    }

    private static AIxCredentialException fieldError(
        AIcCredentialProfile aProfile,
        AInCredentialField aField,
        String aMessage
    ) {
        return new AIxCredentialException(
            "Credential '" + aProfile.getId() + "/" + aProfile.getType().getId() + "/" + aField.getId() + "' " + aMessage
        );
    }
}
