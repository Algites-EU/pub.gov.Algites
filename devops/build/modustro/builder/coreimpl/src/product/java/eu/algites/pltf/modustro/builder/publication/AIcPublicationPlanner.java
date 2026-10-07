package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.execution.AIngBuildExecutionFailurePolicy_1;

import eu.algites.pltf.modustro.builder.model.publication.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Converts one effective output-publication plan into publication jobs and finalization actions. */
public final class AIcPublicationPlanner {
    /** Canonical schema field name until the publication descriptor itself is consumed as a generated model. */
    private static final String SCHEMA_FIELD_NAME__EXECUTION_FAILURE_POLICY = "ExecutionFailurePolicy";

    public List<AIcPublicationJob> plan(AIcPublicationPayload aRoot, Map<String, Object> aConfiguration,
            Map<String, Object> aContext, Path aDirectory) throws Exception {
        if (!Boolean.TRUE.equals(AIcValue(aConfiguration, "PublicationEnabled", "publicationEnabled"))) return List.of();
        if (aRoot.coordinates().getOrDefault("groupId", "").isBlank()) {
            throw new IllegalArgumentException("Publication requires effective GroupId before planning.");
        }
        List<AIcPublicationJob> locJobs = new ArrayList<>();
        Set<String> locIds = new HashSet<>();
        Set<String> locTargets = new HashSet<>();
        AIcPublicationPayload locFrozen = AIcFreeze(aRoot, aDirectory);
        Map<String, AIcPublicationEndpoint> locRegistry = publicationEndpointRegistry(aConfiguration);
        Object locEndpointsValue = AIcValue(aConfiguration, "PublicationEndpoints", "publicationEndpoints");
        for (Map<String, Object> locDeclaration : AIcMaps(locEndpointsValue)) {
            AIcPublicationEndpoint locEndpoint = endpoint(locDeclaration, Objects.toString(AIcValue(locDeclaration, "Id", "id"), ""));
            locRegistry.putIfAbsent(locEndpoint.id(), locEndpoint);
            if (!locEndpoint.executionEnabled()) continue;
            Object locFormsValue = AIcValue(locDeclaration, "Publications", "publications");
            List<Map<String, Object>> locForms = locFormsValue == null ? List.of(Map.of("Id", "standard")) : AIcMaps(locFormsValue);
            int locIndex = 0;
            for (Map<String, Object> locForm : locForms) {
                if (Boolean.FALSE.equals(AIcValue(locForm, "ExecutionEnabled", "executionEnabled"))) continue;
                String locLocalId = Objects.toString(AIcValue(locForm, "Id", "id"), "standard");
                String locId = locEndpoint.id() + "/" + locLocalId;
                if (!locIds.add(locId)) throw new IllegalArgumentException("Duplicate publication path " + locId);
                AIcPublicationPayload locPayload = AIcForm(locFrozen, locForm, locEndpoint, locIndex++ == 0, aContext);
                for (AIcPublicationPayloadFile locFile : locPayload.files()) {
                    String locTarget = locEndpoint.publicationUri() + "|" + locFile.logicalName();
                    if (!locTargets.add(locTarget)) throw new IllegalArgumentException("Duplicate publication target " + locFile.logicalName());
                }
                List<AIcPublicationFinalizationAction> locActions = publicationFinalizationActions(
                        AIcValue(locForm, "PublicationFinalizationActions", "publicationFinalizationActions"), 0);
                if (locActions.stream().noneMatch(locAction -> "build-record".equals(locAction.id()))) {
                    ArrayList<AIcPublicationFinalizationAction> locWithDefault = new ArrayList<>(locActions);
                    locWithDefault.add(new AIcPublicationFinalizationAction("build-record", true, "modustro-build-record", null, 0,
                            AIngBuildExecutionFailurePolicy_1.FAIL_BUILD_ON_FAILURE, 0, 1000L, null, true, Map.of(), List.of()));
                    locActions = List.copyOf(locWithDefault);
                }
                locJobs.add(new AIcPublicationJob(locId, AIcWithId(locEndpoint, locId), locPayload, aContext, locActions, locRegistry));
            }
        }
        return List.copyOf(locJobs);
    }

