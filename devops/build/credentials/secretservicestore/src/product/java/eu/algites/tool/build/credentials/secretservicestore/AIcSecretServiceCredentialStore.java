package eu.algites.tool.build.credentials.secretservicestore;

import de.swiesend.secretservice.functional.Collection;
import de.swiesend.secretservice.functional.SecretService;
import de.swiesend.secretservice.functional.interfaces.CollectionInterface;
import de.swiesend.secretservice.functional.interfaces.ServiceInterface;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialStoreAvailability;
import eu.algites.tool.build.credentials.coreintf.AIiCredentialStore;
import eu.algites.tool.build.credentials.coreintf.AInCredentialStoreAvailabilityStatus;
import eu.algites.tool.build.credentials.coreintf.AIxCredentialException;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.nio.CharBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Freedesktop Secret Service backend using the D-Bus Secret Service API directly.
 *
 * The backend is compatible with Secret Service providers such as GNOME Keyring,
 * modern KWallet/ksecretd and KeePassXC. No external secret-tool executable is required.
 */
public final class AIcSecretServiceCredentialStore implements AIiCredentialStore {
    private static final String COLLECTION_LABEL = "Algites Credentials";
    private static final String ATTRIBUTE_APPLICATION = "application";
    private static final String ATTRIBUTE_STORAGE_KEY = "storage-key";

