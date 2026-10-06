package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;

/** Maps complete result trees to a versioned data-only handoff and validates a shared scope's domain barrier. */
public final class AIcPublicationDomainBridge {
    public static final int FORMAT_VERSION = 1;
    private AIcPublicationDomainBridge() { }

    /** Complete aggregation state; missing domains or artifacts cannot pass the higher finalization barrier. */
    public record AIcdAggregation(AIcVersionScopePublicationExecutionResult execution, List<String> missingResults) {
        public AIcdAggregation { missingResults = List.copyOf(missingResults); }
        public boolean readyForFinalization() {
            return missingResults.isEmpty() && execution.artifacts().stream().allMatch(AIcArtifactPublicationExecutionResult::success);
        }
    }

    /** Encodes only JSON-compatible values; payload URIs remain canonical and exceptions become diagnostics. */
    public static Map<String, Object> encode(AIcPublicationDomainContribution aContribution) {
        Map<String, Object> locValue = AIcMap("FormatVersion", FORMAT_VERSION,
                "InvocationId", aContribution.invocationId(), "DomainId", aContribution.domainId(),
                "VersionScopes", aContribution.versionScopes().stream().map(AIcPublicationDomainBridge::AIcScopeMap).toList(),
                "Metadata", aContribution.metadata());
        AIcCheckWireValue(locValue, 0);
        return AIcPublicationValues.freeze(locValue);
    }

    /** Reconstructs immutable Core objects without Java serialization or shared Gradle class identities. */
    public static AIcPublicationDomainContribution decode(Map<String, Object> aValue) {
        AIcCheckWireValue(aValue, 0);
        if (AIcInt(aValue, "FormatVersion") != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported publication-domain handoff format.");
        }
        return new AIcPublicationDomainContribution(AIcString(aValue, "InvocationId"), AIcString(aValue, "DomainId"),
                AIcList(aValue, "VersionScopes").stream().map(AIcPublicationDomainBridge::AIcScope).toList(),
                AIcObject(aValue.get("Metadata")));
    }