    public static Map<String, AIcPublicationEndpoint> publicationEndpointRegistry(Map<String, Object> aConfiguration) {
        LinkedHashMap<String, AIcPublicationEndpoint> locRegistry = new LinkedHashMap<>();
        Object locRegistryValue = AIcValue(aConfiguration, "PublicationEndpointRegistry", "publicationEndpointRegistry");
        for (Map<String, Object> locDeclaration : AIcMaps(locRegistryValue)) {
            String locId = Objects.toString(AIcValue(locDeclaration, "Id", "id"), "");
            if (!locId.isBlank()) locRegistry.put(locId, endpoint(locDeclaration, locId));
        }
        return locRegistry;
    }

    public static Map<String, AIcPublicationEndpoint> snapshotPublicationEndpoints(Map<String, Object> aConfiguration) {
        LinkedHashMap<String, AIcPublicationEndpoint> locResult = new LinkedHashMap<>();
        Object locValue = AIcValue(aConfiguration, "SnapshotPublicationEndpoints", "snapshotPublicationEndpoints");
        for (Map<String, Object> locDeclaration : AIcMaps(locValue)) {
            String locId = Objects.toString(AIcValue(locDeclaration, "Id", "id"), "");
            if (!locId.isBlank()) locResult.put(locId, endpoint(locDeclaration, locId));
        }
        return Map.copyOf(locResult);
    }

    public static List<AIcOutputPublicationFinalizationAction> outputPublicationFinalizationActions(Map<String, Object> aConfiguration) {
        List<AIcOutputPublicationFinalizationAction> locResult = new ArrayList<>();
        for (Map<String, Object> locItem : AIcMaps(AIcValue(aConfiguration,
                "OutputPublicationFinalizationActions", "outputPublicationFinalizationActions"))) {
            String locId = AIcActionId(locItem, "OutputPublicationFinalizationActions");
            locResult.add(new AIcOutputPublicationFinalizationAction(
                    locId,
                    !Boolean.FALSE.equals(AIcValueOrDefault(locItem, true, "ExecutionEnabled", "executionEnabled")),
                    Objects.toString(AIcValue(locItem, "OutputPublicationFinalizationActionAdapter", "outputPublicationFinalizationActionAdapter"), null),
                    AIcInt(locItem, 0, "ExecutionOrder", "executionOrder"),
                    AIcBuildExecutionFailurePolicy(AIcValueOrDefault(locItem,
                            AIngBuildExecutionFailurePolicy_1.FAIL_BUILD_ON_FAILURE.wireValue(), SCHEMA_FIELD_NAME__EXECUTION_FAILURE_POLICY)),
                    AIcInt(locItem, 0, "RetryCount", "retryCount"),
                    AIcLong(locItem, 1000L, "WaitForNextAttemptMillis", "waitForNextAttemptMillis"),
                    AIcNullableLong(locItem, "AttemptTimeoutMillis", "attemptTimeoutMillis"),
                    !Boolean.FALSE.equals(AIcValueOrDefault(locItem, true, "ShowProgressIfPossible", "showProgressIfPossible")),
                    AIcObjectMap(AIcValue(locItem, "Configuration", "configuration"))));
        }
        AIcRequireUniqueActionIds(locResult.stream().map(AIcOutputPublicationFinalizationAction::id).toList(), "OutputPublicationFinalizationActions");
        return List.copyOf(locResult);
    }

    public static List<AIcArtifactPublicationFinalizationAction> artifactPublicationFinalizationActions(Object aValue) {
        List<AIcArtifactPublicationFinalizationAction> locResult = new ArrayList<>();
        for (Map<String, Object> locItem : AIcMaps(aValue)) {
            String locId = AIcActionId(locItem, "ArtifactPublicationFinalizationActions");
            locResult.add(new AIcArtifactPublicationFinalizationAction(
                    locId,
                    !Boolean.FALSE.equals(AIcValueOrDefault(locItem, true, "ExecutionEnabled", "executionEnabled")),
                    Objects.toString(AIcValue(locItem, "ArtifactPublicationFinalizationActionAdapter", "artifactPublicationFinalizationActionAdapter"), null),
                    AIcInt(locItem, 0, "ExecutionOrder", "executionOrder"),
                    AIcBuildExecutionFailurePolicy(AIcValueOrDefault(locItem,
                            AIngBuildExecutionFailurePolicy_1.FAIL_BUILD_ON_FAILURE.wireValue(), SCHEMA_FIELD_NAME__EXECUTION_FAILURE_POLICY)),
                    AIcInt(locItem, 0, "RetryCount", "retryCount"),
                    AIcLong(locItem, 1000L, "WaitForNextAttemptMillis", "waitForNextAttemptMillis"),
                    AIcNullableLong(locItem, "AttemptTimeoutMillis", "attemptTimeoutMillis"),
                    !Boolean.FALSE.equals(AIcValueOrDefault(locItem, true, "ShowProgressIfPossible", "showProgressIfPossible")),
                    AIcObjectMap(AIcValue(locItem, "Configuration", "configuration"))));
        }
        AIcRequireUniqueActionIds(locResult.stream().map(AIcArtifactPublicationFinalizationAction::id).toList(), "ArtifactPublicationFinalizationActions");
        return List.copyOf(locResult);
    }

