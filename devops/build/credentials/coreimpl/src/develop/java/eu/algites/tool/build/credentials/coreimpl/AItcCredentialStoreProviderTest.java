package eu.algites.tool.build.credentials.coreimpl;

import eu.algites.tool.build.credentials.coreintf.AIcCredential;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialProfile;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialStoreAvailability;
import eu.algites.tool.build.credentials.coreintf.AIiCredentialStore;
import eu.algites.tool.build.credentials.coreintf.AInCredentialField;
import eu.algites.tool.build.credentials.coreintf.AInCredentialStoreAvailabilityStatus;
import eu.algites.tool.build.credentials.coreintf.AInCredentialType;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AItcCredentialStoreProviderTest {
    @Test
    public void testUsesHighestPriorityAvailableStoreAndTypedStorageKey() {
        AIcMemoryStore locLow = new AIcMemoryStore("low", 10, true);
        AIcMemoryStore locHigh = new AIcMemoryStore("high", 20, true);
        AIcCredentialProfile locProfile = new AIcCredentialProfile("profile", AInCredentialType.BEARER);
        try (AIcCredential locInput = new AIcCredential(Map.of(AInCredentialField.TOKEN, "secret".toCharArray()))) {
            byte[] locBlob = AIcCredentialCodec.encode(locProfile, locInput);
            try {
                locHigh.write(locProfile.getStorageKey(), locBlob);
            } finally {
                Arrays.fill(locBlob, (byte) 0);
            }
        }

        AIcCredentialStoreProvider locProvider = new AIcCredentialStoreProvider(List.of(locLow, locHigh));
        Optional<AIcCredential> locCredential = locProvider.resolve(locProfile);
        Assert.assertTrue(locCredential.isPresent());
        Assert.assertTrue(locHigh.values.containsKey("profile/bearer"));
        try (AIcCredential locValue = locCredential.orElseThrow()) {
            Assert.assertEquals(new String(locValue.getValue(AInCredentialField.TOKEN).orElseThrow()), "secret");
        }
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
