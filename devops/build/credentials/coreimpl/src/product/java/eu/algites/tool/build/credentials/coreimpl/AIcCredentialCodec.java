package eu.algites.tool.build.credentials.coreimpl;

import eu.algites.tool.build.credentials.coreintf.AIcCredential;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialProfile;
import eu.algites.tool.build.credentials.coreintf.AInCredentialField;
import eu.algites.tool.build.credentials.coreintf.AIxCredentialException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

/**
 * Versioned binary codec for credential blobs stored by operating-system backends.
 */
final class AIcCredentialCodec {
    private static final int MAGIC = 0x414C4743;
    private static final int FORMAT_VERSION = 2;

    private AIcCredentialCodec() {
    }

    static byte[] encode(AIcCredentialProfile aProfile, AIcCredential aCredential) {
        if (!aCredential.satisfies(aProfile)) {
            throw new AIxCredentialException(
                "Credential does not contain all fields required by profile '" + aProfile.getId() + "'."
            );
        }

        List<AInCredentialField> locStoredFields = aProfile.getType().getSupportedFields().stream()
            .filter(aCredential::contains)
            .toList();

        try {
            AIcWipingByteArrayOutputStream locBytes = new AIcWipingByteArrayOutputStream();
            try (DataOutputStream locOutput = new DataOutputStream(locBytes)) {
                locOutput.writeInt(MAGIC);
                locOutput.writeInt(FORMAT_VERSION);
                locOutput.writeUTF(aProfile.getType().getId());
                locOutput.writeInt(locStoredFields.size());
                for (AInCredentialField locField : locStoredFields) {
                    char[] locChars = aCredential.getValue(locField).orElseThrow();
                    byte[] locEncoded = encodeUtf8(locChars);
                    try {
                        locOutput.writeUTF(locField.name());
                        locOutput.writeInt(locEncoded.length);
                        locOutput.write(locEncoded);
                    } finally {
                        Arrays.fill(locChars, '\0');
                        Arrays.fill(locEncoded, (byte) 0);
                    }
                }
            }
            byte[] locResult = locBytes.toByteArray();
            locBytes.wipe();
            return locResult;
        } catch (IOException aException) {
            throw new AIxCredentialException("Cannot encode credential profile '" + aProfile.getId() + "'.", aException);
        }
    }

    static AIcCredential decode(AIcCredentialProfile aProfile, byte[] aBlob) {
        EnumMap<AInCredentialField, char[]> locValues = new EnumMap<>(AInCredentialField.class);
        try (DataInputStream locInput = new DataInputStream(new ByteArrayInputStream(aBlob))) {
            if (locInput.readInt() != MAGIC) {
                throw new AIxCredentialException("Stored credential profile has an invalid format marker.");
            }
            int locVersion = locInput.readInt();
            if (locVersion != FORMAT_VERSION) {
                throw new AIxCredentialException("Unsupported stored credential format version " + locVersion + ".");
            }
            String locType = locInput.readUTF();
            if (!aProfile.getType().getId().equals(locType)) {
                throw new AIxCredentialException(
                    "Stored credential profile '" + aProfile.getId() + "' has type " + locType +
                        " but the requested profile has type " + aProfile.getType().getId() + "."
                );
            }
            int locFieldCount = locInput.readInt();
            if (locFieldCount < aProfile.getType().getRequiredFields().size() ||
                locFieldCount > aProfile.getType().getSupportedFields().size()) {
                throw new AIxCredentialException("Stored credential profile has an unexpected field count.");
            }
            for (int locIndex = 0; locIndex < locFieldCount; locIndex++) {
                AInCredentialField locField = AInCredentialField.valueOf(locInput.readUTF());
                if (!aProfile.getType().getSupportedFields().contains(locField) || locValues.containsKey(locField)) {
                    throw new AIxCredentialException("Stored credential profile contains an unexpected or duplicate field.");
                }
                int locLength = locInput.readInt();
                if (locLength < 0 || locLength > 16 * 1024 * 1024) {
                    throw new AIxCredentialException("Stored credential field has an invalid length.");
                }
                byte[] locEncoded = locInput.readNBytes(locLength);
                if (locEncoded.length != locLength) {
                    throw new AIxCredentialException("Stored credential field is truncated.");
                }
                try {
                    locValues.put(locField, decodeUtf8(locEncoded));
                } finally {
                    Arrays.fill(locEncoded, (byte) 0);
                }
            }
            if (locInput.available() != 0) {
                throw new AIxCredentialException("Stored credential profile contains trailing data.");
            }
            AIcCredential locCredential = new AIcCredential(locValues);
            if (!locCredential.satisfies(aProfile)) {
                locCredential.close();
                throw new AIxCredentialException("Stored credential profile '" + aProfile.getId() + "' is incomplete.");
            }
            return locCredential;
        } catch (IOException | IllegalArgumentException aException) {
            throw new AIxCredentialException("Cannot decode credential profile '" + aProfile.getId() + "'.", aException);
        } finally {
            locValues.values().forEach(locValue -> Arrays.fill(locValue, '\0'));
        }
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

    private static char[] decodeUtf8(byte[] aValue) {
        CharBuffer locBuffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(aValue));
        char[] locResult = new char[locBuffer.remaining()];
        locBuffer.get(locResult);
        if (locBuffer.hasArray()) {
            Arrays.fill(locBuffer.array(), '\0');
        }
        return locResult;
    }

    private static final class AIcWipingByteArrayOutputStream extends ByteArrayOutputStream {
        private void wipe() {
            Arrays.fill(buf, (byte) 0);
            reset();
        }
    }
}
