package eu.algites.pltf.modustro.builder.model.publication;

/** Defines whether final failure of one publication endpoint fails the build. */
public enum AInPublicationFailurePolicy {
    FAIL_BUILD_ON_PUBLICATION_FAILURE,
    IGNORE_PUBLICATION_FAILURE
}
