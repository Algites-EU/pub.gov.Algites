package eu.algites.pltf.modustro.builder.build;

import eu.algites.pltf.modustro.builder.model.build.AInGradleBuildPhase;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/** Verifies strict repository-wide barriers in the Gradle phase controller. */
public final class AItcGradlePhaseControllerTest {
    /** Verifies that a failed VERIFY phase prevents PACKAGE and PUBLISH from starting. */
    @Test
    public void AItStopsAtFirstFailedPhase() throws Exception {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            throw new SkipException("The fake Gradle wrapper test requires a POSIX shell.");
        }

        Path locDirectory = Files.createTempDirectory("modustro-phase-controller-");
        Path locLog = locDirectory.resolve("phases.log");
        Path locWrapper = locDirectory.resolve("gradlew");
        Files.writeString(
                locWrapper,
                "#!/usr/bin/env bash\n" +
                        "set -euo pipefail\n" +
                        "locLast=\"${!#}\"\n" +
                        "echo \"${locLast}\" >> \"" + locLog.toAbsolutePath() + "\"\n" +
                        "if [[ \"${locLast}\" == \"modustroVerifyPhase\" ]]; then exit 37; fi\n",
                StandardCharsets.UTF_8);
        try {
            Set<PosixFilePermission> locPermissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(locWrapper, locPermissions);
        } catch (UnsupportedOperationException aException) {
            throw new SkipException("The file system does not support POSIX permissions.", aException);
        }

        AIcGradlePhaseController locController = new AIcGradlePhaseController(locWrapper, locDirectory);
        assertThrows(
                IllegalStateException.class,
                () -> locController.executeThrough(AInGradleBuildPhase.PUBLISH, List.of()));

        assertEquals(
                Files.readAllLines(locLog, StandardCharsets.UTF_8),
                List.of(
                        "modustroResolvePhase",
                        "modustroPreparePhase",
                        "modustroCompilePhase",
                        "modustroVerifyPhase"));
    }
}
