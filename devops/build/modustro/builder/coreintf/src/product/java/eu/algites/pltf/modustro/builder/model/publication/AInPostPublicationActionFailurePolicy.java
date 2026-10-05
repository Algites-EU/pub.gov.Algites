package eu.algites.pltf.modustro.builder.model.publication;

/** Defines whether final failure of one post-publication action fails the required build path. */
public enum AInPostPublicationActionFailurePolicy {
    FAIL_BUILD_ON_FAILURE,
    IGNORE_FAILURE
}
