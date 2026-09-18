package eu.algites.tool.build.credentials.coreimpl;

import eu.algites.tool.build.credentials.coreintf.AIcCredential;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialProfile;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialStoreAvailability;
import eu.algites.tool.build.credentials.coreintf.AIiCredentialProvider;
import eu.algites.tool.build.credentials.coreintf.AIiCredentialStore;
import eu.algites.tool.build.credentials.coreintf.AIxCredentialException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Resolves credentials from the highest-priority available OS secure-store backend.
 */
public final class AIcCredentialStoreProvider implements AIiCredentialProvider {
    private final List<AIiCredentialStore> stores;

    public AIcCredentialStoreProvider() {
        this(loadStores());
    }

    public AIcCredentialStoreProvider(List<AIiCredentialStore> aStores) {
        stores = new ArrayList<>(Objects.requireNonNull(aStores, "Credential stores must not be null."));
        stores.sort(
            Comparator.comparingInt(AIiCredentialStore::getPriority)
                .reversed()
                .thenComparing(AIiCredentialStore::getId)
        );
    }

    public Optional<AIiCredentialStore> getAvailableStore() {
        return stores.stream().filter(locStore -> locStore.getAvailability().isAvailable()).findFirst();
    }

    public List<AIcCredentialStoreAvailability> getStoreAvailabilities() {
        return stores.stream().map(AIiCredentialStore::getAvailability).toList();
    }

    @Override
    public Optional<AIcCredential> resolve(AIcCredentialProfile aProfile) {
        Objects.requireNonNull(aProfile, "Credential profile must not be null.");
        Optional<AIiCredentialStore> locStore = getAvailableStore();
        if (locStore.isEmpty()) {
            return Optional.empty();
        }
        Optional<byte[]> locBlob = locStore.get().read(aProfile.getStorageKey());
        if (locBlob.isEmpty()) {
            return Optional.empty();
        }
        byte[] locValue = locBlob.get();
        try {
            return Optional.of(AIcCredentialCodec.decode(aProfile, locValue));
        } finally {
            java.util.Arrays.fill(locValue, (byte) 0);
        }
    }

    public AIiCredentialStore requireAvailableStore() {
        return getAvailableStore().orElseThrow(() -> new AIxCredentialException(buildUnavailableStoreMessage()));
    }

    public String buildUnavailableStoreMessage() {
        boolean locAnyAvailable = stores.stream().anyMatch(locStore -> locStore.getAvailability().isAvailable());
        StringBuilder locMessage = new StringBuilder(
            locAnyAvailable
                ? "Algites persistent credential-store diagnostics:"
                : "No supported Algites persistent credential store is available."
        );
        if (stores.isEmpty()) {
            locMessage.append(System.lineSeparator())
                .append(" - no AIiCredentialStore provider was discovered through ServiceLoader")
                .append(System.lineSeparator())
                .append("   Ensure the platform credential-store artifact is present on the runtime classpath.");
        }
        for (AIiCredentialStore locStore : stores) {
            AIcCredentialStoreAvailability locAvailability = locStore.getAvailability();
            locMessage.append(System.lineSeparator())
                .append(" - ").append(locStore.getId()).append(": ")
                .append(locAvailability.getStatus().name().toLowerCase().replace('_', '-'))
                .append(" - ").append(locAvailability.getMessage());
            for (String locRemediation : locAvailability.getRemediation()) {
                locMessage.append(System.lineSeparator()).append("   ").append(locRemediation);
            }
        }
        return locMessage.toString();
    }

    private static List<AIiCredentialStore> loadStores() {
        List<AIiCredentialStore> locStores = new ArrayList<>();
        ServiceLoader.load(AIiCredentialStore.class).forEach(locStores::add);
        return locStores;
    }
}
