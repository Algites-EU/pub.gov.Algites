package eu.algites.tool.build.credentials.coreintf;

import java.util.Optional;

/**
 * Persistent secure-store service provider interface.
 *
 * Each credential profile/type pair is stored as one opaque, versioned secret blob.
 */
public interface AIiCredentialStore {
    String getId();

    int getPriority();

    AIcCredentialStoreAvailability getAvailability();

    default boolean isAvailable() {
        return getAvailability().isAvailable();
    }

    Optional<byte[]> read(String aStorageKey);

    void write(String aStorageKey, byte[] aValue);

    void delete(String aStorageKey);
}
