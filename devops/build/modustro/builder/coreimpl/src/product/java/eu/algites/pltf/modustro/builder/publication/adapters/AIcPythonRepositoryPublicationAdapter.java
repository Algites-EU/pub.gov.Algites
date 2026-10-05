package eu.algites.pltf.modustro.builder.publication.adapters;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayloadFile;
import eu.algites.pltf.modustro.builder.publication.AIcPublicationAttemptContext;
import eu.algites.pltf.modustro.builder.publication.AIiPublicationAdapter;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Publishes Python distributions by executing exactly one Twine upload attempt. */
public final class AIcPythonRepositoryPublicationAdapter implements AIiPublicationAdapter {
    /** Canonical adapter id. */
    public static final String ADAPTER_ID = "python-repository";

    @Override
    public String adapterId() {
        return ADAPTER_ID;
    }

    @Override
    public boolean isRetrySafe(AIcPublicationPayload aPayload, AIcPublicationEndpoint aEndpoint) {
        return false;
    }

    @Override
    public void publish(AIcPublicationAttemptContext aContext) throws Exception {
        String locPython = aContext.payload().coordinates().getOrDefault("pythonExecutable", "python3");
        List<String> locCommand = new ArrayList<>();
        locCommand.add(locPython);
        locCommand.add("-m");
        locCommand.add("twine");
        locCommand.add("upload");
        locCommand.add("--repository-url");
        locCommand.add(aContext.endpoint().publicationUri().toString());
        AIcApplyCredentials(locCommand, aContext.credentials());
        for (AIcPublicationPayloadFile locFile : aContext.payload().files()) {
            locCommand.add(locFile.path().toAbsolutePath().toString());
        }
        if (aContext.payload().files().isEmpty()) {
            throw new IllegalArgumentException("python-repository publishing requires at least one distribution file.");
        }

        aContext.progressReporter().started("Uploading Python distribution with Twine.");
        Process locProcess = new ProcessBuilder(locCommand)
                .directory(AIcWorkingDirectory(aContext.payload()))
                .inheritIO()
                .start();
        try {
            while (locProcess.isAlive()) {
                if (aContext.cancellationToken().isCancellationRequested()) {
                    locProcess.destroy();
                    if (!locProcess.waitFor(2L, TimeUnit.SECONDS)) {
                        locProcess.destroyForcibly();
                    }
                    throw new InterruptedException("Python publication attempt cancelled.");
                }
                Instant locDeadline = aContext.deadline();
                if (locDeadline != null && !Instant.now().isBefore(locDeadline)) {
                    locProcess.destroy();
                    if (!locProcess.waitFor(2L, TimeUnit.SECONDS)) {
                        locProcess.destroyForcibly();
                    }
                    throw new java.util.concurrent.TimeoutException("Python publication attempt exceeded its deadline.");
                }
                aContext.progressReporter().indeterminate("Twine upload is running.");
                locProcess.waitFor(250L, TimeUnit.MILLISECONDS);
            }
            int locExit = locProcess.exitValue();
            if (locExit != 0) {
                throw new IllegalStateException("Twine upload failed with exit code " + locExit + ".");
            }
            aContext.progressReporter().completed("Python repository publication completed.");
        } finally {
            if (locProcess.isAlive()) {
                locProcess.destroyForcibly();
            }
        }
    }

    private static void AIcApplyCredentials(List<String> aCommand, Map<String, String> aCredentials) {
        String locUsername = aCredentials.get("username");
        String locPassword = aCredentials.get("password");
        if (locUsername != null && locPassword != null) {
            aCommand.add("--username");
            aCommand.add(locUsername);
            aCommand.add("--password");
            aCommand.add(locPassword);
        }
    }

    private static File AIcWorkingDirectory(AIcPublicationPayload aPayload) {
        return aPayload.files().get(0).path().toAbsolutePath().getParent().toFile();
    }
}
