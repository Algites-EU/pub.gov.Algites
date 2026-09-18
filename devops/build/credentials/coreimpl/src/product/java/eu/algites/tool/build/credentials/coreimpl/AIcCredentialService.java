package eu.algites.tool.build.credentials.coreimpl;

import eu.algites.tool.build.credentials.coreintf.AIcCredential;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialProfile;
import eu.algites.tool.build.credentials.coreintf.AIiCredentialStore;
import eu.algites.tool.build.credentials.coreintf.AIxCredentialException;

import java.util.Arrays;
import java.util.Objects;

/**
 * Persistent credential management facade used by the Algites CLI.
 */
public final class AIcCredentialService {
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
        AIiCredentialStore locStore = storeProvider.requireAvailableStore();
        return locStore.read(aProfile.getStorageKey()).map(locBlob -> {
            try {
                try (AIcCredential locCredential = AIcCredentialCodec.decode(aProfile, locBlob)) {
                    return locCredential.satisfies(aProfile);
                }
            } finally {
                Arrays.fill(locBlob, (byte) 0);
            }
        }).orElse(false);
    }

    public void store(AIcCredentialProfile aProfile, AIcCredential aCredential) {
        Objects.requireNonNull(aProfile, "Credential profile must not be null.");
        Objects.requireNonNull(aCredential, "Credential must not be null.");
        if (!aCredential.satisfies(aProfile)) {
            throw new AIxCredentialException(
                "Credential does not contain all fields required by profile '" + aProfile.getId() + "'."
            );
        }
        byte[] locBlob = AIcCredentialCodec.encode(aProfile, aCredential);
        try {
            storeProvider.requireAvailableStore().write(aProfile.getStorageKey(), locBlob);
        } finally {
            Arrays.fill(locBlob, (byte) 0);
        }
    }

    public void remove(AIcCredentialProfile aProfile) {
        Objects.requireNonNull(aProfile, "Credential profile must not be null.");
        storeProvider.requireAvailableStore().delete(aProfile.getStorageKey());
    }

    public String getStoreDiagnostics() {
        return storeProvider.buildUnavailableStoreMessage();
    }
}
