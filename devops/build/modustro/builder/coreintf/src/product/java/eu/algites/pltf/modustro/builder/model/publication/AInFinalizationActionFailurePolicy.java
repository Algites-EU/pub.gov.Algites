package eu.algites.pltf.modustro.builder.model.publication;

/** Defines whether final failure of one finalization action fails the required build path. */
public enum AInFinalizationActionFailurePolicy {
    FAIL_BUILD_ON_FAILURE,
    IGNORE_FAILURE
}
