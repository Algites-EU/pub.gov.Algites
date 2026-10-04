package eu.algites.pltf.modustro.builder.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Regression for handwritten/generated modules sharing a Python namespace package. */
public final class AItcPythonLayeredPackagingTest {
    /** Exercises the actual staging script without requiring a third-party Python build backend. */
    @Test
    public void AIcMergesPythonPackageLayersAndRejectsConflicts() throws Exception {
        Path repository = Path.of("").toAbsolutePath();
        Path scriptFile;
        while (!Files.isRegularFile(scriptFile = repository.resolve("gradle/tool/repository/modustro-root-build.gradle.kts"))) {
            repository = repository.getParent();
            if (repository == null) throw new IllegalStateException("Cannot locate governance packaging script");
        }
        var matcher = Pattern.compile("val locPythonBuildAndManifestScript = \"\"\"(.*?)\"\"\"\\.trimIndent\\(\\)", Pattern.DOTALL)
                .matcher(Files.readString(scriptFile));
        Assert.assertTrue(matcher.find());
        String raw = matcher.group(1);
        int indent = raw.lines().filter(line -> !line.isBlank()).mapToInt(line -> line.length() - line.stripLeading().length()).min().orElseThrow();
        String script = raw.lines().map(line -> line.isBlank() ? "" : line.substring(indent)).collect(java.util.stream.Collectors.joining("\n"));
        script = script.substring(0, script.indexOf("    subprocess.run(build_command, cwd=build_project, check=True)"));
        String assertions = """
                    assert (merged_root / 'example/manual.py').read_text() == 'value = 1'
                    assert (merged_root / 'example/generated.py').read_text() == 'value = 2'
                    assert (merged_root / 'example/extra.py').read_text() == 'value = 3'
                    assert (merged_root / 'example/data.json').read_text() == '{}'
                    assert tomllib.loads(metadata_path.read_text())['tool']['setuptools']['packages']['find']['where'] == ['modustro-python-package']
                """;
        script += assertions.lines().map(line -> "    " + line.stripLeading()).collect(java.util.stream.Collectors.joining("\n")) + "\n";
        Path fixture = Files.createTempDirectory("modustro-python-layers-");
        for (String layer : new String[]{"manual", "generated", "external"}) Files.createDirectories(fixture.resolve(layer + "/example"));
        Files.writeString(fixture.resolve("manual/example/manual.py"), "value = 1");
        Files.writeString(fixture.resolve("generated/example/generated.py"), "value = 2");
        Files.writeString(fixture.resolve("external/example/extra.py"), "value = 3");
        Files.writeString(fixture.resolve("external/example/data.json"), "{}");
        Files.writeString(fixture.resolve("pyproject.toml"), """
                [tool.setuptools.packages.find]
                where = ["manual", "generated", "external"]
                namespaces = true
                """);
        Path manifest = fixture.resolve("manifest.yml");
        Files.writeString(manifest, "ManifestVersion: 1\n");
        var process = new ProcessBuilder("python3", "-c", script, fixture.toString(), fixture.resolve("dist").toString(), manifest.toString(), "wheel")
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        Assert.assertEquals(process.waitFor(), 0, output);
        Files.writeString(fixture.resolve("generated/example/manual.py"), "value = 99");
        process = new ProcessBuilder("python3", "-c", script, fixture.toString(), fixture.resolve("dist").toString(), manifest.toString(), "wheel")
                .redirectErrorStream(true).start();
        output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        Assert.assertNotEquals(process.waitFor(), 0);
        Assert.assertTrue(output.contains("Conflicting Python source module/resource"), output);
        Assert.assertFalse(Files.exists(fixture.resolve("modustro-python-package")));
    }
}
