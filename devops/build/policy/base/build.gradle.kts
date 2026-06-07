plugins {
    `maven-publish`
    `kotlin-dsl`
    `java-gradle-plugin`
}


java {
    sourceSets {
        val main by getting {
            java.setSrcDirs(listOf("src/product/kotlin"))
            resources.setSrcDirs(listOf("src/product/resources"))
        }
        val test by getting {
            java.setSrcDirs(listOf("src/develop/kotlin"))
            resources.setSrcDirs(listOf("src/develop/resources"))
        }
    }
}

group = "eu.algites.gov.devops"

val PLUGIN_ID = "eu.algites.tool.build.policy.base"

gradlePlugin {
    plugins {
        create("buildPolicyBase") {
            id = PLUGIN_ID
            implementationClass =
                "eu.algites.tool.build.policy.base.AIcAlgitesPolicyBasePlugin"
        }
    }
}


publishing {

    publications {

        create<MavenPublication>("policyParentPom") {

            groupId = project.group.toString()
            artifactId = "pub.gov.Algites_build.policy.base-pom"
            version = project.version.toString()

            pom {
                packaging = "pom"
                name.set("Algites DevOps Build Policy Base – Parent POM")
                description.set(
                    "Generated policy parent POM for Algites DevOps build governance (base level)."
                )
            }
        }
    }

}
