package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationDomainContribution;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;

/** Atomic immutable domain handoffs, scoped by the exact invocation identity rather than a last-result filename. */
public final class AIcPublicationDomainStore {
    private static final long MAXIMUM_BYTES = 32L * 1024L * 1024L;
    private AIcPublicationDomainStore() { }

    public static Path resultFile(Path aRepositoryRoot, String aInvocationId, String aDomainId, String aStage) {
        if (!Set.of("artifact-results", "scope-finalization").contains(aStage)) throw new IllegalArgumentException("Unknown publication handoff stage.");
        return aRepositoryRoot.resolve("build/run/publication-coordination").resolve(fingerprint(aInvocationId))
                .resolve(aStage).resolve(fingerprint(aDomainId) + ".json");
    }

    public static String fingerprint(String aValue) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(aValue.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException locFailure) { throw new IllegalStateException("SHA-256 is unavailable.", locFailure); }
    }

    /** Permits byte-identical repeated writes while refusing any change to an already committed domain result. */
    public static void write(Path aRepositoryRoot, String aStage, AIcPublicationDomainContribution aContribution) throws IOException {
        Path locFile = resultFile(aRepositoryRoot, aContribution.invocationId(), aContribution.domainId(), aStage);
        byte[] locBytes = (AIcBuildRecordPublicationFinalizationActionAdapter.json(AIcPublicationDomainBridge.encode(aContribution)) + "\n").getBytes(StandardCharsets.UTF_8);
        if (locBytes.length > MAXIMUM_BYTES) throw new IOException("Publication-domain handoff exceeds 32 MiB.");
        Files.createDirectories(locFile.getParent());
        Path locLock = locFile.resolveSibling(locFile.getFileName() + ".lock");
        try (FileChannel locChannel = FileChannel.open(locLock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                var locHeldLock = locChannel.lock()) {
            if (Files.exists(locFile)) {
                if (Files.size(locFile) > MAXIMUM_BYTES || !Arrays.equals(Files.readAllBytes(locFile), locBytes)) {
                    throw new IOException("Publication-domain result is immutable; start a new invocation: " + aContribution.domainId());
                }
                return;
            }
            Path locTemporary = Files.createTempFile(locFile.getParent(), "domain-", ".json.tmp");
            try {
                Files.write(locTemporary, locBytes);
                try { Files.move(locTemporary, locFile, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException locFailure) { Files.move(locTemporary, locFile); }
            } finally { Files.deleteIfExists(locTemporary); }
        }
    }

    /** The representation adapter supplies JSON parsing; invocation and domain are checked after reconstruction. */
    public static AIcPublicationDomainContribution read(Path aRepositoryRoot, String aInvocationId, String aDomainId,
            String aStage, Function<String, Map<String, Object>> aParser) throws IOException {
        Path locFile = resultFile(aRepositoryRoot, aInvocationId, aDomainId, aStage);
        if (Files.size(locFile) > MAXIMUM_BYTES) throw new IOException("Publication-domain handoff exceeds 32 MiB.");
        AIcPublicationDomainContribution locResult = AIcPublicationDomainBridge.decode(aParser.apply(Files.readString(locFile)));
        if (!aInvocationId.equals(locResult.invocationId()) || !aDomainId.equals(locResult.domainId())) {
            throw new IOException("Publication-domain handoff identity does not match its requested path.");
        }
        return locResult;
    }
}
