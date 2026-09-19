package eu.algites.tool.build.credentials.coreimpl;

import eu.algites.tool.build.credentials.coreintf.AIcCredentialStoreAvailability;
import eu.algites.tool.build.credentials.coreintf.AIiCredentialStore;
import eu.algites.tool.build.credentials.coreintf.AInCredentialStoreAvailabilityStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AItcCredentialStoreProviderTest {
    @Test
    public void testUsesHighestPriorityAvailableStoreForUniversalDocument() {
        AIcMemoryStore locLow = new AIcMemoryStore("low", 10, true);
        AIcMemoryStore locHigh = new AIcMemoryStore("high", 20, true);
        AIcCredentialStoreProvider locProvider = new AIcCredentialStoreProvider(List.of(locLow, locHigh));

        byte[] locInput = "{\"profile\":{}}".getBytes(StandardCharsets.UTF_8);
        try {
            locProvider.writeCredentialDocument(locInput);
        } finally {
            Arrays.fill(locInput, (byte) 0);
        }

        Assert.assertFalse(locLow.values.containsKey(AIcCredentialStoreProvider.CREDENTIAL_DOCUMENT_STORAGE_KEY));
        Assert.assertTrue(locHigh.values.containsKey(AIcCredentialStoreProvider.CREDENTIAL_DOCUMENT_STORAGE_KEY));
        Optional<byte[]> locStored = locProvider.readCredentialDocument();
        Assert.assertTrue(locStored.isPresent());
        byte[] locValue = locStored.orElseThrow();
        try {
            Assert.assertEquals(new String(locValue, StandardCharsets.UTF_8), "{\"profile\":{}}");
        } finally {
            Arrays.fill(locValue, (byte) 0);
        }
    }

    @Test
    public void testNamedSecretUsesSeparateNamespace() {
        AIcMemoryStore locStore = new AIcMemoryStore("store", 10, true);
        AIcCredentialStoreProvider locProvider = new AIcCredentialStoreProvider(List.of(locStore));
        byte[] locInput = "secret".getBytes(StandardCharsets.UTF_8);
        try {
            locProvider.writeNamedSecret("PASSWORD_SECRET", locInput);
        } finally {
            Arrays.fill(locInput, (byte) 0);
        }

        Assert.assertTrue(locStore.values.containsKey("secret/PASSWORD_SECRET"));
        Assert.assertFalse(locStore.values.containsKey(AIcCredentialStoreProvider.CREDENTIAL_DOCUMENT_STORAGE_KEY));
    }

    private static final class AIcMemoryStore implements AIiCredentialStore {
        private final String id;
        private final int priority;
        private final boolean available;
        private final Map<String, byte[]> values = new HashMap<>();

        private AIcMemoryStore(String aId, int aPriority, boolean aAvailable) {
            id = aId;
            priority = aPriority;
            available = aAvailable;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public AIcCredentialStoreAvailability getAvailability() {
            return available
                ? AIcCredentialStoreAvailability.available("available")
                : new AIcCredentialStoreAvailability(
                    AInCredentialStoreAvailabilityStatus.SERVICE_UNAVAILABLE,
                    "unavailable",
                    List.of("test remediation")
                );
        }

        @Override
        public Optional<byte[]> read(String aStorageKey) {
            byte[] locValue = values.get(aStorageKey);
            return locValue == null ? Optional.empty() : Optional.of(Arrays.copyOf(locValue, locValue.length));
        }

        @Override
        public void write(String aStorageKey, byte[] aValue) {
            values.put(aStorageKey, Arrays.copyOf(aValue, aValue.length));
        }

        @Override
        public void delete(String aStorageKey) {
            byte[] locRemoved = values.remove(aStorageKey);
            if (locRemoved != null) {
                Arrays.fill(locRemoved, (byte) 0);
            }
        }
    }
}
