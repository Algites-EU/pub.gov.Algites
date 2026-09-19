package eu.algites.tool.build.credentials.coreimpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.algites.tool.build.credentials.coreintf.AIcCredential;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialProfile;
import eu.algites.tool.build.credentials.coreintf.AInCredentialField;
import eu.algites.tool.build.credentials.coreintf.AInCredentialValueSource;
import eu.algites.tool.build.credentials.coreintf.AIxCredentialException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Persistent credential management facade used by the Algites CLI.
 */
public final class AIcCredentialService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AIcCredentialStoreProvider storeProvider;

    public AIcCredentialService() {
        this(new AIcCredentialStoreProvider());
    }

    public AIcCredentialService(AIcCredentialStoreProvider aStoreProvider) {
        storeProvider = Objects.requireNonNull(aStoreProvider, "Credential store provider must not be null.");
    }

    public String getStoreId() {
        return storeProvider.requireAvailableStore().getId();
    }

    public boolean isStored(AIcCredentialProfile aProfile) {
        Objects.requireNonNull(aProfile, "Credential profile must not be null.");
        ObjectNode locRoot = readCredentialDocumentObject();
        JsonNode locProfile = locRoot.get(aProfile.getId());
        if (locProfile == null || !locProfile.isObject()) {
            return false;
        }
        JsonNode locType = locProfile.get(aProfile.getType().getId());
        if (locType == null || !locType.isObject()) {
            return false;
        }
        return aProfile.getType().getRequiredFields().stream()
            .allMatch(locField -> locType.has(locField.getId()));
    }

    public void store(AIcCredentialProfile aProfile, AIcCredential aCredential) {
        Objects.requireNonNull(aProfile, "Credential profile must not be null.");
        Objects.requireNonNull(aCredential, "Credential must not be null.");
        if (!aCredential.satisfies(aProfile)) {
            throw new AIxCredentialException(
                "Credential does not contain all fields required by profile '" + aProfile.getId() + "'."
            );
        }

        ObjectNode locRoot = readCredentialDocumentObject();
        ObjectNode locProfile = objectChild(locRoot, aProfile.getId());
        ObjectNode locTypedCredential = OBJECT_MAPPER.createObjectNode();
        for (AInCredentialField locField : aProfile.getType().getSupportedFields()) {
            Optional<char[]> locValue = aCredential.getValue(locField);
            if (locValue.isEmpty()) {
                continue;
            }
            char[] locChars = locValue.get();
            try {
                ObjectNode locFieldNode = OBJECT_MAPPER.createObjectNode();
                locFieldNode.put("source", AInCredentialValueSource.DIRECT_VALUE.name());
                locFieldNode.put("value", new String(locChars));
                locTypedCredential.set(locField.getId(), locFieldNode);
            } finally {
                Arrays.fill(locChars, '\0');
            }
        }
        locProfile.set(aProfile.getType().getId(), locTypedCredential);
        writeCredentialDocumentObject(locRoot);
    }

    public void remove(AIcCredentialProfile aProfile) {
        Objects.requireNonNull(aProfile, "Credential profile must not be null.");
        ObjectNode locRoot = readCredentialDocumentObject();
        JsonNode locProfileNode = locRoot.get(aProfile.getId());
        if (!(locProfileNode instanceof ObjectNode locProfile)) {
            return;
        }
        locProfile.remove(aProfile.getType().getId());
        if (locProfile.size() == 0) {
            locRoot.remove(aProfile.getId());
        }
        if (locRoot.size() == 0) {
            removeCredentialDocument();
        } else {
            writeCredentialDocumentObject(locRoot);
        }
    }

    public boolean isCredentialDocumentStored() {
        Optional<byte[]> locValue = storeProvider.readCredentialDocument();
        if (locValue.isEmpty()) {
            return false;
        }
        byte[] locBytes = locValue.get();
        Arrays.fill(locBytes, (byte) 0);
        return true;
    }

    public Optional<char[]> readCredentialDocument() {
        Optional<byte[]> locValue = storeProvider.readCredentialDocument();
        if (locValue.isEmpty()) {
            return Optional.empty();
        }
        byte[] locBytes = locValue.get();
        try {
            return Optional.of(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(locBytes)).toString().toCharArray());
        } finally {
            Arrays.fill(locBytes, (byte) 0);
        }
    }

    public void storeCredentialDocument(char[] aDocument) {
        Objects.requireNonNull(aDocument, "Credential document must not be null.");
        String locDocument = new String(aDocument);
        ObjectNode locRoot = parseCredentialDocumentObject(locDocument);
        writeCredentialDocumentObject(locRoot);
    }

    public void removeCredentialDocument() {
        storeProvider.deleteCredentialDocument();
    }

    public boolean isNamedSecretStored(String aSecretId) {
        Optional<byte[]> locValue = storeProvider.readNamedSecret(aSecretId);
        if (locValue.isEmpty()) {
            return false;
        }
        byte[] locBytes = locValue.get();
        Arrays.fill(locBytes, (byte) 0);
        return true;
    }

    public Optional<char[]> readNamedSecret(String aSecretId) {
        Optional<byte[]> locValue = storeProvider.readNamedSecret(aSecretId);
        if (locValue.isEmpty()) {
            return Optional.empty();
        }
        byte[] locBytes = locValue.get();
        try {
            return Optional.of(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(locBytes)).toString().toCharArray());
        } finally {
            Arrays.fill(locBytes, (byte) 0);
        }
    }

    public void storeNamedSecret(String aSecretId, char[] aValue) {
        Objects.requireNonNull(aValue, "Secret value must not be null.");
        byte[] locEncoded = encodeUtf8(aValue);
        try {
            storeProvider.writeNamedSecret(aSecretId, locEncoded);
        } finally {
            Arrays.fill(locEncoded, (byte) 0);
        }
    }

    public void removeNamedSecret(String aSecretId) {
        storeProvider.deleteNamedSecret(aSecretId);
    }

    private ObjectNode readCredentialDocumentObject() {
        Optional<char[]> locDocument = readCredentialDocument();
        if (locDocument.isEmpty()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        char[] locChars = locDocument.get();
        try {
            return parseCredentialDocumentObject(new String(locChars));
        } finally {
            Arrays.fill(locChars, '\0');
        }
    }

    private static ObjectNode parseCredentialDocumentObject(String aDocument) {
        try {
            JsonNode locNode = OBJECT_MAPPER.readTree(aDocument);
            if (!(locNode instanceof ObjectNode locObject)) {
                throw new AIxCredentialException("ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS must contain a JSON object.");
            }
            return locObject;
        } catch (IOException aException) {
            throw new AIxCredentialException("ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS does not contain valid JSON.", aException);
        }
    }

    private void writeCredentialDocumentObject(ObjectNode aRoot) {
        byte[] locEncoded;
        try {
            locEncoded = OBJECT_MAPPER.writeValueAsBytes(aRoot);
        } catch (IOException aException) {
            throw new AIxCredentialException("Cannot serialize ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS.", aException);
        }
        try {
            storeProvider.writeCredentialDocument(locEncoded);
        } finally {
            Arrays.fill(locEncoded, (byte) 0);
        }
    }

    private static ObjectNode objectChild(ObjectNode aParent, String aName) {
        JsonNode locCurrent = aParent.get(aName);
        if (locCurrent == null || locCurrent.isNull()) {
            ObjectNode locCreated = OBJECT_MAPPER.createObjectNode();
            aParent.set(aName, locCreated);
            return locCreated;
        }
        if (locCurrent instanceof ObjectNode locObject) {
            return locObject;
        }
        throw new AIxCredentialException("ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS entry '" + aName + "' must be a JSON object.");
    }

    private static byte[] encodeUtf8(char[] aValue) {
        ByteBuffer locBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(aValue));
        byte[] locResult = new byte[locBuffer.remaining()];
        locBuffer.get(locResult);
        if (locBuffer.hasArray()) {
            Arrays.fill(locBuffer.array(), (byte) 0);
        }
        return locResult;
    }

    public String getStoreDiagnostics() {
        return storeProvider.buildUnavailableStoreMessage();
    }
}
