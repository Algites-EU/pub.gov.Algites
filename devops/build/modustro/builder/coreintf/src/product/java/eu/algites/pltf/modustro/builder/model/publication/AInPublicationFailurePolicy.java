package eu.algites.pltf.modustro.builder.model.publication;

/** Defines whether final failure of one publishing endpoint fails the build. */
public enum AInPublishingFailurePolicy {
    FAIL_BUILD_ON_PUBLISHING_FAILURE,
    IGNORE_PUBLISHING_FAILURE
}
