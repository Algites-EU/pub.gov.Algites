package eu.algites.pltf.modustro.builder.publication.adapters;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingPayload;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingPayloadFile;
import eu.algites.pltf.modustro.builder.publication.AIcPublishingAttemptContext;
import eu.algites.pltf.modustro.builder.publication.AIiPublishingAdapter;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Publishes payload files to a local or mounted filesystem directory. */
public final class AIcLocalCopyPublishingAdapter implements AIiPublishingAdapter {
    /** Canonical adapter id. */
    public static final String ADAPTER_ID = "local-copy";

    @Override
    public String adapterId() {
        return ADAPTER_ID;
    }

    @Override
    public boolean isRetrySafe(AIcPublishingPayload aPayload, AIcPublishingEndpoint aEndpoint) {
        return true;
    }

    @Override
    public void publish(AIcPublishingAttemptContext aContext) throws Exception {
        Path locDestinationRoot = AIcDestinationPath(aContext.endpoint().publishingUrl());
        Files.createDirectories(locDestinationRoot);
        long locTotal = AIcTotalSize(aContext.payload());
        long locCompleted = 0L;
        aContext.progressReporter().started("Copying publishing payload to " + locDestinationRoot);
        for (AIcPublishingPayloadFile locPayloadFile : aContext.payload().files()) {
            AIcCheckCancellation(aContext);
            Path locSource = locPayloadFile.path();
            Path locTarget = locDestinationRoot.resolve(locPayloadFile.logicalName()).normalize();
            if (!locTarget.startsWith(locDestinationRoot.normalize())) {
                throw new IllegalArgumentException("Publishing logical name escapes destination root: " + locPayloadFile.logicalName());
            }
            Files.createDirectories(locTarget.getParent());
            Path locTemporary = locTarget.resolveSibling(locTarget.getFileName() + ".modustro-publishing-tmp");
            try (InputStream locInput = Files.newInputStream(locSource);
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
                                "Copying " + locPayloadFile.logicalName());
                    } else {
                        aContext.progressReporter().indeterminate("Copying " + locPayloadFile.logicalName());
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
        aContext.progressReporter().completed("Publishing payload copied to " + locDestinationRoot);
    }

    private static Path AIcDestinationPath(URI aUri) {
        if (aUri == null) {
            throw new IllegalArgumentException("local-copy publishing requires PublishingUrl.");
        }
        if (aUri.getScheme() == null) {
            return Path.of(aUri.toString()).toAbsolutePath().normalize();
        }
        if (!"file".equalsIgnoreCase(aUri.getScheme())) {
            throw new IllegalArgumentException("local-copy publishing supports only file: PublishingUrl values.");
        }
        return Path.of(aUri).toAbsolutePath().normalize();
    }

    private static long AIcTotalSize(AIcPublishingPayload aPayload) throws Exception {
        long locTotal = 0L;
        for (AIcPublishingPayloadFile locFile : aPayload.files()) {
            locTotal = Math.addExact(locTotal, Files.size(locFile.path()));
        }
        return locTotal;
    }

    private static void AIcCheckCancellation(AIcPublishingAttemptContext aContext) throws InterruptedException {
        if (aContext.cancellationToken().isCancellationRequested()) {
            throw new InterruptedException("Publishing attempt cancelled.");
        }
    }
}