    @Override
    public String getId() {
        return "freedesktop-secret-service";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public AIcCredentialStoreAvailability getAvailability() {
        String locOsName = System.getProperty("os.name", "").trim().toLowerCase(Locale.ROOT);
        if (!locOsName.equals("linux")) {
            return new AIcCredentialStoreAvailability(
                AInCredentialStoreAvailabilityStatus.UNSUPPORTED_PLATFORM,
                "Freedesktop Secret Service backend is enabled only on Linux.",
                List.of()
            );
        }
        if (System.getenv("DBUS_SESSION_BUS_ADDRESS") == null && System.getenv("XDG_RUNTIME_DIR") == null) {
            return new AIcCredentialStoreAvailability(
                AInCredentialStoreAvailabilityStatus.SERVICE_UNAVAILABLE,
                "No desktop session D-Bus environment was detected.",
                List.of(
                    "Run the build in a logged-in desktop/session D-Bus environment, or provide the credential through the environment variables reported for the credential profile."
                )
            );
        }
        try (ServiceInterface locService = SecretService.create().orElse(null)) {
            if (locService == null) {
                return providerUnavailable();
            }
            return AIcCredentialStoreAvailability.available(
                "Freedesktop Secret Service is available through provider '" + locService.getProvider() + "'."
            );
        } catch (Exception aException) {
            return new AIcCredentialStoreAvailability(
                AInCredentialStoreAvailabilityStatus.SERVICE_UNAVAILABLE,
                "Freedesktop Secret Service could not be opened: " + safeMessage(aException),
                providerRemediation()
            );
        }
    }

    @Override
    public Optional<byte[]> read(String aStorageKey) {
        try (CollectionInterface locCollection = requireCollection()) {
            List<String> locItems = locCollection.getItems(attributes(aStorageKey)).orElse(List.of());
            if (locItems.isEmpty()) {
                return Optional.empty();
            }
            String locItem = locItems.get(0);
            return locCollection.withSecret(locItem, locSecret -> {
                byte[] locEncoded = new byte[locSecret.length];
                for (int locIndex = 0; locIndex < locSecret.length; locIndex++) {
                    if (locSecret[locIndex] > 0x7f) {
                        throw new AIxCredentialException("Stored Algites Secret Service value is not valid Base64 ASCII data.");
                    }
                    locEncoded[locIndex] = (byte) locSecret[locIndex];
                }
                try {
                    return Base64.getDecoder().decode(locEncoded);
                } catch (IllegalArgumentException aException) {
                    throw new AIxCredentialException("Stored Algites Secret Service value is not valid Base64-encoded Algites secure-store data.", aException);
                } finally {
                    Arrays.fill(locEncoded, (byte) 0);
                }
            });
        } catch (AIxCredentialException aException) {
            throw aException;
        } catch (Exception aException) {
            throw new AIxCredentialException("Cannot read credential from Freedesktop Secret Service.", aException);
        }
    }

    @Override
    public void write(String aStorageKey, byte[] aValue) {
        byte[] locEncodedBytes = Base64.getEncoder().encode(aValue);
        char[] locEncodedChars = new char[locEncodedBytes.length];
        try {
            for (int locIndex = 0; locIndex < locEncodedBytes.length; locIndex++) {
                locEncodedChars[locIndex] = (char) (locEncodedBytes[locIndex] & 0xff);
            }
            try (CollectionInterface locCollection = requireCollection()) {
                List<String> locItems = locCollection.getItems(attributes(aStorageKey)).orElse(List.of());
                for (String locItem : locItems) {
                    locCollection.deleteItem(locItem);
                }
                locCollection.createItem(
                    "Algites credential " + aStorageKey,
                    CharBuffer.wrap(locEncodedChars),
                    attributes(aStorageKey)
                ).orElseThrow(() -> new AIxCredentialException("Secret Service refused to create the credential item."));
            }
        } catch (AIxCredentialException aException) {
            throw aException;
        } catch (Exception aException) {
            throw new AIxCredentialException("Cannot write credential to Freedesktop Secret Service.", aException);
        } finally {
            Arrays.fill(locEncodedBytes, (byte) 0);
            Arrays.fill(locEncodedChars, '\0');
        }
    }

    @Override
    public void delete(String aStorageKey) {
        try (CollectionInterface locCollection = requireCollection()) {
            for (String locItem : locCollection.getItems(attributes(aStorageKey)).orElse(List.of())) {
                locCollection.deleteItem(locItem);
            }
        } catch (Exception aException) {
            throw new AIxCredentialException("Cannot delete credential from Freedesktop Secret Service.", aException);
        }
    }

    private static CollectionInterface requireCollection() {
        AIcSecretServiceCredentialStore locStore = new AIcSecretServiceCredentialStore();
        AIcCredentialStoreAvailability locAvailability = locStore.getAvailability();
        if (!locAvailability.isAvailable()) {
            throw new AIxCredentialException(
                locAvailability.getMessage() +
                    (locAvailability.getRemediation().isEmpty()
                        ? ""
                        : System.lineSeparator() + String.join(System.lineSeparator(), locAvailability.getRemediation()))
            );
        }
        return Collection.open(COLLECTION_LABEL)
            .orElseThrow(() -> new AIxCredentialException("Freedesktop Secret Service collection '" + COLLECTION_LABEL + "' could not be opened."));
    }

    private static Map<String, String> attributes(String aStorageKey) {
        return Map.of(
            ATTRIBUTE_APPLICATION, "Algites",
            ATTRIBUTE_STORAGE_KEY, aStorageKey
        );
    }

    private static AIcCredentialStoreAvailability providerUnavailable() {
        return new AIcCredentialStoreAvailability(
            AInCredentialStoreAvailabilityStatus.SERVICE_UNAVAILABLE,
            "No org.freedesktop.secrets provider is available on the current Linux desktop session.",
            providerRemediation()
        );
    }

    private static List<String> providerRemediation() {
        return List.of(
            "Install or enable a Secret Service provider such as GNOME Keyring, KWallet/ksecretd, or KeePassXC Secret Service.",
            "Alternatively provide the credential through the environment variables reported for the credential profile."
        );
    }

    private static String safeMessage(Throwable aThrowable) {
        String locMessage = aThrowable.getMessage();
        return locMessage == null || locMessage.isBlank() ? aThrowable.getClass().getSimpleName() : locMessage;
    }
}
