package eu.algites.tool.build.policy.polarman;

/**
 * <p>
 * Title: {@link PolicyGenMain}
 * </p>
 * <p>
 * Description: Algites Policy Artifact Manager
 * </p>
 * <p>
 * Copyright: Copyright (c) 2026 Arttur Linhart, Algites
 * </p>
 * <p>
 * Company: Algites
 * </p>
 *
 * @author linhart1
 * @date 03.01.26 16:31
 */

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AIcPolicyArtifactManager {

	public static final String ALGITES_POLICY_ARTIFACT_GROUP_ID = "algites.policy.artifact.groupId";
	public static final String ALGITES_POLICY_ARTIFACT_ARTIFACT_ID_BASE = "algites.policy.artifact.artifactId";
	public static final String ALGITES_POLICY_ID = "algites.policy.id";
	public static final String ALGITES_POLICY_VERSION = "algites.policy.version";

	public static final String ALGITES_REPOSITORY_LANE = "algites.repository.lane";
	public static final String ALGITES_REPOSITORY_LANE_REVISION = "algites.repository.lane.revision";
	public static final String ALGITES_REPOSITORY_LANE_REVISION_SUFFIX = "algites.repository.lane.revision.suffix";

	public static final String SRC_PRODUCT_MAVEN_POM_XML_TPL = "src/product/maven/pom.xml.tpl";
	public static final String SRC_PRODUCT_GRADLE_PLUGIN_KT_TPL = "src/product/gradle/plugin.kt.tpl";

	public static void main(String[] args) throws Exception {
		System.out.println("Algites Policy Artifact Manager start, passed arguments: " + Arrays.toString(args));
		if (args.length == 0) {
			usage();
			System.exit(2);
		}
		String cmd = args[0];
		Map<String, String> opts = parseOpts(Arrays.copyOfRange(args, 1, args.length));

		Path root = Path.of(opts.getOrDefault("--root", ".")).normalize();
		Path policyFile = root.resolve(opts.getOrDefault("--policy-file", "algites-artifact.properties"));
		final String locRepositoryFileName = opts.getOrDefault("--repository-file", "algites-source-repository.properties");
		Path repositoryRoot = AIsPolicyArtifactManagerUtils.findRepositoryRoot(root, locRepositoryFileName);
		Path repositoryFile = repositoryRoot.resolve(locRepositoryFileName);
		Path outDir = root.resolve(opts.getOrDefault("--out", "run/bld/polarman"));

		switch (cmd) {
		case "generate" -> generate(root, policyFile, repositoryRoot, repositoryFile, outDir);
		case "validate" -> validate(root, policyFile, repositoryRoot, repositoryFile);
		case "scaffold" -> scaffold(opts, repositoryRoot, repositoryFile);
		default -> {
			System.err.println("Unknown command: " + cmd);
			usage();
			System.exit(2);
		}
		}
	}

	private static void usage() {
		System.out.println("""
            algites-policy-gen
              generate --root . [--policy-file algites-artifact.properties] [--repository-file algites-source-repository.properties] [--out run/gen]
              validate --root . [--policy-file algites-artifact.properties] [--repository-file algites-source-repository.properties]
              scaffold --dir <path_inside_repository> --AlgitesPolicyArtifactGroupId <gid> --AlgitesPolicyArtifactArtifactId <aid> --AlgitesPolicyId <id> --AlgitesPolicyVersion <ver> [--repository-file algites-source-repository.properties]
            """);
	}

	private static Map<String, String> parseOpts(String[] args) {
		Map<String, String> m = new LinkedHashMap<>();
		for (int i = 0; i < args.length; i++) {
			String a = args[i];
			if (a.startsWith("--")) {
				String v = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "true";
				m.put(a, v);
			}
		}
		return m;
	}

	private static void validate(Path aArtifactRoot, Path aPolicyFile, Path aRepositoryRoot, Path aRepositoryFile) throws IOException {
		AIcPolicyModel locPolicyModel = AIcPolicyModel.load(aPolicyFile);
		System.out.println("INFO: Loaded content of the policy file:\n" + locPolicyModel.values + "\n---");

		// Required template files for a policy artifact
		Path mavenTpl = aArtifactRoot.resolve(SRC_PRODUCT_MAVEN_POM_XML_TPL);
		Path gradleTpl = aArtifactRoot.resolve(SRC_PRODUCT_GRADLE_PLUGIN_KT_TPL);

		List<Path> required = List.of(aPolicyFile, mavenTpl, gradleTpl);
		List<Path> missing = required.stream().filter(p -> !Files.isRegularFile(p)).toList();
		if (!missing.isEmpty()) {
			throw new IllegalStateException("Missing required files:\n" + joinPaths(missing));
		}

		// Required properties
		List<String> requiredKeys = List.of(
				ALGITES_POLICY_ARTIFACT_GROUP_ID,
				ALGITES_POLICY_ARTIFACT_ARTIFACT_ID_BASE,
				ALGITES_POLICY_ID,
				ALGITES_POLICY_VERSION
		);
		List<String> missingKeys = requiredKeys.stream().filter(k -> !locPolicyModel.values.containsKey(k)).toList();
		if (!missingKeys.isEmpty()) {
			throw new IllegalStateException("Missing required properties in " + aPolicyFile + ":\n" +
					String.join("\n", missingKeys));
		}

		System.out.println("OK: policy artifact inputs validated.");

		AIcRepositoryModel locRepositoryModel = AIcRepositoryModel.load(aRepositoryFile);
		System.out.println("INFO: Loaded content of the repository file:\n" + locRepositoryModel.values + "\n---");
		requiredKeys = List.of(
				ALGITES_REPOSITORY_LANE,
				ALGITES_REPOSITORY_LANE_REVISION,
				ALGITES_REPOSITORY_LANE_REVISION_SUFFIX
		);
		missingKeys = requiredKeys.stream().filter(k -> !locRepositoryModel.values.containsKey(k)).toList();
		if (!missingKeys.isEmpty()) {
			throw new IllegalStateException("Missing required properties in " + aRepositoryFile + ":\n" +
					String.join("\n", missingKeys));
		}
		System.out.println("OK: repository inputs validated.");
	}

	private static void generate(Path aArtifactRoot, Path aPolicyFile, Path aRepositoryRoot, Path aRepositoryFile, Path outDir) throws IOException {
		validate(aArtifactRoot, aPolicyFile, aRepositoryRoot, aRepositoryFile);

		AIcRepositoryModel locRepositoryModel = AIcRepositoryModel.load(aRepositoryFile);

		AIcPolicyModel locArtifactPolicyModel = AIcPolicyModel.load(aPolicyFile);

		Path mavenTpl = aArtifactRoot.resolve(SRC_PRODUCT_MAVEN_POM_XML_TPL);
		Path gradleTpl = aArtifactRoot.resolve(SRC_PRODUCT_GRADLE_PLUGIN_KT_TPL);

		String pomTpl = Files.readString(mavenTpl, StandardCharsets.UTF_8);
		String pluginTpl = Files.readString(gradleTpl, StandardCharsets.UTF_8);

		String pom = AIcTemplateEngine.apply(pomTpl, locArtifactPolicyModel.values);
		pom = AIcTemplateEngine.apply(pom, locRepositoryModel.values);
		String plugin = AIcTemplateEngine.apply(pluginTpl, locArtifactPolicyModel.values);
		plugin = AIcTemplateEngine.apply(plugin, locRepositoryModel.values);

		// Enforce: no unresolved placeholders left
		AIcTemplateEngine.failIfUnresolved(pom, "maven/pom.xml");
		AIcTemplateEngine.failIfUnresolved(plugin, "gradle/plugin.kt");

		// Write outputs
		Path pomOut = outDir.resolve("maven/pom.xml");
		Path pluginOut = outDir.resolve("gradle/plugin.kt");

		writeFile(pomOut, pom);
		writeFile(pluginOut, plugin);

		System.out.println("Generated:");
		System.out.println("  " + pomOut);
		System.out.println("  " + pluginOut);
	}

	private static void scaffold(Map<String, String> opts, Path aRepositoryRoot, Path aRepositoryFile) throws IOException {
		Path dir = Path.of(requireOpt(opts, "--dir"));
		String locPolicyArtifactGroupId = requireOpt(opts, "--AlgitesPolicyArtifactGroupId");
		String locPolicyArtifactId = requireOpt(opts, "--AlgitesPolicyArtifactArtifactId");
		String locPolicyId = requireOpt(opts, "--AlgitesPolicyId");
		String locPolicyVersion = requireOpt(opts, "--AlgitesPolicyVersion");

		Files.createDirectories(dir);

		// Marker + data
		Path policyProps = dir.resolve("algites-artifact.properties");
		if (!Files.exists(policyProps)) {
			String content = """
                # Algites policy artifact marker + data
                algites.policy.artifact.groupId=%s
                algites.policy.artifact.artifactId=%s
                algites.policy.id=%s
                algites.policy.version=%s

                # Add versions here (examples)
                #version.junit=5.10.2
                #version.kotlin=1.9.25
                """.formatted(locPolicyArtifactGroupId, locPolicyArtifactId, locPolicyId, locPolicyVersion);
			writeFile(policyProps, content);
		}

		// Templates
		Path mavenTpl = dir.resolve("src/product/maven/pom.xml.tpl");
		Path gradleTpl = dir.resolve("src/product/gradle/plugin.kt.tpl");
		Files.createDirectories(mavenTpl.getParent());
		Files.createDirectories(gradleTpl.getParent());

		if (!Files.exists(mavenTpl)) {
			writeFile(mavenTpl, """
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <locArtifactGroupId>@algites.policy.artifact.groupId@</locArtifactGroupId>
                  <artifactId>@algites.policy.artifact.artifactId@-maven</artifactId>
                  <version>@algites.repository.lane@.@algites.repository.lane.revision@@algites.repository.lane.suffix@</version>
                  <packaging>pom</packaging>
                  <name>@algites.policy.artifact.artifactId@ Maven artifact</name>
                </project>
                """);
		}

		if (!Files.exists(gradleTpl)) {
			writeFile(gradleTpl, """
                package eu.algites.tool.build.base

                // Generated from policy template
                object AIoAlgitesBuildPolicy {
                    const val ID = "@algites.policy.id@"
                    const val VERSION = "@algites.policy.version@"
                }
                """);
		}

		System.out.println("Scaffolded policy artifact skeleton at: " + dir);
	}

	private static String requireOpt(Map<String, String> opts, String key) {
		String v = opts.get(key);
		if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing option: " + key);
		return v;
	}

	private static void writeFile(Path p, String content) throws IOException {
		Files.createDirectories(p.getParent());
		Files.writeString(p, content, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	private static String joinPaths(List<Path> ps) {
		StringBuilder sb = new StringBuilder();
		for (Path p : ps) sb.append(" - ").append(p).append("\n");
		return sb.toString();
	}

	// ---- Model ----

	static abstract class AIcModel {
		final Map<String, String> values;

		protected AIcModel(Map<String, String> values) {
			// Deterministic ordering for debugging / reproducibility
			this.values = Collections.unmodifiableMap(new TreeMap<>(values));
		}

		protected static Map<String, String> load(Path file, String aMissingFileErrorMessage) throws IOException {
			if (!Files.isRegularFile(file)) {
				throw new IllegalStateException(aMissingFileErrorMessage + ": " + file);
			}
			Properties props = new Properties();
			try (InputStream in = Files.newInputStream(file)) {
				props.load(in);
			}
			Map<String, String> map = new HashMap<>();
			for (Map.Entry<Object, Object> e : props.entrySet()) {
				map.put(String.valueOf(e.getKey()).trim(), String.valueOf(e.getValue()).trim());
			}
			return map;
		}
	}

	static final class AIcPolicyModel {
		final Map<String, String> values;

		private AIcPolicyModel(Map<String, String> values) {
			// Deterministic ordering for debugging / reproducibility
			this.values = Collections.unmodifiableMap(new TreeMap<>(values));
		}

		static AIcPolicyModel load(Path file) throws IOException {
			return new AIcPolicyModel(AIcModel.load(file, "Missing policy properties file: " + file));
		}
	}

	static final class AIcRepositoryModel {
		final Map<String, String> values;

		private AIcRepositoryModel(Map<String, String> values) {
			// Deterministic ordering for debugging / reproducibility
			this.values = Collections.unmodifiableMap(new TreeMap<>(values));
		}

		static AIcRepositoryModel load(Path file) throws IOException {
			return new AIcRepositoryModel(AIcModel.load(file, "Missing repository properties file: " + file));
		}
	}

	// ---- Template ----

	static final class AIcTemplateEngine {
		private static final Pattern PLACEHOLDER = Pattern.compile("@([A-Za-z0-9_.-]+)@");

		static String apply(String template, Map<String, String> values) {
			Matcher m = PLACEHOLDER.matcher(template);
			StringBuffer sb = new StringBuffer();
			while (m.find()) {
				String key = m.group(1);
				String val = values.get(key);
				if (val == null) {
					// keep placeholder for later unresolved check
					m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
				} else {
					m.appendReplacement(sb, Matcher.quoteReplacement(val));
				}
			}
			m.appendTail(sb);
			return sb.toString();
		}

		static void failIfUnresolved(String output, String logicalName) {
			Matcher m = PLACEHOLDER.matcher(output);
			List<String> unresolved = new ArrayList<>();
			while (m.find()) unresolved.add(m.group(1));
			if (!unresolved.isEmpty()) {
				throw new IllegalStateException(
						"Unresolved placeholders in " + logicalName + ":\n" +
								String.join("\n", new TreeSet<>(unresolved) + "\nOutput Produced:\n" + output + "\n---")
				);
			}
		}
	}
}
