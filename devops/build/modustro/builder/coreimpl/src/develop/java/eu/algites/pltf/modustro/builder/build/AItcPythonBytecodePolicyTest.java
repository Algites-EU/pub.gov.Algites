package eu.algites.pltf.modustro.builder.build;

import org.testng.Assert;
import org.testng.annotations.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Tests the Python bytecode policy in an actual subprocess environment. */
public final class AItcPythonBytecodePolicyTest {
    @Test
    public void testGradlePythonExecTasksEnforceBytecodePolicy() throws Exception {
        Path locDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (locDirectory != null && !Files.isRegularFile(locDirectory.resolve("modustro-source-repository.yml"))) {
            locDirectory = locDirectory.getParent();
        }
        Assert.assertNotNull(locDirectory, "Modustro source repository root must be available");
        String locGradleCode = Files.readString(locDirectory.resolve("gradle/tool/repository/modustro-root-build.gradle.kts"));
        Assert.assertTrue(locGradleCode.contains("environment(\"PYTHONDONTWRITEBYTECODE\", \"1\")"),
                "Python Exec build tasks must enforce the bytecode policy directly");
        Assert.assertTrue(locGradleCode.contains("environment()[\"PYTHONDONTWRITEBYTECODE\"] = \"1\""),
                "Python ProcessBuilder tasks must enforce the bytecode policy directly");
    }

    @Test
    public void testPythonSubprocessDoesNotWriteBytecode() throws Exception {
        Path locDirectory = Files.createTempDirectory("modustro-python-bytecode-");
        try {
            Path locModule = locDirectory.resolve("modustro_bytecode_sample.py");
            Files.writeString(locModule, "answer = 42\n");
            String locPython = System.getenv().getOrDefault("ALGITES_PYTHON_EXECUTABLE", "python3");
            ProcessBuilder locBuilder = new ProcessBuilder(locPython, "-c", "import modustro_bytecode_sample; assert modustro_bytecode_sample.answer == 42");
            locBuilder.directory(locDirectory.toFile());
            Map<String, String> locEnvironment = locBuilder.environment();
            locEnvironment.put("PYTHONDONTWRITEBYTECODE", "1");
            Process locProcess = locBuilder.start();
            Assert.assertEquals(locProcess.waitFor(), 0, "Python import must succeed");
            Assert.assertFalse(Files.exists(locDirectory.resolve("__pycache__")), "Bytecode cache must not be written");
        } finally {
            try (var locFiles = Files.walk(locDirectory)) {
                locFiles.sorted(java.util.Comparator.reverseOrder()).forEach(aPath -> {
                    try { Files.deleteIfExists(aPath); }
                    catch (java.io.IOException aException) { throw new java.io.UncheckedIOException(aException); }
                });
            }
        }
    }
}