    public static List<AIcVersionScopePublicationFinalizationAction> versionScopePublicationFinalizationActions(Object aValue) {
        List<AIcVersionScopePublicationFinalizationAction> locResult = new ArrayList<>();
        for (Map<String, Object> locItem : AIcMaps(aValue)) {
            String locId = AIcActionId(locItem, "VersionScopePublicationFinalizationActions");
            locResult.add(new AIcVersionScopePublicationFinalizationAction(
                    locId,
                    !Boolean.FALSE.equals(AIcValueOrDefault(locItem, true, "ExecutionEnabled", "executionEnabled")),
                    Objects.toString(AIcValue(locItem, "VersionScopePublicationFinalizationActionAdapter", "versionScopePublicationFinalizationActionAdapter"), null),
                    AIcInt(locItem, 0, "ExecutionOrder", "executionOrder"),
                    AIcBuildExecutionFailurePolicy(AIcValueOrDefault(locItem,
                            AIngBuildExecutionFailurePolicy_1.FAIL_BUILD_ON_FAILURE.wireValue(), SCHEMA_FIELD_NAME__EXECUTION_FAILURE_POLICY)),
                    AIcInt(locItem, 0, "RetryCount", "retryCount"),
                    AIcLong(locItem, 1000L, "WaitForNextAttemptMillis", "waitForNextAttemptMillis"),
                    AIcNullableLong(locItem, "AttemptTimeoutMillis", "attemptTimeoutMillis"),
                    !Boolean.FALSE.equals(AIcValueOrDefault(locItem, true, "ShowProgressIfPossible", "showProgressIfPossible")),
                    AIcObjectMap(AIcValue(locItem, "Configuration", "configuration"))));
        }
        AIcRequireUniqueActionIds(locResult.stream().map(AIcVersionScopePublicationFinalizationAction::id).toList(), "VersionScopePublicationFinalizationActions");
        return List.copyOf(locResult);
    }

    public static AIcPublicationEndpoint endpoint(Map<String, Object> aItem, String aId) {
        String locUri = Objects.toString(AIcValue(aItem, "PublicationUri", "publicationUri"), null);
        String locAdapter = Objects.toString(AIcValue(aItem, "PublicationAdapter", "publicationAdapter"), null);
        return new AIcPublicationEndpoint(
                aId,
                !Boolean.FALSE.equals(AIcValueOrDefault(aItem, true, "ExecutionEnabled", "executionEnabled")),
                locUri == null ? null : URI.create(locUri),
                locAdapter,
                Objects.toString(AIcValue(aItem, "PublicationCredentialProfile", "publicationCredentialProfile"), null),
                AIcInt(aItem, 0, "ExecutionOrder", "executionOrder"),
                AIcBuildExecutionFailurePolicy(AIcValueOrDefault(aItem,
                        AIngBuildExecutionFailurePolicy_1.FAIL_BUILD_ON_FAILURE.wireValue(), SCHEMA_FIELD_NAME__EXECUTION_FAILURE_POLICY)),
                AIcInt(aItem, 0, "PublicationRetryCount", "publicationRetryCount"),
                AIcLong(aItem, 1000L, "PublicationWaitForNextAttemptMillis", "publicationWaitForNextAttemptMillis"),
                AIcNullableLong(aItem, "PublicationAttemptTimeoutMillis", "publicationAttemptTimeoutMillis"),
                !Boolean.FALSE.equals(AIcValueOrDefault(aItem, true, "ShowPublicationProgressIfPossible", "showPublicationProgressIfPossible")),
                AIcObjectMap(AIcValue(aItem, "Configuration", "configuration")));
    }

