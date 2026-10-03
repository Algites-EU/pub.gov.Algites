package eu.algites.pltf.modustro.builder.model.build;

/** Defines how nested Gradle settings files affect Modustro build-domain discovery. */
public enum AInNestedGradleSettingsBuildPolicy {
    IGNORE_NESTED_SETTINGS,
    USE_ISOLATED_BUILD_ON_NESTED_SETTINGS
}
