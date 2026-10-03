package eu.algites.pltf.modustro.builder.build;

import eu.algites.pltf.modustro.builder.model.build.AInGradleBuildPhase;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes repository-wide Modustro phases as separate Gradle invocations with strict barriers. */
public final class AIcGradlePhaseController {
    private final Path gradleWrapper;
    private final Path buildRoot;

    /** Creates a phase controller for one Gradle build root. */
    public AIcGradlePhaseController(Path aGradleWrapper, Path aBuildRoot) {
        gradleWrapper = Objects.requireNonNull(aGradleWrapper, "gradleWrapper").toAbsolutePath().normalize();
        buildRoot = Objects.requireNonNull(aBuildRoot, "buildRoot").toAbsolutePath().normalize();
    }

    /** Executes every phase from RESOLVE through the requested terminal phase. */
    public void executeThrough(AInGradleBuildPhase aTerminalPhase, List<String> aCommonGradleArguments)
            throws IOException, InterruptedException {
        Objects.requireNonNull(aTerminalPhase, "terminalPhase");
        List<String> locCommonArguments = List.copyOf(
                aCommonGradleArguments == null ? List.of() : aCommonGradleArguments);
        for (AInGradleBuildPhase locPhase : AInGradleBuildPhase.values()) {
            AIcExecutePhase(locPhase, locCommonArguments);
            if (locPhase == aTerminalPhase) {
                return;
            }
        }
        throw new IllegalStateException("Terminal phase was not reached: " + aTerminalPhase);
    }

    private void AIcExecutePhase(AInGradleBuildPhase aPhase, List<String> aCommonGradleArguments)
            throws IOException, InterruptedException {
        List<String> locCommand = new ArrayList<>();
        locCommand.add(gradleWrapper.toString());
        locCommand.add("--no-daemon");
        locCommand.add("--project-dir");
        locCommand.add(buildRoot.toString());
        locCommand.addAll(aCommonGradleArguments);
        locCommand.add(aPhase.gradleTaskName());
        Process locProcess = new ProcessBuilder(locCommand)
                .directory(buildRoot.toFile())
                .inheritIO()
                .start();
        int locExit = locProcess.waitFor();
        if (locExit != 0) {
            throw new IllegalStateException(
                    "Modustro phase " + aPhase + " failed with Gradle exit code " + locExit + ".");
        }
    }
}
