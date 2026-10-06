package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.catalog.AIcAdapterCatalog;
import eu.algites.pltf.modustro.builder.model.publication.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Cross-domain protocol regressions, independent of Gradle and external provider services. */
public final class AIcPublicationDomainBridgeChecks {
    private AIcPublicationDomainBridgeChecks() { }
    public static void main(String[] aArguments) throws Exception {
        run();
        if (aArguments.length == 1) Files.writeString(Path.of(aArguments[0]),
                AIcBuildRecordPublicationFinalizationActionAdapter.json(AIcPublicationDomainBridge.encode(AIcDomain("run", ".", "alpha", true))));
        System.out.println("PUBLICATION_DOMAIN_BRIDGE_CHECKS_OK");
    }
    public static void run() throws Exception {
        var locAlpha = AIcDomain("run", ".", "alpha", true);
        var locBeta = AIcDomain("run", "isolated", "isolated/beta", true);
        var locEncoded = AIcPublicationDomainBridge.encode(locAlpha);
        var locDecoded = AIcPublicationDomainBridge.decode(locEncoded);
        AIcPublicationCheckAssertions.assertEquals(AIcPublicationDomainBridge.encode(locDecoded), locEncoded,
                "The protocol must preserve complete endpoint/payload/action trees.");
        var locPublication = locDecoded.versionScopes().get(0).artifacts().get(0).outputs().get(0).publications().get(0);
        AIcPublicationCheckAssertions.assertEquals(locPublication.payload().files().get(0).contentUri(), URI.create("https://input.example.test/alpha.jar"));
        AIcPublicationCheckAssertions.assertEquals(locPublication.publicationFinalizationActions().get(0).finalizationActions().get(0).result().metadata().get("value"), List.of("nested"));
        AIcRejects(() -> locPublication.configuration().put("mutate", true), UnsupportedOperationException.class);
        AIcRejects(() -> ((List<?>)locPublication.configuration().get("nested")).clear(), UnsupportedOperationException.class);
        Map<String, Set<String>> locExpected = Map.of(".", Set.of("alpha"), "isolated", Set.of("isolated/beta"));
        var locCombined = AIcPublicationDomainBridge.aggregate("run", ".", "1.0", AInPublicationStability.RELEASE, locExpected, List.of(locBeta, locDecoded));
        AIcPublicationCheckAssertions.assertTrue(locCombined.readyForFinalization());
        AIcPublicationCheckAssertions.assertEquals(locCombined.execution().artifacts().stream().map(AIcArtifactPublicationExecutionResult::artifactPath).toList(), List.of("alpha", "isolated/beta"));
        var locMissingDomain = AIcPublicationDomainBridge.aggregate("run", ".", "1.0", AInPublicationStability.RELEASE, locExpected, List.of(locDecoded));
        AIcPublicationCheckAssertions.assertFalse(locMissingDomain.readyForFinalization());
        AIcPublicationCheckAssertions.assertEquals(locMissingDomain.missingResults(), List.of("domain:isolated"));
        var locMissingArtifact = AIcPublicationDomainBridge.aggregate("run", ".", "1.0", AInPublicationStability.RELEASE,
                Map.of(".", Set.of("alpha", "unpublished")), List.of(locDecoded));
        AIcPublicationCheckAssertions.assertFalse(locMissingArtifact.readyForFinalization());
        var locIncomplete = AIcPublicationDomainBridge.aggregate("run", ".", "1.0", AInPublicationStability.RELEASE,
                locExpected, List.of(locAlpha, AIcDomain("run", "isolated", "isolated/beta", false)));
        AIcPublicationCheckAssertions.assertFalse(locIncomplete.readyForFinalization());
        var locWrongVersion = AIcPublicationDomainBridge.aggregate("run", ".", "2.0", AInPublicationStability.RELEASE, locExpected, List.of(locAlpha, locBeta));
        AIcPublicationCheckAssertions.assertFalse(locWrongVersion.readyForFinalization());
        AIcRejects(() -> AIcPublicationDomainBridge.aggregate("next-run", ".", "1.0", AInPublicationStability.RELEASE, locExpected, List.of(locAlpha, locBeta)), IllegalArgumentException.class);
        AIcRejects(() -> AIcPublicationDomainBridge.aggregate("run", ".", "1.0", AInPublicationStability.RELEASE, locExpected, List.of(locAlpha, locAlpha)), IllegalArgumentException.class);
        AIcRejects(() -> AIcPublicationDomainBridge.aggregate("run", ".", "1.0", AInPublicationStability.RELEASE, Map.of(".", Set.of("alpha")), List.of(locBeta)), IllegalArgumentException.class);
        var locBadVersion = new LinkedHashMap<>(locEncoded);
        locBadVersion.put("FormatVersion", 999);
        AIcRejects(() -> AIcPublicationDomainBridge.decode(locBadVersion), IllegalArgumentException.class);
        var locBadObject = new AIcPublicationDomainContribution("run", ".", List.of(), Map.of("object", new Object()));
        AIcRejects(() -> AIcPublicationDomainBridge.encode(locBadObject), IllegalArgumentException.class);
        AtomicInteger locFinalized = new AtomicInteger();
        AIiVersionScopePublicationFinalizationActionAdapter locAdapter = new AIiVersionScopePublicationFinalizationActionAdapter() {
            @Override public String adapterId() { return "scope-probe"; }
            @Override public AIcPublicationFinalizationActionResult execute(AIcVersionScopePublicationFinalizationActionAttemptContext aContext) {
                AIcPublicationCheckAssertions.assertEquals(aContext.versionScopeExecution().artifacts().size(), 2);
                AIcPublicationCheckAssertions.assertEquals(aContext.versionScopeExecution().artifacts().get(1).outputs().get(0).snapshotPublicationEndpoints().get("snapshot").publicationCredentialProfile(), "snapshot-profile");
                locFinalized.incrementAndGet();
                return new AIcPublicationFinalizationActionResult("probe", true, true, false, 1, Duration.ZERO, null, Map.of(), null);
            }
        };
        try (var locScheduler = new AIcPublicationScheduler(new AIcAdapterCatalog(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(locAdapter)))) {
            var locAction = new AIcVersionScopePublicationFinalizationAction("probe", true, "scope-probe", 0,
                    AInFinalizationActionFailurePolicy.FAIL_BUILD_ON_FAILURE, 0, 0L, null, false, Map.of());
            var locComplete = locScheduler.finalizeVersionScope(locCombined.execution(), List.of(locAction), Map.of(), aEndpoint -> AIcNoProgress());
            AIcPublicationCheckAssertions.assertEquals(locComplete.state(), AInVersionScopePublicationAttemptState.COMPLETE);
            locScheduler.finalizeVersionScope(locComplete, List.of(locAction), Map.of(), aEndpoint -> AIcNoProgress());
            AIcPublicationCheckAssertions.assertEquals(locFinalized.get(), 1);
            var locPremature = new AIcPublicationDomainContribution("run", ".", List.of(locComplete), Map.of());
            AIcRejects(() -> AIcPublicationDomainBridge.aggregate("run", ".", "1.0", AInPublicationStability.RELEASE, locExpected, List.of(locPremature, locBeta)), IllegalArgumentException.class);
        }
        Throwable locFailure = new IllegalStateException("recorded failure", new IllegalArgumentException("recorded cause"));
        var locFailedResult = new AIcPublicationFinalizationActionResult("failed", true, false, true, 2, Duration.ofMillis(20), null, Map.of(), locFailure);
        var locFailedNode = new AIcFinalizationActionExecutionResult("failed", "version-scope", locFailedResult, Map.of(), List.of());
        var locFailedScope = new AIcVersionScopePublicationExecutionResult(".", "1.0", AInPublicationStability.RELEASE, AInVersionScopePublicationAttemptState.FAILED, List.of(), List.of(locFailedNode));
        var locFailed = new AIcPublicationDomainContribution("run", ".", List.of(locFailedScope), Map.of());
        var locFailedDecoded = AIcPublicationDomainBridge.decode(AIcPublicationDomainBridge.encode(locFailed));
        AIcPublicationCheckAssertions.assertEquals(AIcPublicationDomainBridge.encode(locFailedDecoded), AIcPublicationDomainBridge.encode(locFailed));
        AIcCheckStore(locAlpha);
    }
    private static void AIcCheckStore(AIcPublicationDomainContribution aContribution) throws Exception {
        Path locRepository = Files.createTempDirectory("modustro-domain-store-");
        try {
            AIcPublicationDomainStore.write(locRepository, "artifact-results", aContribution);
            Path locFile = AIcPublicationDomainStore.resultFile(locRepository, aContribution.invocationId(), aContribution.domainId(), "artifact-results");
            String locWire = Files.readString(locFile);
            AIcPublicationCheckAssertions.assertTrue(locWire.contains("\"FormatVersion\":1"));
            var locTimestamp = Files.getLastModifiedTime(locFile);
            AIcPublicationDomainStore.write(locRepository, "artifact-results", aContribution);
            AIcPublicationCheckAssertions.assertEquals(Files.getLastModifiedTime(locFile), locTimestamp);
            var locRead = AIcPublicationDomainStore.read(locRepository, "run", ".", "artifact-results", aText -> {
                AIcPublicationCheckAssertions.assertEquals(aText, locWire);
                return AIcPublicationDomainBridge.encode(aContribution);
            });
            AIcPublicationCheckAssertions.assertEquals(AIcPublicationDomainBridge.encode(locRead), AIcPublicationDomainBridge.encode(aContribution));
            try {
                AIcPublicationDomainStore.write(locRepository, "artifact-results", AIcDomain("run", ".", "alpha", false));
                throw new AssertionError("Committed domain results were overwritten.");
            } catch (java.io.IOException locExpected) {
                AIcPublicationCheckAssertions.assertTrue(locExpected.getMessage().contains("immutable"));
            }
            AIcPublicationDomainStore.write(locRepository, "scope-finalization", aContribution);
            AIcPublicationCheckAssertions.assertTrue(Files.isRegularFile(AIcPublicationDomainStore.resultFile(locRepository, "run", ".", "scope-finalization")));
            Path locStaleFile = AIcPublicationDomainStore.resultFile(locRepository, "next-run", ".", "artifact-results");
            Files.createDirectories(locStaleFile.getParent());
            Files.writeString(locStaleFile, locWire);
            try {
                AIcPublicationDomainStore.read(locRepository, "next-run", ".", "artifact-results", aText -> AIcPublicationDomainBridge.encode(aContribution));
                throw new AssertionError("Stale invocation identity was accepted at a fresh result path.");
            } catch (java.io.IOException locExpected) {
                AIcPublicationCheckAssertions.assertTrue(locExpected.getMessage().contains("identity"));
            }
            try (var locOversized = new java.io.RandomAccessFile(locStaleFile.toFile(), "rw")) { locOversized.setLength(32L * 1024L * 1024L + 1L); }
            try {
                AIcPublicationDomainStore.read(locRepository, "next-run", ".", "artifact-results", aText -> { throw new AssertionError("Oversized input reached the parser."); });
                throw new AssertionError("Oversized domain handoff was accepted.");
            } catch (java.io.IOException locExpected) {
                AIcPublicationCheckAssertions.assertTrue(locExpected.getMessage().contains("32 MiB"));
            }
        } finally {
            try (var locPaths = Files.walk(locRepository)) {
                for (Path locPath : locPaths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(locPath);
            }
        }
    }
    private static AIcPublicationDomainContribution AIcDomain(String aInvocation, String aDomain, String aArtifact, boolean aCompleted) {
        var locEndpoint = new AIcPublicationEndpoint("release", true, URI.create("https://output.example.test/releases/"), "maven-repository", null,
                0, AInPublicationFailurePolicy.FAIL_BUILD_ON_PUBLICATION_FAILURE, 0, 1000L, 5000L, true, Map.of("Provider", "test"));
        var locSnapshot = new AIcPublicationEndpoint("snapshot", true, URI.create("https://output.example.test/snapshots/"), "maven-repository", "snapshot-profile",
                1, AInPublicationFailurePolicy.IGNORE_PUBLICATION_FAILURE, 1, 100L, null, false, Map.of("nested", List.of(Map.of("key", "value"))));
        String locOutputIdentity = aArtifact + ":java";
        var locPayload = new AIcPublicationPayload(AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES, AInPublicationStability.RELEASE, locOutputIdentity, "1.0",
                List.of(new AIcPublicationPayloadFile(URI.create("https://input.example.test/" + aArtifact + ".jar"), "payload.jar")), Map.of("groupId", "test", "artifactId", aArtifact));
        var locAttempt = new AIcPublicationFinalizationActionResult("derived", true, true, false, 2, Duration.ofMillis(40), URI.create("https://output.example.test/derived"), Map.of("value", List.of("nested")), null);
        var locChild = new AIcFinalizationActionExecutionResult("child", "publication", locAttempt, Map.of(), List.of());
        var locParent = new AIcFinalizationActionExecutionResult("parent", "publication", locAttempt, Map.of("configuration", List.of("frozen")), List.of(locChild));
        var locPublication = new AIcPublicationExecutionResult("standard", locEndpoint, locPayload, Map.of("nested", List.of(Map.of("x", 1))),
                new AIcPublicationResult("standard", true, true, false, 1, Duration.ofSeconds(2), URI.create("https://output.example.test/payload.jar"), Map.of("result", List.of("frozen")), null), List.of(locParent));
        var locOutput = new AIcOutputPublicationExecutionResult(locOutputIdentity, "java", AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES, AInPublicationStability.RELEASE,
                "1.0", Map.of("release", locEndpoint, "snapshot", locSnapshot), Map.of("snapshot", locSnapshot), List.of(locPublication), List.of(), aCompleted);
        var locArtifact = new AIcArtifactPublicationExecutionResult(aArtifact, aArtifact, ".", "1.0", AInPublicationStability.RELEASE, List.of(locOutput), List.of());
        return new AIcPublicationDomainContribution(aInvocation, aDomain,
                List.of(new AIcVersionScopePublicationExecutionResult(".", "1.0", AInPublicationStability.RELEASE, AInVersionScopePublicationAttemptState.PUBLISHING, List.of(locArtifact), List.of())), Map.of("NonSecret", true));
    }
    private static AIiPublicationProgressReporter AIcNoProgress() {
        return new AIiPublicationProgressReporter() {
            @Override public void started(String aMessage) { }
            @Override public void progress(long aCompleted, long aTotal, String aUnit, String aMessage) { }
            @Override public void indeterminate(String aMessage) { }
            @Override public void completed(String aMessage) { }
        };
    }
    private static void AIcRejects(Runnable aCall, Class<? extends Throwable> aExpected) {
        try { aCall.run(); } catch (Throwable locFailure) {
            if (aExpected.isInstance(locFailure)) return;
            throw new AssertionError("Unexpected rejection type.", locFailure);
        }
        throw new AssertionError("Unsafe publication-domain input was accepted.");
    }
}