    private static List<AIcPublicationFinalizationAction> publicationFinalizationActions(Object aValue, int aDepth) {
        if (aDepth > 64) throw new IllegalArgumentException("FinalizationActions nesting exceeds 64.");
        List<AIcPublicationFinalizationAction> locResult = new ArrayList<>();
        Set<String> locIds = new HashSet<>();
        for (Map<String, Object> locItem : AIcMaps(aValue)) {
            String locId = AIcActionId(locItem, aDepth == 0 ? "PublicationFinalizationActions" : "FinalizationActions");
            if (!locIds.add(locId)) throw new IllegalArgumentException("Finalization action Id must be unique among siblings.");
            String locAdapter = Objects.toString(AIcValue(locItem,
                    aDepth == 0 ? "PublicationFinalizationActionAdapter" : "FinalizationActionAdapter",
                    aDepth == 0 ? "publicationFinalizationActionAdapter" : "finalizationActionAdapter"), null);
            locResult.add(new AIcPublicationFinalizationAction(
                    locId,
                    !Boolean.FALSE.equals(AIcValueOrDefault(locItem, true, "ExecutionEnabled", "executionEnabled")),
                    locAdapter,
                    Objects.toString(AIcValue(locItem, "TargetPublicationEndpointId", "targetPublicationEndpointId"), null),
                    AIcInt(locItem, 0, "ExecutionOrder", "executionOrder"),
                    AIcBuildExecutionFailurePolicy(AIcValueOrDefault(locItem,
                            AIngBuildExecutionFailurePolicy_1.FAIL_BUILD_ON_FAILURE.wireValue(), SCHEMA_FIELD_NAME__EXECUTION_FAILURE_POLICY)),
                    AIcInt(locItem, 0, "RetryCount", "retryCount"),
                    AIcLong(locItem, 1000L, "WaitForNextAttemptMillis", "waitForNextAttemptMillis"),
                    AIcNullableLong(locItem, "AttemptTimeoutMillis", "attemptTimeoutMillis"),
                    !Boolean.FALSE.equals(AIcValueOrDefault(locItem, true, "ShowProgressIfPossible", "showProgressIfPossible")),
                    AIcObjectMap(AIcValue(locItem, "Configuration", "configuration")),
                    publicationFinalizationActions(AIcValue(locItem, "FinalizationActions", "finalizationActions"), aDepth + 1)));
        }
        return List.copyOf(locResult);
    }

