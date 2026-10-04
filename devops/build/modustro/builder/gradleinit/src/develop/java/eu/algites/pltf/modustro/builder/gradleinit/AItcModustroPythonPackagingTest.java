package eu.algites.pltf.modustro.builder.gradleinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Tests preservation of native/generated namespace package sources in Python distributions. */
public final class AItcModustroPythonPackagingTest {
    /** Verifies that a legitimate package directory named build survives workspace staging. */
    @Test
    public void AIcPreservesBuildNamespaceAndMergedSources() throws IOException, InterruptedException {
        Path locRepository = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (locRepository != null && !Files.isRegularFile(locRepository.resolve("modustro-source-repository.yml"))) {
            locRepository = locRepository.getParent();
        }
        if (locRepository == null) throw new IOException("Cannot locate governance repository sources.");
        String locRootBuild = Files.readString(locRepository.resolve("gradle/tool/repository/modustro-root-build.gradle.kts"));
        String locStart = "val locPythonBuildAndManifestScript = \"\"\"\n";
        int locBegin = locRootBuild.indexOf(locStart) + locStart.length();
        int locEnd = locRootBuild.indexOf("\n        \"\"\".trimIndent()", locBegin);
        String locScript = locRootBuild.substring(locBegin, locEnd).replaceAll("(?m)^            ", "");
        Path locFixture = Files.createTempDirectory("modustro-python-namespace-");
        Path locProject = Files.createDirectories(locFixture.resolve("project"));
        Path locNative = Files.createDirectories(locProject.resolve("src/product/python/eu/algites/tool/build"));
        Path locGenerated = Files.createDirectories(locProject.resolve("src/product/python.gen/eu/algites/tool/build"));
        Files.writeString(locNative.resolve("native_model.py"), "VALUE = 'native'\n");
        Files.writeString(locGenerated.resolve("generated_model.py"), "VALUE = 'generated'\n");
        Files.writeString(locProject.resolve("pyproject.toml"), """
            [build-system]
            requires = ["setuptools>=77", "wheel"]
            build-backend = "setuptools.build_meta"
            [project]
            name = "modustro-namespace-fixture"
            version = "1.0.dev0"
            [tool.setuptools.packages.find]
            where = ["src/product/python", "src/product/python.gen"]
            namespaces = true
            """);
        Path locManifest = locFixture.resolve("manifest.yml");
        Files.writeString(locManifest, "artifact: namespace-fixture\n");
        Path locScriptFile = locFixture.resolve("build.py");
        Files.writeString(locScriptFile, locScript);
        Path locDist = locFixture.resolve("dist");
        String locPython = System.getenv("ALGITES_PYTHON_EXECUTABLE");
        if (locPython == null || locPython.isBlank()) locPython = "python3";
        Path locLog = locFixture.resolve("build.log");
        int locExit = new ProcessBuilder(locPython, locScriptFile.toString(), locProject.toString(), locDist.toString(),
            locManifest.toString(), "wheel,sdist").redirectErrorStream(true).redirectOutput(locLog.toFile()).start().waitFor();
        Assert.assertEquals(locExit, 0, Files.readString(locLog));
        try (var locFiles = Files.list(locDist)) {
            Path locWheel = locFiles.filter(aFile -> aFile.toString().endsWith(".whl")).findFirst().orElseThrow();
            try (ZipFile locZip = new ZipFile(locWheel.toFile())) {
                Assert.assertNotNull(locZip.getEntry("eu/algites/tool/build/native_model.py"));
                Assert.assertNotNull(locZip.getEntry("eu/algites/tool/build/generated_model.py"));
            }
        }
        try (var locFiles = Files.list(locDist)) {
            Assert.assertEquals(locFiles.filter(aFile -> aFile.toString().endsWith(".tar.gz")).count(), 1L);
        }
    }
}
