package eu.algites.pltf.modustro.builder.publication;

import org.testng.annotations.Test;

/** Runs Version Scope snapshot cleanup against local HTTP provider fixtures. */
public final class AItcRemoveCorrespondingSnapshotsVersionScopePublicationFinalizationActionAdapterTest {
    @Test public void AIcChecksSnapshotCleanup() throws Exception { AIcRemoveCorrespondingSnapshotsChecks.run(); }
}
