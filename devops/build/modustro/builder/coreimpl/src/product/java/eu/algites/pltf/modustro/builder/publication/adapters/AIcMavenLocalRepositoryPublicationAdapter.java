package eu.algites.pltf.modustro.builder.publication.adapters;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayloadFile;
import eu.algites.pltf.modustro.builder.publication.AIcPublicationAttemptContext;
import eu.algites.pltf.modustro.builder.publication.AIiPublicationAdapter;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/** Publishes Maven-native files into an explicitly configured local Maven repository root. */
public final class AIcMavenLocalRepositoryPublicationAdapter implements AIiPublicationAdapter {
    /** Canonical adapter id. */
    public static final String ADAPTER_ID = "maven-local-repository";

    @Override
    public String adapterId() {
        return ADAPTER_ID;
    }

    @Override
    public boolean isRetrySafe(AIcPublicationPayload aPayload, AIcPublicationEndpoint aEndpoint) {
        return true;
    }

    @Override
    public void publish(AIcPublicationAttemptContext aContext) throws Exception {
        Map<String, String> locCoordinates = aContext.payload().coordinates();
        String locGroupId = AIcRequiredCoordinate(locCoordinates, "groupId");
        String locArtifactId = AIcRequiredCoordinate(locCoordinates, "artifactId");
        String locVersion = AIcRequiredCoordinate(locCoordinates, "version");
        Path locRepositoryRoot = AIcRepositoryRoot(aContext.endpoint().publicationUri());
        Path locArtifactDirectory = locRepositoryRoot
                .resolve(locGroupId.replace('.', '/'))
                .resolve(locArtifactId)
                .resolve(locVersion)
                .normalize();
        if (!locArtifactDirectory.startsWith(locRepositoryRoot.normalize())) {
            throw new IllegalArgumentException("Resolved Maven publication directory escapes repository root.");
        }
        Files.createDirectories(locArtifactDirectory);

        long locTotal = 0L;
        for (AIcPublicationPayloadFile locFile : aContext.payload().files()) {
            locTotal = Math.addExact(locTotal, Files.size(locFile.path()));
        }
        long locCompleted = 0L;
        aContext.progressReporter().started(
                "Publishing " + locGroupId + ":" + locArtifactId + ":" + locVersion + " to " + locRepositoryRoot);
        for (AIcPublicationPayloadFile locPayloadFile : aContext.payload().files()) {
            AIcCheckCancellation(aContext);
            String locFileName = Path.of(locPayloadFile.logicalName()).getFileName().toString();
            Path locTarget = locArtifactDirectory.resolve(locFileName);
            Path locTemporary = locTarget.resolveSibling(locTarget.getFileName() + ".modustro-publishing-tmp");
            try (InputStream locInput = Files.newInputStream(locPayloadFile.path());
                 OutputStream locOutput = Files.newOutputStream(
                         locTemporary,
                         StandardOpenOption.CREATE,
                         StandardOpenOption.TRUNCATE_EXISTING,
                         StandardOpenOption.WRITE)) {
                byte[] locBuffer = new byte[1024 * 1024];
                while (true) {
                    AIcCheckCancellation(aContext);
                    int locRead = locInput.read(locBuffer);
                    if (locRead < 0) {
                        break;
                    }
                    locOutput.write(locBuffer, 0, locRead);
                    locCompleted += locRead;
                    if (locTotal > 0L) {
                        aContext.progressReporter().progress(
                                locCompleted,
                                locTotal,
                                "bytes",
                                "Publishing " + locFileName);
                    }
                }
            } catch (Throwable locFailure) {
                Files.deleteIfExists(locTemporary);
                throw locFailure;
            }
            try {
                Files.move(locTemporary, locTarget, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException locIgnored) {
                Files.move(locTemporary, locTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        aContext.progressReporter().completed("Maven local publication completed.");
    }

    private static String AIcRequiredCoordinate(Map<String, String> aCoordinates, String aKey) {
        String locValue = aCoordinates.get(aKey);
        if (locValue == null || locValue.isBlank()) {
            throw new IllegalArgumentException("maven-local-repository publishing requires payload coordinate '" + aKey + "'.");
        }
        return locValue;
    }

    private static Path AIcRepositoryRoot(URI aUri) {
        if (aUri == null) {
            throw new IllegalArgumentException("maven-local-repository publishing requires PublicationUri.");
        }
        if (aUri.getScheme() == null) {
            return Path.of(aUri.toString()).toAbsolutePath().normalize();
        }
        if (!"file".equalsIgnoreCase(aUri.getScheme())) {
            throw new IllegalArgumentException("maven-local-repository publishing supports only file: PublicationUri values.");
        }
        return Path.of(aUri).toAbsolutePath().normalize();
    }

    private static void AIcCheckCancellation(AIcPublicationAttemptContext aContext) throws InterruptedException {
        if (aContext.cancellationToken().isCancellationRequested()) {
            throw new InterruptedException("Publication attempt cancelled.");
        }
    }
}
