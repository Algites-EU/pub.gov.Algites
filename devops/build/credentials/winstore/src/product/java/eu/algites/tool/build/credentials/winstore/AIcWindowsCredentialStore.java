package eu.algites.tool.build.credentials.winstore;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.win32.W32APITypeMapper;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialStoreAvailability;
import eu.algites.tool.build.credentials.coreintf.AIiCredentialStore;
import eu.algites.tool.build.credentials.coreintf.AInCredentialStoreAvailabilityStatus;
import eu.algites.tool.build.credentials.coreintf.AIxCredentialException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Windows Credential Manager backend using generic credentials through Advapi32.
 */
public final class AIcWindowsCredentialStore implements AIiCredentialStore {
    private static final int CRED_TYPE_GENERIC = 1;
    private static final int CRED_PERSIST_LOCAL_MACHINE = 2;
    private static final int ERROR_NOT_FOUND = 1168;

    @Override
    public String getId() {
        return "windows-credential-manager";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public AIcCredentialStoreAvailability getAvailability() {
        String locOsName = System.getProperty("os.name", "").trim().toLowerCase(Locale.ROOT);
        if (!locOsName.startsWith("windows")) {
            return new AIcCredentialStoreAvailability(
                AInCredentialStoreAvailabilityStatus.UNSUPPORTED_PLATFORM,
                "Windows Credential Manager is available only on Windows.",
                List.of()
            );
        }
        return AIcCredentialStoreAvailability.available("Windows Credential Manager is available.");
    }

    @Override
    public Optional<byte[]> read(String aStorageKey) {
        requireAvailable();
        PointerByReference locCredentialReference = new PointerByReference();
        boolean locSuccess = AIiAdvapi32.INSTANCE.CredRead(target(aStorageKey), CRED_TYPE_GENERIC, 0, locCredentialReference);
        if (!locSuccess) {
            int locError = Native.getLastError();
            if (locError == ERROR_NOT_FOUND) {
                return Optional.empty();
            }
            throw new AIxCredentialException("Windows CredRead failed with error " + locError + ".");
        }

        Pointer locPointer = locCredentialReference.getValue();
        try {
            AIcNativeCredential locCredential = new AIcNativeCredential(locPointer);
            return Optional.of(locCredential.CredentialBlob.getByteArray(0, locCredential.CredentialBlobSize));
        } finally {
            AIiAdvapi32.INSTANCE.CredFree(locPointer);
        }
    }

    @Override
    public void write(String aStorageKey, byte[] aValue) {
        requireAvailable();
        Memory locMemory = new Memory(Math.max(1, aValue.length));
        try {
            if (aValue.length > 0) {
                locMemory.write(0, aValue, 0, aValue.length);
            }
            AIcNativeCredential locCredential = new AIcNativeCredential();
            locCredential.Flags = 0;
            locCredential.Type = CRED_TYPE_GENERIC;
            locCredential.TargetName = target(aStorageKey);
            locCredential.Comment = "Algites credential profile";
            locCredential.CredentialBlobSize = aValue.length;
            locCredential.CredentialBlob = locMemory;
            locCredential.Persist = CRED_PERSIST_LOCAL_MACHINE;
            locCredential.AttributeCount = 0;
            locCredential.Attributes = Pointer.NULL;
            locCredential.TargetAlias = null;
            locCredential.UserName = aStorageKey;
            locCredential.write();

            if (!AIiAdvapi32.INSTANCE.CredWrite(locCredential, 0)) {
                throw new AIxCredentialException("Windows CredWrite failed with error " + Native.getLastError() + ".");
            }
        } finally {
            locMemory.clear();
        }
    }

    @Override
    public void delete(String aStorageKey) {
        requireAvailable();
        if (!AIiAdvapi32.INSTANCE.CredDelete(target(aStorageKey), CRED_TYPE_GENERIC, 0)) {
            int locError = Native.getLastError();
            if (locError != ERROR_NOT_FOUND) {
                throw new AIxCredentialException("Windows CredDelete failed with error " + locError + ".");
            }
        }
    }

    private static String target(String aStorageKey) {
        return "Algites/credential/" + aStorageKey;
    }

    private void requireAvailable() {
        AIcCredentialStoreAvailability locAvailability = getAvailability();
        if (!locAvailability.isAvailable()) {
            throw new AIxCredentialException(locAvailability.getMessage());
        }
    }

    private interface AIiAdvapi32 extends StdCallLibrary {
        AIiAdvapi32 INSTANCE = Native.load("Advapi32", AIiAdvapi32.class, W32APIOptions.UNICODE_OPTIONS);

        boolean CredRead(String aTargetName, int aType, int aFlags, PointerByReference aCredential);

        boolean CredWrite(AIcNativeCredential aCredential, int aFlags);

        boolean CredDelete(String aTargetName, int aType, int aFlags);

        void CredFree(Pointer aCredential);
    }

    public static final class AIcFileTime extends Structure {
        public int dwLowDateTime;
        public int dwHighDateTime;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("dwLowDateTime", "dwHighDateTime");
        }
    }

    public static final class AIcNativeCredential extends Structure {
        public int Flags;
        public int Type;
        public String TargetName;
        public String Comment;
        public AIcFileTime LastWritten;
        public int CredentialBlobSize;
        public Pointer CredentialBlob;
        public int Persist;
        public int AttributeCount;
        public Pointer Attributes;
        public String TargetAlias;
        public String UserName;

        public AIcNativeCredential() {
            super(ALIGN_DEFAULT, W32APITypeMapper.UNICODE);
            LastWritten = new AIcFileTime();
        }

        public AIcNativeCredential(Pointer aPointer) {
            super(aPointer, ALIGN_DEFAULT, W32APITypeMapper.UNICODE);
            read();
        }

        @Override
        protected List<String> getFieldOrder() {
            return List.of(
                "Flags", "Type", "TargetName", "Comment", "LastWritten", "CredentialBlobSize",
                "CredentialBlob", "Persist", "AttributeCount", "Attributes", "TargetAlias", "UserName"
            );
        }
    }
}
