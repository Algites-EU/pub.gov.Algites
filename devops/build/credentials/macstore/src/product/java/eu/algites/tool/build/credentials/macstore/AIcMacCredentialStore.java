package eu.algites.tool.build.credentials.macstore;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialStoreAvailability;
import eu.algites.tool.build.credentials.coreintf.AIiCredentialStore;
import eu.algites.tool.build.credentials.coreintf.AInCredentialStoreAvailabilityStatus;
import eu.algites.tool.build.credentials.coreintf.AIxCredentialException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * macOS Keychain backend using the Security framework generic-password API.
 */
public final class AIcMacCredentialStore implements AIiCredentialStore {
    private static final int ERR_SEC_SUCCESS = 0;
    private static final int ERR_SEC_ITEM_NOT_FOUND = -25300;
    private static final String SERVICE = "eu.algites.credentials";

    @Override
    public String getId() {
        return "macos-keychain";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public AIcCredentialStoreAvailability getAvailability() {
        String locOsName = System.getProperty("os.name", "").trim().toLowerCase(Locale.ROOT);
        if (!(locOsName.startsWith("mac") || locOsName.equals("darwin"))) {
            return new AIcCredentialStoreAvailability(
                AInCredentialStoreAvailabilityStatus.UNSUPPORTED_PLATFORM,
                "macOS Keychain is available only on macOS.",
                java.util.List.of()
            );
        }
        return AIcCredentialStoreAvailability.available("macOS Keychain is available.");
    }

    @Override
    public Optional<byte[]> read(String aStorageKey) {
        requireAvailable();
        byte[] locService = SERVICE.getBytes(StandardCharsets.UTF_8);
        byte[] locAccount = aStorageKey.getBytes(StandardCharsets.UTF_8);
        IntByReference locLength = new IntByReference();
        PointerByReference locData = new PointerByReference();

        int locStatus = AIiSecurity.INSTANCE.SecKeychainFindGenericPassword(
            Pointer.NULL, locService.length, locService, locAccount.length, locAccount, locLength, locData, null
        );
        if (locStatus == ERR_SEC_ITEM_NOT_FOUND) {
            return Optional.empty();
        }
        requireSuccess("SecKeychainFindGenericPassword", locStatus);

        Pointer locPointer = locData.getValue();
        try {
            return Optional.of(locPointer.getByteArray(0, locLength.getValue()));
        } finally {
            AIiSecurity.INSTANCE.SecKeychainItemFreeContent(Pointer.NULL, locPointer);
        }
    }

    @Override
    public void write(String aStorageKey, byte[] aValue) {
        requireAvailable();
        byte[] locService = SERVICE.getBytes(StandardCharsets.UTF_8);
        byte[] locAccount = aStorageKey.getBytes(StandardCharsets.UTF_8);
        PointerByReference locItem = new PointerByReference();
        IntByReference locExistingLength = new IntByReference();
        PointerByReference locExistingData = new PointerByReference();

        int locFindStatus = AIiSecurity.INSTANCE.SecKeychainFindGenericPassword(
            Pointer.NULL,
            locService.length,
            locService,
            locAccount.length,
            locAccount,
            locExistingLength,
            locExistingData,
            locItem
        );

        if (locFindStatus == ERR_SEC_SUCCESS) {
            Pointer locExistingPointer = locExistingData.getValue();
            if (locExistingPointer != null) {
                AIiSecurity.INSTANCE.SecKeychainItemFreeContent(Pointer.NULL, locExistingPointer);
            }
            try {
                Memory locSecretMemory = new Memory(Math.max(1, aValue.length));
                try {
                    if (aValue.length > 0) {
                        locSecretMemory.write(0, aValue, 0, aValue.length);
                    }
                    requireSuccess(
                        "SecKeychainItemModifyAttributesAndData",
                        AIiSecurity.INSTANCE.SecKeychainItemModifyAttributesAndData(
                            locItem.getValue(), Pointer.NULL, aValue.length, locSecretMemory
                        )
                    );
                } finally {
                    locSecretMemory.clear();
                }
            } finally {
                AIiCoreFoundation.INSTANCE.CFRelease(locItem.getValue());
            }
            return;
        }

        if (locFindStatus != ERR_SEC_ITEM_NOT_FOUND) {
            requireSuccess("SecKeychainFindGenericPassword", locFindStatus);
        }

        Memory locSecretMemory = new Memory(Math.max(1, aValue.length));
        try {
            if (aValue.length > 0) {
                locSecretMemory.write(0, aValue, 0, aValue.length);
            }
            requireSuccess(
                "SecKeychainAddGenericPassword",
                AIiSecurity.INSTANCE.SecKeychainAddGenericPassword(
                    Pointer.NULL,
                    locService.length,
                    locService,
                    locAccount.length,
                    locAccount,
                    aValue.length,
                    locSecretMemory,
                    null
                )
            );
        } finally {
            locSecretMemory.clear();
        }
    }

    @Override
    public void delete(String aStorageKey) {
        requireAvailable();
        byte[] locService = SERVICE.getBytes(StandardCharsets.UTF_8);
        byte[] locAccount = aStorageKey.getBytes(StandardCharsets.UTF_8);
        PointerByReference locItem = new PointerByReference();
        int locStatus = AIiSecurity.INSTANCE.SecKeychainFindGenericPassword(
            Pointer.NULL, locService.length, locService, locAccount.length, locAccount, null, null, locItem
        );
        if (locStatus == ERR_SEC_ITEM_NOT_FOUND) {
            return;
        }
        requireSuccess("SecKeychainFindGenericPassword", locStatus);
        try {
            requireSuccess("SecKeychainItemDelete", AIiSecurity.INSTANCE.SecKeychainItemDelete(locItem.getValue()));
        } finally {
            AIiCoreFoundation.INSTANCE.CFRelease(locItem.getValue());
        }
    }

    private void requireAvailable() {
        AIcCredentialStoreAvailability locAvailability = getAvailability();
        if (!locAvailability.isAvailable()) {
            throw new AIxCredentialException(locAvailability.getMessage());
        }
    }

    private static void requireSuccess(String aOperation, int aStatus) {
        if (aStatus != ERR_SEC_SUCCESS) {
            throw new AIxCredentialException(aOperation + " failed with OSStatus " + aStatus + ".");
        }
    }

    private interface AIiSecurity extends Library {
        AIiSecurity INSTANCE = Native.load("/System/Library/Frameworks/Security.framework/Security", AIiSecurity.class);

        int SecKeychainFindGenericPassword(
            Pointer aKeychainOrArray,
            int aServiceNameLength,
            byte[] aServiceName,
            int aAccountNameLength,
            byte[] aAccountName,
            IntByReference aPasswordLength,
            PointerByReference aPasswordData,
            PointerByReference aItemRef
        );

        int SecKeychainAddGenericPassword(
            Pointer aKeychain,
            int aServiceNameLength,
            byte[] aServiceName,
            int aAccountNameLength,
            byte[] aAccountName,
            int aPasswordLength,
            Pointer aPasswordData,
            PointerByReference aItemRef
        );

        int SecKeychainItemModifyAttributesAndData(Pointer aItemRef, Pointer aAttributes, int aLength, Pointer aData);

        int SecKeychainItemDelete(Pointer aItemRef);

        int SecKeychainItemFreeContent(Pointer aAttributes, Pointer aData);
    }

    private interface AIiCoreFoundation extends Library {
        AIiCoreFoundation INSTANCE = Native.load(
            "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation",
            AIiCoreFoundation.class
        );

        void CFRelease(Pointer aObject);
    }
}
