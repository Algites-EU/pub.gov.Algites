import java.util.Properties

// ============================================================
// build/base - publisher repo (bootstrap)
// - generates sources + parent POM from templates
// - publishes ONLY generated artifacts (not root pom.xml)
// ============================================================

layout.buildDirectory.set(file("run/bld/gradle"))

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "eu.algites.tool.build"
version = loadAlgitesVersions()["repository.version"] ?: "0.0.0-LOCAL"

// -----------------------------
// Versions / filtering
// -----------------------------
fun loadAlgitesVersions(): Map<String, String> {
    val props = Properties()

    // Prefer the new name; fallback to legacy name for compatibility
    val candidates = listOf(
        file("algites-versions.properties"),
        file("version.properties")
    )

    val f = candidates.firstOrNull { it.exists() }
        ?: error("Missing versions file. Expected algites-versions.properties (preferred) or version.properties (legacy).")

    f.inputStream().use { props.load(it) }
    return props.entries.associate { it.key.toString() to it.value.toString() }
}

fun filterTemplate(text: String, versions: Map<String, String>): String {
    // Replace @key@ tokens
    var out = text.replace(Regex("@([A-Za-z0-9_.:-]+)@")) { m ->
        val key = m.groupValues[1]
        versions[key] ?: error("Missing token value for @$key@ in versions file.")
    }

    // Replace @version_<groupId>:<artifactId>@ tokens (mapped to key 'version_<groupId>:<artifactId>')
    out = out.replace(Regex("@version_([^@]+?)@")) { m ->
        val gav = m.groupValues[1]
        val key = "version_$gav"
        versions[key] ?: error("Missing version mapping for @$key@ (used as @version_$gav@).")
    }

    return out
}

// -----------------------------
// Template locations (expected in this repo)
// -----------------------------
val templatesDir = layout.projectDirectory.dir("templates")
val pluginTplFile = templatesDir.file("base-plugin.tpl").asFile
val pomTplFile = templatesDir.file("base-pom.tpl").asFile

// Generated outputs
val genDir = layout.buildDirectory.dir("gen").get().asFile
val genSrcDir = file("${genDir}/src/main/kotlin")
val genPomDir = file("${genDir}/maven-parent")
val genPomFile = file("${genPomDir}/pom.xml")

tasks.register("generateBasicPluginSources") {
    inputs.file(pluginTplFile)
    inputs.file(file("algites-versions.properties").takeIf { it.exists() } ?: file("version.properties"))
    outputs.dir(genSrcDir)

    doLast {
        val versions = loadAlgitesVersions()
        val filtered = filterTemplate(pluginTplFile.readText(Charsets.UTF_8), versions)

        val pkg = "eu.algites.tool.build.base"
        val outFile = file("${genSrcDir}/${pkg.replace('.', '/')}/AIcAlgitesBuildBasePublicPlugin.kt")
        outFile.parentFile.mkdirs()
        outFile.writeText(filtered, Charsets.UTF_8)
    }
}

tasks.register("generateBasicMavenParentPom") {
    inputs.file(pomTplFile)
    inputs.file(file("algites-versions.properties").takeIf { it.exists() } ?: file("version.properties"))
    outputs.file(genPomFile)

    doLast {
        val versions = loadAlgitesVersions()
        val filtered = filterTemplate(pomTplFile.readText(Charsets.UTF_8), versions)
        genPomDir.mkdirs()
        genPomFile.writeText(filtered, Charsets.UTF_8)
    }
}

// Wire generated sources into the plugin build
tasks.named("compileKotlin").configure {
    dependsOn("generateBasicPluginSources")
}

sourceSets {
    named("main") {
        kotlin.srcDir(genSrcDir)
    }
}

// -----------------------------
// Gradle plugin metadata
// -----------------------------
gradlePlugin {
    plugins {
        create("algitesBasic") {
            // NOTE: ID is what consuming repos apply
            id = "eu.algites.tool.build.base.pub"
            implementationClass = "eu.algites.tool.build.base.AIcAlgitesBuildBasePublicPlugin"
        }
    }
}

// -----------------------------
// Repositories for building THIS plugin repo (bootstrap)
// -----------------------------
repositories {
    mavenCentral()
    maven {
        name = "algites-public-snapshots"
        url = uri("https://dl.cloudsmith.io/public/algites/maven-snapshots-pub/maven/")
    }
}

// -----------------------------
// Publishing: publish ONLY the Gradle plugin module from this build.
// The Maven parent POM is generated separately (see task below).
// -----------------------------
publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            from(components["java"])
            artifactId = "pub.gov.Algites.devops_build.base-gradle"
        }
    }
}

// Optional helper task: deploy generated Maven parent POM (requires mvn on PATH).
// This avoids having to "publish a POM-only module" via Gradle internals.
tasks.register<Exec>("deployBasicMavenParentPom") {
    dependsOn("generateBasicMavenParentPom")

    // Expected environment variables (same pattern as CI):
    //   ALGITES_REPO_URL (target repository URL)
    //   ALGITES_REPO_USER / ALGITES_REPO_PASS (if required by repository)
    val repoUrl = System.getenv("ALGITES_REPO_URL")
        ?: error("Missing env ALGITES_REPO_URL for deployBasicMavenParentPom")
    val repoUser = System.getenv("ALGITES_REPO_USER") ?: ""
    val repoPass = System.getenv("ALGITES_REPO_PASS") ?: ""

    // Use settings.xml via env if you have it; otherwise rely on altDeploymentRepository + server id.
    // Here we use altDeploymentRepository with a fixed id.
    commandLine(
        "mvn", "-B", "-ntp", "-U",
        "-f", genPomFile.absolutePath,
        "deploy",
        "-DskipTests",
        "-DaltDeploymentRepository=algites-snapshots::default::${repoUrl}"
    )

    // Provide credentials to Maven via standard env for settings.xml interpolation if you use it
    environment("ALGITES_REPO_USER", repoUser)
    environment("ALGITES_REPO_PASS", repoPass)
}