    private static AIngBuildExecutionFailurePolicy_1 AIcBuildExecutionFailurePolicy(Object aValue) {
        String locValue = Objects.toString(aValue, "");
        return Arrays.stream(AIngBuildExecutionFailurePolicy_1.values())
                .filter(aPolicy -> aPolicy.wireValue().equals(locValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported ExecutionFailurePolicy '" + locValue + "'."));
    }

    private static String AIcActionId(Map<String, Object> aItem, String aCollectionName) {
        String locId = Objects.toString(AIcValue(aItem, "Id", "id"), "");
        if (!locId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException(aCollectionName + " action Id must be lowercase dash-separated.");
        }
        return locId;
    }

    private static void AIcRequireUniqueActionIds(List<String> aIds, String aCollectionName) {
        if (new HashSet<>(aIds).size() != aIds.size()) throw new IllegalArgumentException(aCollectionName + " requires unique action Id values.");
    }

    private static AIcPublicationPayload AIcFreeze(AIcPublicationPayload aRoot, Path aDirectory) throws Exception {
        Path locFrozen = aDirectory.resolve("payloads").resolve(UUID.randomUUID().toString()).toAbsolutePath();
        List<AIcPublicationPayloadFile> locFiles = new ArrayList<>();
        for (AIcPublicationPayloadFile locFile : aRoot.files()) {
            if (!"file".equalsIgnoreCase(locFile.contentUri().getScheme())) {
                locFiles.add(locFile);
                continue;
            }
            Path locTarget = locFrozen.resolve(locFile.logicalName()).normalize();
            if (!locTarget.startsWith(locFrozen)) throw new IllegalArgumentException("Invalid payload filename.");
            Files.createDirectories(locTarget.getParent());
            Files.copy(locFile.path(), locTarget);
            locFiles.add(new AIcPublicationPayloadFile(locTarget, locFile.logicalName()));
        }
        return new AIcPublicationPayload(aRoot.outputKind(), aRoot.stability(), aRoot.artifactIdentity(), aRoot.version(), locFiles, aRoot.coordinates());
    }

    private static AIcPublicationPayload AIcForm(AIcPublicationPayload aRoot, Map<String, Object> aForm,
            AIcPublicationEndpoint aEndpoint, boolean aIncludePom, Map<String, Object> aContext) throws Exception {
        Map<String, String> locCoordinates = new LinkedHashMap<>(aRoot.coordinates());
        List<AIcPublicationPayloadFile> locFiles = new ArrayList<>();
        String locPublishedVersion = aRoot.version();
        boolean locJava = locCoordinates.getOrDefault("technologyKind", "").equals("java")
                || aEndpoint.publicationAdapter().startsWith("maven-");
        String locClassifier = Objects.toString(AIcValue(aForm, "Classifier", "classifier"), locCoordinates.getOrDefault("classifier", ""));
        String locExtension = Objects.toString(AIcValue(aForm, "Extension", "extension"), locCoordinates.getOrDefault("extension", "jar"));
        if ((aForm.containsKey("Classifier") || aForm.containsKey("Extension")) && !locJava) {
            throw new IllegalArgumentException("Classifier/Extension requires a Maven-compatible publication form.");
        }
        if (locJava && aRoot.stability() == AInPublicationStability.SNAPSHOT && "maven-repository".equals(aEndpoint.publicationAdapter())) {
            String locBase = locCoordinates.getOrDefault("version", Objects.toString(aRoot.version(), ""));
            if (!locBase.endsWith("-SNAPSHOT")) throw new IllegalArgumentException("Maven snapshot requires -SNAPSHOT logical version.");
            @SuppressWarnings("unchecked") Map<String, Object> locInvocation = (Map<String, Object>) aContext.getOrDefault("Invocation", Map.of());
            Instant locNow = Instant.parse(Objects.toString(locInvocation.getOrDefault("StartedAt", Instant.now().toString())));
            String locStamp = DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss").withZone(ZoneOffset.UTC).format(locNow);
            String locNumber = Long.toString(locNow.getNano() + 1L);
            locPublishedVersion = locBase.substring(0, locBase.length() - 9) + "-" + locStamp + "-" + locNumber;
            locCoordinates.put("snapshotTimestamp", locStamp);
            locCoordinates.put("snapshotBuildNumber", locNumber);
        }
        for (AIcPublicationPayloadFile locFile : aRoot.files()) {
            if (locJava && locFile.logicalName().endsWith(".module")) {
                if (aIncludePom) {
                    String locModuleName = locCoordinates.get("artifactId") + "-" + locPublishedVersion + ".module";
                    URI locContent = locFile.contentUri();
                    if (!Objects.equals(locPublishedVersion, aRoot.version())) {
                        Path locModule = locFile.path().resolveSibling(UUID.randomUUID() + ".module");
                        String locText = Files.readString(locFile.path());
                        String locOldPrefix = locCoordinates.get("artifactId") + "-" + aRoot.version();
                        String locNewPrefix = locCoordinates.get("artifactId") + "-" + locPublishedVersion;
                        /* Gradle-generated artifact names and URLs are JSON strings; component and dependency versions remain logical. */
                        java.util.regex.Pattern locPattern = java.util.regex.Pattern.compile(
                                "(\\\"(?:name|url)\\\"\\s*:\\s*\\\")" + java.util.regex.Pattern.quote(locOldPrefix));
                        locText = locPattern.matcher(locText).replaceAll(
                                "$1" + java.util.regex.Matcher.quoteReplacement(locNewPrefix));
                        if (aForm.containsKey("Classifier") || aForm.containsKey("Extension")) {
                            String locOriginalClassifier = locCoordinates.getOrDefault("classifier", "");
                            String locOriginalExtension = locCoordinates.getOrDefault("extension", "jar");
                            String locOriginalName = locNewPrefix + (locOriginalClassifier.isEmpty() ? "" : "-" + locOriginalClassifier) + "." + locOriginalExtension;
                            String locPublishedName = locNewPrefix + (locClassifier.isEmpty() ? "" : "-" + locClassifier) + "." + locExtension;
                            locText = locText.replace("\"" + locOriginalName + "\"", "\"" + locPublishedName + "\"");
                        }
                        Files.writeString(locModule, locText);
                        locContent = locModule.toUri();
                    }
                    locFiles.add(new AIcPublicationPayloadFile(locContent, locModuleName));
                }
                continue;
            }
            if (locFile.logicalName().endsWith(".pom")) {
                if (aIncludePom) locFiles.add(new AIcPublicationPayloadFile(locFile.contentUri(), locJava
                        ? locCoordinates.get("artifactId") + "-" + locPublishedVersion + ".pom" : locFile.logicalName()));
                continue;
            }
            String locName = locFile.logicalName();
            if (locJava && (aForm.containsKey("Classifier") || aForm.containsKey("Extension"))) {
                locName = locCoordinates.get("artifactId") + "-" + locPublishedVersion
                        + (locClassifier.isEmpty() ? "" : "-" + locClassifier) + "." + locExtension;
            } else if (locJava && !Objects.equals(locPublishedVersion, aRoot.version())) {
                locName = locName.replace(Objects.toString(aRoot.version(), ""), locPublishedVersion);
            }
            locFiles.add(new AIcPublicationPayloadFile(locFile.contentUri(), locName));
        }
        if (locJava) {
            locCoordinates.put("classifier", locClassifier);
            locCoordinates.put("extension", locExtension);
        }
        return new AIcPublicationPayload(aRoot.outputKind(), aRoot.stability(), aRoot.artifactIdentity(), locPublishedVersion, locFiles, locCoordinates);
    }

    private static AIcPublicationEndpoint AIcWithId(AIcPublicationEndpoint aEndpoint, String aId) {
        return new AIcPublicationEndpoint(aId, aEndpoint.executionEnabled(), aEndpoint.publicationUri(), aEndpoint.publicationAdapter(),
                aEndpoint.publicationCredentialProfile(), aEndpoint.executionOrder(), aEndpoint.executionFailurePolicy(),
                aEndpoint.publicationRetryCount(), aEndpoint.publicationWaitForNextAttemptMillis(), aEndpoint.publicationAttemptTimeoutMillis(),
                aEndpoint.showPublicationProgressIfPossible(), aEndpoint.configuration());
    }

    static Object AIcValue(Map<String, Object> aMap, String... aKeys) {
        for (String locKey : aKeys) if (aMap.containsKey(locKey)) return aMap.get(locKey);
        return null;
    }
    static Object AIcValueOrDefault(Map<String, Object> aMap, Object aDefault, String aFieldName) {
        Object locValue = AIcValue(aMap, aFieldName);
        return locValue == null ? aDefault : locValue;
    }

    static Object AIcValueOrDefault(
            Map<String, Object> aMap, Object aDefault, String aFieldName, String aAlternativeFieldName) {
        Object locValue = AIcValue(aMap, aFieldName, aAlternativeFieldName);
        return locValue == null ? aDefault : locValue;
    }

    static Object AIcValueOrDefault(Map<String, Object> aMap, Object aDefault, String... aKeys) {
        Object locValue = AIcValue(aMap, aKeys);
        return locValue == null ? aDefault : locValue;
    }
    static int AIcInt(Map<String, Object> aMap, int aDefault, String... aKeys) {
        Object locValue = AIcValue(aMap, aKeys); return locValue == null ? aDefault : ((Number) locValue).intValue();
    }
    static long AIcLong(Map<String, Object> aMap, long aDefault, String... aKeys) {
        Object locValue = AIcValue(aMap, aKeys); return locValue == null ? aDefault : ((Number) locValue).longValue();
    }
    static Long AIcNullableLong(Map<String, Object> aMap, String... aKeys) {
        Object locValue = AIcValue(aMap, aKeys); return locValue == null ? null : ((Number) locValue).longValue();
    }
    @SuppressWarnings("unchecked")
    static Map<String, Object> AIcObjectMap(Object aValue) {
        return aValue instanceof Map<?, ?> ? Map.copyOf((Map<String, Object>) aValue) : Map.of();
    }
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> AIcMaps(Object aValue) {
        if (aValue == null) return List.of();
        if (!(aValue instanceof List<?> locList)) throw new IllegalArgumentException("Expected a list.");
        return locList.stream().map(locItem -> {
            if (!(locItem instanceof Map<?, ?> locMap)) throw new IllegalArgumentException("Expected an object item.");
            return (Map<String, Object>) locMap;
        }).toList();
    }
}
