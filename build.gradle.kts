val locAlgitesRootBuildScript = file("gradle/tool/repository/modustro-root-build.gradle.kts")
if (locAlgitesRootBuildScript.isFile) {
    apply(from = locAlgitesRootBuildScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/${System.getenv("MODUSTRO_PUBLIC_GOVERNANCE_REVISION") ?: "main"}/gradle/tool/repository/modustro-root-build.gradle.kts"))
}

apply(from = file("gradle/tool/validation/algites-governance-conventions.gradle.kts"))
