package eu.algites.pltf.modustro.builder.publication.adapters;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingPayload;
import eu.algites.pltf.modustro.builder.publication.AIcPublishingAttemptContext;
import eu.algites.pltf.modustro.builder.publication.AIiPublishingAdapter;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Publishes an already prepared Git working tree to one remote branch. */
public final class AIcGitBranchPublishingAdapter implements AIiPublishingAdapter {
    /** Canonical adapter id. */
    public static final String ADAPTER_ID = "git-branch";

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
        Map<String, String> locCoordinates = aContext.payload().coordinates();
        Path locWorkingTree = Path.of(AIcRequiredCoordinate(locCoordinates, "workingTree")).toAbsolutePath().normalize();
        if (!Files.isDirectory(locWorkingTree.resolve(".git"))) {
            throw new IllegalArgumentException("git-branch publishing requires a Git working tree at '" + locWorkingTree + "'.");
        }
        URI locPublishingUri = aContext.endpoint().publishingUrl();
        if (locPublishingUri == null) {
            throw new IllegalArgumentException("git-branch publishing requires PublishingUrl.");
        }
        String locBranch = locCoordinates.get("branch");
        if (locBranch == null || locBranch.isBlank()) {
            locBranch = locPublishingUri.getFragment();
        }
        if (locBranch == null || !locBranch.matches("[A-Za-z0-9._/-]+") || locBranch.startsWith("/") || locBranch.endsWith("/")) {
            throw new IllegalArgumentException("git-branch publishing requires a valid payload branch coordinate or PublishingUrl fragment.");
        }
        URI locRemoteUri = new URI(
                locPublishingUri.getScheme(),
                locPublishingUri.getUserInfo(),
                locPublishingUri.getHost(),
                locPublishingUri.getPort(),
                locPublishingUri.getPath(),
                locPublishingUri.getQuery(),
                null);
        String locCommitMessage = locCoordinates.getOrDefault(
                "commitMessage",
                "Publish " + aContext.payload().artifactIdentity() + " " + aContext.payload().version());

        aContext.progressReporter().started("Publishing Git branch '" + locBranch + "' to " + locRemoteUri);
        AIcRun(aContext, locWorkingTree, List.of("git", "add", "-A"), true);
        int locDiffExit = AIcRun(aContext, locWorkingTree, List.of("git", "diff", "--cached", "--quiet"), false);
        if (locDiffExit == 1) {
            AIcRun(
                    aContext,
                    locWorkingTree,
                    List.of(
                            "git",
                            "-c", "user.name=Modustro Builder",
                            "-c", "user.email=modustro-builder@users.noreply.github.com",
                            "commit", "-m", locCommitMessage),
                    true);
        } else if (locDiffExit != 0) {
            throw new IllegalStateException("git diff --cached --quiet failed with exit code " + locDiffExit + ".");
        }
        AIcRun(
                aContext,
                locWorkingTree,
                List.of("git", "push", locRemoteUri.toString(), "HEAD:refs/heads/" + locBranch),
                true);
        aContext.progressReporter().completed("Git branch publication completed.");
    }

    private static int AIcRun(
            AIcPublishingAttemptContext aContext,
            Path aWorkingTree,
            List<String> aCommand,
            boolean aRequireSuccess) throws Exception {
        AIcCheckCancellation(aContext);
        List<String> locCommand = new ArrayList<>(aCommand);
        ProcessBuilder locBuilder = new ProcessBuilder(locCommand);
        locBuilder.directory(aWorkingTree.toFile());
        locBuilder.inheritIO();
        Process locProcess = locBuilder.start();
        while (true) {
            AIcCheckCancellation(aContext);
            Instant locDeadline = aContext.deadline();
            if (locDeadline != null && !Instant.now().isBefore(locDeadline)) {
                locProcess.destroy();
                if (!locProcess.waitFor(1, TimeUnit.SECONDS)) {
                    locProcess.destroyForcibly();
                }
                throw new java.util.concurrent.TimeoutException("Git publishing attempt exceeded its deadline.");
            }
            if (locProcess.waitFor(200L, TimeUnit.MILLISECONDS)) {
                int locExit = locProcess.exitValue();
                if (aRequireSuccess && locExit != 0) {
                    throw new IllegalStateException("Git publishing command failed with exit code " + locExit + ": " + String.join(" ", locCommand));
                }
                return locExit;
            }
        }
    }

    private static String AIcRequiredCoordinate(Map<String, String> aCoordinates, String aKey) {
        String locValue = aCoordinates.get(aKey);
        if (locValue == null || locValue.isBlank()) {
            throw new IllegalArgumentException("git-branch publishing requires payload coordinate '" + aKey + "'.");
        }
        return locValue;
    }

    private static void AIcCheckCancellation(AIcPublishingAttemptContext aContext) throws InterruptedException {
        if (aContext.cancellationToken().isCancellationRequested()) {
            throw new InterruptedException("Publishing attempt cancelled.");
        }
    }
}
