package eu.algites.pltf.modustro.builder.publication.adapters;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload;
import eu.algites.pltf.modustro.builder.publication.AIcPublicationAttemptContext;
import eu.algites.pltf.modustro.builder.publication.AIiPublicationAdapter;

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
public final class AIcGitBranchPublicationAdapter implements AIiPublicationAdapter {
    /** Canonical adapter id. */
    public static final String ADAPTER_ID = "git-branch";

    @Override
    public String adapterId() {
        return ADAPTER_ID;
    }

    @Override
    public boolean isRetrySafe(AIcPublicationPayload aPayload, AIcPublicationEndpoint aEndpoint) {
        return true;
    }

    private static final java.util.concurrent.ConcurrentHashMap<Path,Object> WORKING_TREE_LOCKS = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void publish(AIcPublicationAttemptContext aContext) throws Exception {
        Path tree=Path.of(AIcRequiredCoordinate(aContext.payload().coordinates(), "workingTree")).toRealPath();
        synchronized(WORKING_TREE_LOCKS.computeIfAbsent(tree, ignored -> new Object())) {
            AIcPublishLocked(aContext);
        }
    }

    private void AIcPublishLocked(AIcPublicationAttemptContext aContext) throws Exception {
        Map<String, String> locCoordinates = aContext.payload().coordinates();
        Path locWorkingTree = Path.of(AIcRequiredCoordinate(locCoordinates, "workingTree")).toAbsolutePath().normalize();
        if (!Files.isDirectory(locWorkingTree.resolve(".git"))) {
            throw new IllegalArgumentException("git-branch publishing requires a Git working tree at '" + locWorkingTree + "'.");
        }
        URI locPublishingUri = aContext.endpoint().publicationUri();
        if (locPublishingUri == null) {
            throw new IllegalArgumentException("git-branch publishing requires PublicationUri.");
        }
        String locBranch = locCoordinates.get("branch");
        if (locBranch == null || locBranch.isBlank()) {
            locBranch = locPublishingUri.getFragment();
        }
        if (locBranch == null || !locBranch.matches("[A-Za-z0-9._/-]+") || locBranch.startsWith("/") || locBranch.endsWith("/")) {
            throw new IllegalArgumentException("git-branch publishing requires a valid payload branch coordinate or PublicationUri fragment.");
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
        for (var file : aContext.payload().files()) {
            Path target=locWorkingTree.resolve(file.logicalName()).normalize();
            if(!target.startsWith(locWorkingTree)||target.startsWith(locWorkingTree.resolve(".git")))throw new IllegalArgumentException("Invalid Git payload path.");
            Files.createDirectories(target.getParent());
            if(!target.equals(file.path().toAbsolutePath().normalize()))Files.copy(file.path(),target,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
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
        /* The workflow configures origin with its scoped GitHub token. Never bypass that
         * authenticated remote by passing the public PublicationUri to git push. */
        String locOrigin = AIcReadOriginUrl(aContext, locWorkingTree);
        if (!AIcRemoteMatchesPublication(locOrigin, locRemoteUri)) {
            throw new IllegalStateException(
                    "Configured Git remote 'origin' does not refer to the declared publication repository. "
                    + "Check the documentation-branch workspace setup and PublicationUri."
            );
        }
        AIcRun(
                aContext,
                locWorkingTree,
                List.of("git", "push", "origin", "HEAD:refs/heads/" + locBranch),
                true);
        aContext.progressReporter().completed("Git branch publication completed.");
    }

    private static String AIcReadOriginUrl(AIcPublicationAttemptContext aContext, Path aWorkingTree)
            throws Exception {
        AIcCheckCancellation(aContext);
        Process locProcess = new ProcessBuilder("git", "remote", "get-url", "origin")
                .directory(aWorkingTree.toFile())
                .redirectErrorStream(true)
                .start();
        String locOutput = new String(locProcess.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        int locExitCode = locProcess.waitFor();
        if (locExitCode != 0 || locOutput.isBlank()) {
            throw new IllegalStateException("Git publication requires a configured remote 'origin'.");
        }
        /* Do not log locOutput: it may contain the workflow's authentication token. */
        return locOutput;
    }

    static boolean AIcRemoteMatchesPublication(String aOrigin, URI aPublicationUri) {
        try {
            URI locRemote = new URI(aOrigin);
            if (!"https".equalsIgnoreCase(locRemote.getScheme()) ||
                    !"https".equalsIgnoreCase(aPublicationUri.getScheme()) ||
                    locRemote.getHost() == null || aPublicationUri.getHost() == null ||
                    !locRemote.getHost().equalsIgnoreCase(aPublicationUri.getHost()) ||
                    locRemote.getPort() != aPublicationUri.getPort()) {
                return false;
            }
            return AIcCanonicalRepositoryPath(locRemote.getPath())
                    .equals(AIcCanonicalRepositoryPath(aPublicationUri.getPath()));
        } catch (java.net.URISyntaxException locException) {
            return false;
        }
    }

    private static String AIcCanonicalRepositoryPath(String aPath) {
        String locPath = aPath == null ? "" : aPath.replaceAll("/+$", "");
        if (locPath.endsWith(".git")) {
            locPath = locPath.substring(0, locPath.length() - 4);
        }
        return locPath;
    }

    private static int AIcRun(
            AIcPublicationAttemptContext aContext,
            Path aWorkingTree,
            List<String> aCommand,
            boolean aRequireSuccess) throws Exception {
        AIcCheckCancellation(aContext);
        List<String> locCommand = new ArrayList<>(aCommand);
        ProcessBuilder locBuilder = new ProcessBuilder(locCommand);
        locBuilder.directory(aWorkingTree.toFile());
        locBuilder.redirectErrorStream(true);
        Process locProcess = locBuilder.start();
        java.io.ByteArrayOutputStream locCapturedOutput = new java.io.ByteArrayOutputStream();
        Thread locOutputReader = new Thread(() -> {
            try {
                locProcess.getInputStream().transferTo(locCapturedOutput);
            } catch (java.io.IOException locException) {
                /* The process can close its stream when cancelled. */
            }
        }, "modustro-git-publication-output");
        locOutputReader.setDaemon(true);
        locOutputReader.start();
        while (true) {
            AIcCheckCancellation(aContext);
            Instant locDeadline = aContext.deadline();
            if (locDeadline != null && !Instant.now().isBefore(locDeadline)) {
                locProcess.destroy();
                if (!locProcess.waitFor(1, TimeUnit.SECONDS)) {
                    locProcess.destroyForcibly();
                }
                throw new java.util.concurrent.TimeoutException("Git publication attempt exceeded its deadline.");
            }
            if (locProcess.waitFor(200L, TimeUnit.MILLISECONDS)) {
                int locExit = locProcess.exitValue();
                locOutputReader.join(1000L);
                if (aRequireSuccess && locExit != 0) {
                    String locSafeOutput = AIcRedactGitOutput(
                            locCapturedOutput.toString(java.nio.charset.StandardCharsets.UTF_8));
                    throw new IllegalStateException("Git publishing command failed with exit code " + locExit
                            + ": " + String.join(" ", locCommand)
                            + (locSafeOutput.isBlank() ? "" : "\nGit output: " + locSafeOutput));
                }
                return locExit;
            }
        }
    }

    static String AIcRedactGitOutput(String aOutput) {
        /* Never include embedded HTTPS authentication material in CI error messages. */
        String locRedacted = aOutput.replaceAll("(?i)(https?://)[^/@\\s]+@", "$1[REDACTED]@").trim();
        return locRedacted.substring(0, Math.min(locRedacted.length(), 16000));
    }

    private static String AIcRequiredCoordinate(Map<String, String> aCoordinates, String aKey) {
        String locValue = aCoordinates.get(aKey);
        if (locValue == null || locValue.isBlank()) {
            throw new IllegalArgumentException("git-branch publishing requires payload coordinate '" + aKey + "'.");
        }
        return locValue;
    }

    private static void AIcCheckCancellation(AIcPublicationAttemptContext aContext) throws InterruptedException {
        if (aContext.cancellationToken().isCancellationRequested()) {
            throw new InterruptedException("Publication attempt cancelled.");
        }
    }
}
