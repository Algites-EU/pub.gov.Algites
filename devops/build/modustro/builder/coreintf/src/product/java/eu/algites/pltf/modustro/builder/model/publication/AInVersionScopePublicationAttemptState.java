package eu.algites.pltf.modustro.builder.model.publication;

/** Persistent state of one Version Scope publication attempt. */
public enum AInVersionScopePublicationAttemptState {
    PUBLISHING,
    FINALIZING,
    COMPLETE,
    FAILED
}