    /** Checks fresh domain identities and every expected artifact before exposing the combined immutable scope. */
    public static AIcdAggregation aggregate(
            String aInvocationId, String aScopeId, String aVersion, AInPublicationStability aStability,
            Map<String, Set<String>> aExpectedArtifactsByDomain, List<AIcPublicationDomainContribution> aContributions) {
        if (aExpectedArtifactsByDomain.isEmpty()) throw new IllegalArgumentException("A publication scope needs expected domains.");
        Map<String, AIcPublicationDomainContribution> locDomains = new LinkedHashMap<>();
        Set<String> locPlannedArtifacts = new HashSet<>();
        for (Set<String> locPaths : aExpectedArtifactsByDomain.values()) for (String locPath : locPaths) {
            if (!locPlannedArtifacts.add(locPath)) throw new IllegalArgumentException("Artifact belongs to multiple publication domains: " + locPath);
        }
        for (AIcPublicationDomainContribution locContribution : aContributions) {
            if (!aInvocationId.equals(locContribution.invocationId())) throw new IllegalArgumentException("Stale publication-domain invocation: " + locContribution.domainId());
            if (!aExpectedArtifactsByDomain.containsKey(locContribution.domainId())) throw new IllegalArgumentException("Unexpected publication domain: " + locContribution.domainId());
            if (locDomains.putIfAbsent(locContribution.domainId(), locContribution) != null) throw new IllegalArgumentException("Duplicate publication domain: " + locContribution.domainId());
        }
        List<String> locMissing = new ArrayList<>();
        List<AIcArtifactPublicationExecutionResult> locArtifacts = new ArrayList<>();
        for (Map.Entry<String, Set<String>> locExpected : new TreeMap<>(aExpectedArtifactsByDomain).entrySet()) {
            AIcPublicationDomainContribution locContribution = locDomains.get(locExpected.getKey());
            if (locContribution == null) { locMissing.add("domain:" + locExpected.getKey()); continue; }
            List<AIcVersionScopePublicationExecutionResult> locScopes = locContribution.versionScopes().stream()
                    .filter(aScope -> Objects.equals(aScope.versionScopeId(), aScopeId)
                            && Objects.equals(aScope.version(), aVersion) && aScope.stability() == aStability).toList();
            if (locScopes.size() > 1) throw new IllegalArgumentException("Duplicate Version Scope in domain: " + locExpected.getKey());
            if (locScopes.isEmpty()) {
                for (String locPath : new TreeSet<>(locExpected.getValue())) locMissing.add("artifact:" + locExpected.getKey() + ":" + locPath);
                continue;
            }
            AIcVersionScopePublicationExecutionResult locScope = locScopes.get(0);
            if (aExpectedArtifactsByDomain.size() > 1 && (!locScope.versionScopePublicationFinalizationActions().isEmpty()
                    || locScope.state() == AInVersionScopePublicationAttemptState.COMPLETE)) {
                throw new IllegalArgumentException("Shared Version Scope was finalized before its domain barrier: " + locExpected.getKey());
            }
            Set<String> locSeen = new HashSet<>();
            for (AIcArtifactPublicationExecutionResult locArtifact : locScope.artifacts()) {
                if (!Objects.equals(locArtifact.versionScopeId(), aScopeId) || !Objects.equals(locArtifact.version(), aVersion)
                        || locArtifact.stability() != aStability) throw new IllegalArgumentException("Artifact result contradicts its Version Scope.");
                if (!locExpected.getValue().contains(locArtifact.artifactPath())) throw new IllegalArgumentException("Unexpected artifact in publication domain: " + locArtifact.artifactPath());
                if (!locSeen.add(locArtifact.artifactPath())) throw new IllegalArgumentException("Duplicate artifact result: " + locArtifact.artifactPath());
                if (locArtifact.outputs().isEmpty()) locMissing.add("outputs:" + locExpected.getKey() + ":" + locArtifact.artifactPath());
                for (AIcOutputPublicationExecutionResult locOutput : locArtifact.outputs()) {
                    if (!Objects.equals(locOutput.version(), aVersion) || locOutput.stability() != aStability) {
                        throw new IllegalArgumentException("Output result contradicts its artifact boundary.");
                    }
                    for (AIcPublicationExecutionResult locPublication : locOutput.publications()) {
                        if (!Objects.equals(locPublication.payload().artifactIdentity(), locOutput.artifactIdentity())
                                || locPublication.payload().outputKind() != locOutput.outputKind()
                                || locPublication.payload().stability() != locOutput.stability()) {
                            throw new IllegalArgumentException("Publication payload contradicts its output boundary.");
                        }
                    }
                }
                locArtifacts.add(locArtifact);
            }
            for (String locPath : new TreeSet<>(locExpected.getValue())) if (!locSeen.contains(locPath)) {
                locMissing.add("artifact:" + locExpected.getKey() + ":" + locPath);
            }
        }
        locArtifacts.sort(Comparator.comparing(AIcArtifactPublicationExecutionResult::artifactPath));
        boolean locReady = locMissing.isEmpty() && locArtifacts.stream().allMatch(AIcArtifactPublicationExecutionResult::success);
        return new AIcdAggregation(new AIcVersionScopePublicationExecutionResult(aScopeId, aVersion, aStability,
                locReady ? AInVersionScopePublicationAttemptState.PUBLISHING : AInVersionScopePublicationAttemptState.FAILED,
                locArtifacts, List.of()), locMissing);
    }

