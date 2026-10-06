package eu.algites.pltf.modustro.builder.publication;

import org.testng.annotations.Test;

/** Runs a Maven snapshot and its build-record publication against a local HTTP fixture. */
public final class AItcMavenBuildRecordTest {
    @Test public void AIcPublishesPairedSnapshotAndBuildRecord() throws Exception { AIcMavenBuildRecordChecks.run(); }
}