    private static Map<String, Object> AIcScopeMap(AIcVersionScopePublicationExecutionResult aScope) {
        return AIcMap("VersionScopeId", aScope.versionScopeId(), "Version", aScope.version(), "Stability", aScope.stability().name(),
                "State", aScope.state().name(), "Artifacts", aScope.artifacts().stream().map(AIcPublicationDomainBridge::AIcArtifactMap).toList(),
                "VersionScopePublicationFinalizationActions", AIcNodes(aScope.versionScopePublicationFinalizationActions()));
    }
    private static AIcVersionScopePublicationExecutionResult AIcScope(Map<String, Object> aValue) {
        return new AIcVersionScopePublicationExecutionResult(AIcString(aValue, "VersionScopeId"), AIcString(aValue, "Version"),
                AInPublicationStability.valueOf(AIcString(aValue, "Stability")), AInVersionScopePublicationAttemptState.valueOf(AIcString(aValue, "State")),
                AIcList(aValue, "Artifacts").stream().map(AIcPublicationDomainBridge::AIcArtifact).toList(), AIcNodes(aValue, "VersionScopePublicationFinalizationActions"));
    }
    private static Map<String, Object> AIcArtifactMap(AIcArtifactPublicationExecutionResult aArtifact) {
        return AIcMap("ArtifactIdentity", aArtifact.artifactIdentity(), "ArtifactPath", aArtifact.artifactPath(),
                "VersionScopeId", aArtifact.versionScopeId(), "Version", aArtifact.version(), "Stability", aArtifact.stability().name(),
                "Outputs", aArtifact.outputs().stream().map(AIcPublicationDomainBridge::AIcOutputMap).toList(),
                "ArtifactPublicationFinalizationActions", AIcNodes(aArtifact.artifactPublicationFinalizationActions()));
    }
    private static AIcArtifactPublicationExecutionResult AIcArtifact(Map<String, Object> aValue) {
        return new AIcArtifactPublicationExecutionResult(AIcString(aValue, "ArtifactIdentity"), AIcString(aValue, "ArtifactPath"),
                AIcString(aValue, "VersionScopeId"), AIcString(aValue, "Version"), AInPublicationStability.valueOf(AIcString(aValue, "Stability")),
                AIcList(aValue, "Outputs").stream().map(AIcPublicationDomainBridge::AIcOutput).toList(), AIcNodes(aValue, "ArtifactPublicationFinalizationActions"));
    }
    private static Map<String, Object> AIcOutputMap(AIcOutputPublicationExecutionResult aOutput) {
        return AIcMap("ArtifactIdentity", aOutput.artifactIdentity(), "TechnologyKind", aOutput.technologyKind(),
                "OutputKind", aOutput.outputKind().name(), "Stability", aOutput.stability().name(), "Version", aOutput.version(),
                "Completed", aOutput.completed(), "PublicationEndpointRegistry", AIcEndpoints(aOutput.publicationEndpointRegistry()),
                "SnapshotPublicationEndpoints", AIcEndpoints(aOutput.snapshotPublicationEndpoints()),
                "Publications", aOutput.publications().stream().map(AIcPublicationDomainBridge::AIcPublicationMap).toList(),
                "OutputPublicationFinalizationActions", AIcNodes(aOutput.outputPublicationFinalizationActions()));
    }
    private static AIcOutputPublicationExecutionResult AIcOutput(Map<String, Object> aValue) {
        return new AIcOutputPublicationExecutionResult(AIcString(aValue, "ArtifactIdentity"), AIcString(aValue, "TechnologyKind"),
                AInPublicationOutputKind.valueOf(AIcString(aValue, "OutputKind")), AInPublicationStability.valueOf(AIcString(aValue, "Stability")),
                AIcString(aValue, "Version"), AIcEndpoints(aValue, "PublicationEndpointRegistry"), AIcEndpoints(aValue, "SnapshotPublicationEndpoints"),
                AIcList(aValue, "Publications").stream().map(AIcPublicationDomainBridge::AIcPublication).toList(),
                AIcNodes(aValue, "OutputPublicationFinalizationActions"), AIcBoolean(aValue, "Completed"));
    }
    private static Map<String, Object> AIcPublicationMap(AIcPublicationExecutionResult aPublication) {
        return AIcMap("PublicationId", aPublication.publicationId(), "Endpoint", AIcEndpointMap(aPublication.endpoint()),
                "Payload", AIcPayloadMap(aPublication.payload()), "Configuration", aPublication.configuration(),
                "Result", aPublication.result() == null ? null : AIcResultMap(aPublication.result()),
                "PublicationFinalizationActions", AIcNodes(aPublication.publicationFinalizationActions()));
    }
    private static AIcPublicationExecutionResult AIcPublication(Map<String, Object> aValue) {
        return new AIcPublicationExecutionResult(AIcString(aValue, "PublicationId"), AIcEndpoint(AIcObject(aValue.get("Endpoint"))),
                AIcPayload(AIcObject(aValue.get("Payload"))), AIcObject(aValue.get("Configuration")),
                aValue.get("Result") == null ? null : AIcResult(AIcObject(aValue.get("Result"))), AIcNodes(aValue, "PublicationFinalizationActions"));
    }
    private static Map<String, Object> AIcPayloadMap(AIcPublicationPayload aPayload) {
        return AIcMap("OutputKind", aPayload.outputKind().name(), "Stability", aPayload.stability().name(),
                "ArtifactIdentity", aPayload.artifactIdentity(), "Version", aPayload.version(), "Coordinates", aPayload.coordinates(),
                "Files", aPayload.files().stream().map(aFile -> AIcMap("ContentUri", aFile.contentUri().toString(), "LogicalName", aFile.logicalName())).toList());
    }
    private static AIcPublicationPayload AIcPayload(Map<String, Object> aValue) {
        Map<String, String> locCoordinates = new LinkedHashMap<>();
        AIcObject(aValue.get("Coordinates")).forEach((aKey, aItem) -> locCoordinates.put(aKey, Objects.toString(aItem)));
        return new AIcPublicationPayload(AInPublicationOutputKind.valueOf(AIcString(aValue, "OutputKind")),
                AInPublicationStability.valueOf(AIcString(aValue, "Stability")), AIcString(aValue, "ArtifactIdentity"), AIcString(aValue, "Version"),
                AIcList(aValue, "Files").stream().map(aFile -> new AIcPublicationPayloadFile(URI.create(AIcString(aFile, "ContentUri")), AIcString(aFile, "LogicalName"))).toList(), locCoordinates);
    }
    private static Map<String, Object> AIcEndpointMap(AIcPublicationEndpoint aEndpoint) {
        return AIcMap("Id", aEndpoint.id(), "ExecutionEnabled", aEndpoint.executionEnabled(), "PublicationUri", AIcUri(aEndpoint.publicationUri()),
                "PublicationAdapter", aEndpoint.publicationAdapter(), "PublicationCredentialProfile", aEndpoint.publicationCredentialProfile(),
                "ExecutionOrder", aEndpoint.executionOrder(), "PublicationFailurePolicy", aEndpoint.publicationFailurePolicy().name(),
                "PublicationRetryCount", aEndpoint.publicationRetryCount(), "PublicationWaitForNextAttemptMillis", aEndpoint.publicationWaitForNextAttemptMillis(),
                "PublicationAttemptTimeoutMillis", aEndpoint.publicationAttemptTimeoutMillis(), "ShowPublicationProgressIfPossible", aEndpoint.showPublicationProgressIfPossible(),
                "Configuration", aEndpoint.configuration());
    }
    private static AIcPublicationEndpoint AIcEndpoint(Map<String, Object> aValue) {
        return new AIcPublicationEndpoint(AIcString(aValue, "Id"), AIcBoolean(aValue, "ExecutionEnabled"), AIcUri(aValue.get("PublicationUri")),
                (String)aValue.get("PublicationAdapter"), (String)aValue.get("PublicationCredentialProfile"), AIcInt(aValue, "ExecutionOrder"),
                AInPublicationFailurePolicy.valueOf(AIcString(aValue, "PublicationFailurePolicy")), AIcInt(aValue, "PublicationRetryCount"),
                AIcLong(aValue, "PublicationWaitForNextAttemptMillis"), aValue.get("PublicationAttemptTimeoutMillis") == null ? null : AIcLong(aValue, "PublicationAttemptTimeoutMillis"),
                AIcBoolean(aValue, "ShowPublicationProgressIfPossible"), AIcObject(aValue.get("Configuration")));
    }
    private static Map<String, Object> AIcEndpoints(Map<String, AIcPublicationEndpoint> aEndpoints) {
        Map<String, Object> locMap = new LinkedHashMap<>();
        aEndpoints.forEach((aId, aEndpoint) -> locMap.put(aId, AIcEndpointMap(aEndpoint)));
        return locMap;
    }
    private static Map<String, AIcPublicationEndpoint> AIcEndpoints(Map<String, Object> aValue, String aKey) {
        Map<String, AIcPublicationEndpoint> locMap = new LinkedHashMap<>();
        AIcObject(aValue.get(aKey)).forEach((aId, aEndpoint) -> {
            AIcPublicationEndpoint locEndpoint = AIcEndpoint(AIcObject(aEndpoint));
            if (!aId.equals(locEndpoint.id())) throw new IllegalArgumentException("Publication endpoint registry Id mismatch.");
            locMap.put(aId, locEndpoint);
        });
        return locMap;
    }
    private static Map<String, Object> AIcResultMap(AIcPublicationResult aResult) {
        return AIcAttemptMap(aResult.publicationId(), aResult.started(), aResult.success(), aResult.ignoredFailure(), aResult.attempts(),
                aResult.duration(), aResult.outputUri(), aResult.metadata(), aResult.failure());
    }
    private static Map<String, Object> AIcAttemptMap(String aId, boolean aStarted, boolean aSuccess, boolean aIgnored, int aAttempts,
            Duration aDuration, URI aOutputUri, Map<String, Object> aMetadata, Throwable aFailure) {
        return AIcMap("Id", aId, "Started", aStarted, "Success", aSuccess, "IgnoredFailure", aIgnored, "Attempts", aAttempts,
                "Duration", aDuration.toString(), "OutputUri", AIcUri(aOutputUri), "Metadata", aMetadata, "Failure", AIcFailureMap(aFailure, 0));
    }
    private static AIcPublicationResult AIcResult(Map<String, Object> aValue) {
        return new AIcPublicationResult(AIcString(aValue, "Id"), AIcBoolean(aValue, "Started"), AIcBoolean(aValue, "Success"),
                AIcBoolean(aValue, "IgnoredFailure"), AIcInt(aValue, "Attempts"), Duration.parse(AIcString(aValue, "Duration")),
                AIcUri(aValue.get("OutputUri")), AIcObject(aValue.get("Metadata")), AIcFailure(aValue.get("Failure"), 0));
    }
    private static List<Map<String, Object>> AIcNodes(List<AIcFinalizationActionExecutionResult> aNodes) {
        return aNodes.stream().map(aNode -> {
            AIcPublicationFinalizationActionResult locResult = aNode.result();
            return AIcMap("Id", aNode.id(), "ScopeKind", aNode.scopeKind(), "Configuration", aNode.configuration(),
                    "Result", locResult == null ? null : AIcAttemptMap(locResult.actionId(), locResult.started(), locResult.success(),
                            locResult.ignoredFailure(), locResult.attempts(), locResult.duration(), locResult.outputUri(), locResult.metadata(), locResult.failure()),
                    "FinalizationActions", AIcNodes(aNode.finalizationActions()));
        }).toList();
    }
    private static List<AIcFinalizationActionExecutionResult> AIcNodes(Map<String, Object> aValue, String aKey) {
        return AIcList(aValue, aKey).stream().map(aNode -> {
            Map<String, Object> locAttempt = aNode.get("Result") == null ? null : AIcObject(aNode.get("Result"));
            AIcPublicationFinalizationActionResult locResult = locAttempt == null ? null : new AIcPublicationFinalizationActionResult(
                    AIcString(locAttempt, "Id"), AIcBoolean(locAttempt, "Started"), AIcBoolean(locAttempt, "Success"), AIcBoolean(locAttempt, "IgnoredFailure"),
                    AIcInt(locAttempt, "Attempts"), Duration.parse(AIcString(locAttempt, "Duration")), AIcUri(locAttempt.get("OutputUri")),
                    AIcObject(locAttempt.get("Metadata")), AIcFailure(locAttempt.get("Failure"), 0));
            return new AIcFinalizationActionExecutionResult(AIcString(aNode, "Id"), AIcString(aNode, "ScopeKind"), locResult,
                    AIcObject(aNode.get("Configuration")), AIcNodes(aNode, "FinalizationActions"));
        }).toList();
    }
    private static Map<String, Object> AIcFailureMap(Throwable aFailure, int aDepth) {
        if (aFailure == null) return null;
        if (aDepth > 32) throw new IllegalArgumentException("Publication failure chain is too deep.");
        return AIcMap("Type", aFailure instanceof AIxRecordedFailure locRecorded ? locRecorded.recordedType : aFailure.getClass().getName(),
                "Message", aFailure.getMessage(), "Cause", AIcFailureMap(aFailure.getCause(), aDepth + 1));
    }
    private static Throwable AIcFailure(Object aValue, int aDepth) {
        if (aValue == null) return null;
        if (aDepth > 32) throw new IllegalArgumentException("Publication failure chain is too deep.");
        Map<String, Object> locMap = AIcObject(aValue);
        return new AIxRecordedFailure(AIcString(locMap, "Type"), (String)locMap.get("Message"), AIcFailure(locMap.get("Cause"), aDepth + 1));
    }
    private static final class AIxRecordedFailure extends RuntimeException {
        private final String recordedType;
        private AIxRecordedFailure(String aType, String aMessage, Throwable aCause) { super(aMessage, aCause); recordedType = aType; }
    }
    private static Map<String, Object> AIcMap(Object... aPairs) {
        Map<String, Object> locResult = new LinkedHashMap<>();
        for (int locIndex = 0; locIndex < aPairs.length; locIndex += 2) locResult.put((String)aPairs[locIndex], aPairs[locIndex + 1]);
        return locResult;
    }
    private static void AIcCheckWireValue(Object aValue, int aDepth) {
        if (aDepth > 512) throw new IllegalArgumentException("Publication-domain handoff nesting is too deep.");
        if (aValue == null || aValue instanceof String || aValue instanceof Boolean) return;
        if (aValue instanceof Number locNumber) {
            try { new java.math.BigDecimal(locNumber.toString()); }
            catch (NumberFormatException locFailure) { throw new IllegalArgumentException("Publication-domain handoff requires finite numbers.", locFailure); }
            return;
        }
        if (aValue instanceof Map<?, ?> locMap) {
            for (Map.Entry<?, ?> locItem : locMap.entrySet()) {
                if (!(locItem.getKey() instanceof String)) throw new IllegalArgumentException("Publication-domain handoff requires string keys.");
                AIcCheckWireValue(locItem.getValue(), aDepth + 1);
            }
            return;
        }
        if (aValue instanceof List<?> locList) {
            for (Object locItem : locList) AIcCheckWireValue(locItem, aDepth + 1);
            return;
        }
        throw new IllegalArgumentException("Publication-domain handoff contains a non-data value: " + aValue.getClass().getName());
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> AIcObject(Object aValue) {
        if (!(aValue instanceof Map<?, ?> locMap) || locMap.keySet().stream().anyMatch(aKey -> !(aKey instanceof String))) {
            throw new IllegalArgumentException("Publication-domain handoff requires an object with string keys.");
        }
        return (Map<String, Object>)locMap;
    }
    private static List<Map<String, Object>> AIcList(Map<String, Object> aValue, String aKey) {
        if (!(aValue.get(aKey) instanceof List<?> locList)) throw new IllegalArgumentException("Publication-domain handoff requires list " + aKey);
        return locList.stream().map(AIcPublicationDomainBridge::AIcObject).toList();
    }
    private static String AIcString(Map<String, Object> aValue, String aKey) {
        if (!(aValue.get(aKey) instanceof String locString) || locString.isBlank()) throw new IllegalArgumentException("Publication-domain handoff requires string " + aKey);
        return locString;
    }
    private static boolean AIcBoolean(Map<String, Object> aValue, String aKey) {
        if (!(aValue.get(aKey) instanceof Boolean locBoolean)) throw new IllegalArgumentException("Publication-domain handoff requires boolean " + aKey);
        return locBoolean;
    }
    private static long AIcLong(Map<String, Object> aValue, String aKey) {
        if (!(aValue.get(aKey) instanceof Number locNumber)) throw new IllegalArgumentException("Publication-domain handoff requires number " + aKey);
        return new java.math.BigDecimal(locNumber.toString()).longValueExact();
    }
    private static int AIcInt(Map<String, Object> aValue, String aKey) { return Math.toIntExact(AIcLong(aValue, aKey)); }
    private static String AIcUri(URI aUri) { return aUri == null ? null : aUri.toString(); }
    private static URI AIcUri(Object aValue) { return aValue == null ? null : URI.create((String)aValue); }
}
