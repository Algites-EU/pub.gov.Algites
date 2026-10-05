package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Converts effective OutputPublications endpoint/forms into root publication jobs with recursive action trees. */
public final class AIcPublicationPlanner {
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
        Map<String, AIcPublicationEndpoint> locRegistry = new LinkedHashMap<>();
        Object locRegistryValue = AIcValue(aConfiguration, "PublicationEndpointRegistry", "publicationEndpointRegistry");
        for (Map<String, Object> locRegistryDeclaration : AIcMaps(locRegistryValue)) {
            String locRegistryId = Objects.toString(AIcValue(locRegistryDeclaration, "Id", "id"), "");
            AIcPublicationEndpoint locRegistryEndpoint = endpoint(locRegistryDeclaration, locRegistryId);
            locRegistry.put(locRegistryId, locRegistryEndpoint);
        }
        Object locEndpointsValue = AIcValue(aConfiguration, "PublicationEndpoints", "publicationEndpoints");
        for (Map<String, Object> locDeclaration : AIcMaps(locEndpointsValue)) {
            AIcPublicationEndpoint locEndpoint = endpoint(locDeclaration, Objects.toString(AIcValue(locDeclaration, "Id", "id"), ""));
            locRegistry.putIfAbsent(locEndpoint.id(), locEndpoint);
            if (!locEndpoint.enabled()) continue;
            Object locFormsValue = AIcValue(locDeclaration, "Publications", "publications");
            List<Map<String, Object>> locForms = locFormsValue == null ? List.of(Map.of("Id", "standard")) : AIcMaps(locFormsValue);
            int locIndex = 0;
            for (Map<String, Object> locForm : locForms) {
                if (Boolean.FALSE.equals(AIcValue(locForm, "Enabled", "enabled"))) continue;
                String locLocalId = Objects.toString(AIcValue(locForm, "Id", "id"), "standard");
                String locId = locEndpoint.id() + "/" + locLocalId;
                if (!locIds.add(locId)) throw new IllegalArgumentException("Duplicate publication path " + locId);
                AIcPublicationPayload locPayload = AIcForm(locFrozen, locForm, locEndpoint, locIndex++ == 0, aContext);
                for (AIcPublicationPayloadFile locFile : locPayload.files()) {
                    String locTarget = locEndpoint.publicationUri() + "|" + locFile.logicalName();
                    if (!locTargets.add(locTarget)) throw new IllegalArgumentException("Duplicate publication target " + locFile.logicalName());
                }
                List<AIcPostPublicationAction> locActions = AIcActions(AIcValue(locForm, "PostPublicationActions", "postPublicationActions"), 0);
                if (locActions.stream().noneMatch(locAction -> "build-record".equals(locAction.id()))) {
                    ArrayList<AIcPostPublicationAction> locWithDefault = new ArrayList<>(locActions);
                    locWithDefault.add(new AIcPostPublicationAction("build-record", true, "modustro-build-record", null, 0,
                            AInPostPublicationActionFailurePolicy.FAIL_BUILD_ON_FAILURE, 0, 1000L, null, true, Map.of(), List.of()));
                    locActions = List.copyOf(locWithDefault);
                }
                locJobs.add(new AIcPublicationJob(locId, AIcWithId(locEndpoint, locId), locPayload, aContext, locActions, locRegistry));
            }
        }
        return List.copyOf(locJobs);
    }

    public static AIcPublicationEndpoint endpoint(Map<String, Object> aItem, String aId) {
        String locUri = Objects.toString(AIcValue(aItem, "PublicationUri", "publicationUri"), null);
        String locAdapter = Objects.toString(AIcValue(aItem, "PublicationAdapter", "publicationAdapter"), null);
        return new AIcPublicationEndpoint(
                aId,
                !Boolean.FALSE.equals(AIcValueOrDefault(aItem, true, "Enabled", "enabled")),
                locUri == null ? null : URI.create(locUri),
                locAdapter,
                Objects.toString(AIcValue(aItem, "PublicationCredentialProfile", "publicationCredentialProfile"), null),
                AIcInt(aItem, 0, "PublicationOrder", "publicationOrder"),
                AInPublicationFailurePolicy.valueOf(Objects.toString(AIcValueOrDefault(aItem,
                        "FAIL_BUILD_ON_PUBLICATION_FAILURE", "PublicationFailurePolicy", "publicationFailurePolicy"))),
                AIcInt(aItem, 0, "PublicationRetryCount", "publicationRetryCount"),
                AIcLong(aItem, 1000L, "PublicationWaitForNextAttemptMillis", "publicationWaitForNextAttemptMillis"),
                AIcNullableLong(aItem, "PublicationAttemptTimeoutMillis", "publicationAttemptTimeoutMillis"),
                !Boolean.FALSE.equals(AIcValueOrDefault(aItem, true, "ShowPublicationProgressIfPossible", "showPublicationProgressIfPossible")),
                AIcObjectMap(AIcValue(aItem, "Configuration", "configuration")));
    }

    private static List<AIcPostPublicationAction> AIcActions(Object aValue, int aDepth) {
        if (aDepth > 64) throw new IllegalArgumentException("PostPublicationActions nesting exceeds 64.");
        List<AIcPostPublicationAction> locResult = new ArrayList<>();
        Set<String> locIds = new HashSet<>();
        for (Map<String, Object> locItem : AIcMaps(aValue)) {
            String locId = Objects.toString(AIcValue(locItem, "Id", "id"), "");
            if (!locId.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || !locIds.add(locId)) {
                throw new IllegalArgumentException("Post-publication action Id must be unique among siblings.");
            }
            locResult.add(new AIcPostPublicationAction(
                    locId,
                    !Boolean.FALSE.equals(AIcValueOrDefault(locItem, true, "Enabled", "enabled")),
                    Objects.toString(AIcValue(locItem, "PostPublicationActionAdapter", "postPublicationActionAdapter"), null),
                    Objects.toString(AIcValue(locItem, "TargetPublicationEndpointId", "targetPublicationEndpointId"), null),
                    AIcInt(locItem, 0, "Order", "order"),
                    AInPostPublicationActionFailurePolicy.valueOf(Objects.toString(AIcValueOrDefault(locItem,
                            "FAIL_BUILD_ON_FAILURE", "FailurePolicy", "failurePolicy"))),
                    AIcInt(locItem, 0, "RetryCount", "retryCount"),
                    AIcLong(locItem, 1000L, "WaitForNextAttemptMillis", "waitForNextAttemptMillis"),
                    AIcNullableLong(locItem, "AttemptTimeoutMillis", "attemptTimeoutMillis"),
                    !Boolean.FALSE.equals(AIcValueOrDefault(locItem, true, "ShowProgressIfPossible", "showProgressIfPossible")),
                    AIcObjectMap(AIcValue(locItem, "Configuration", "configuration")),
                    AIcActions(AIcValue(locItem, "PostPublicationActions", "postPublicationActions"), aDepth + 1)));
        }
        return List.copyOf(locResult);
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
            AIcPublicationEndpoint aEndpoint, boolean aIncludePom, Map<String, Object> aContext) {
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
        return new AIcPublicationEndpoint(aId, aEndpoint.enabled(), aEndpoint.publicationUri(), aEndpoint.publicationAdapter(),
                aEndpoint.publicationCredentialProfile(), aEndpoint.publicationOrder(), aEndpoint.publicationFailurePolicy(),
                aEndpoint.publicationRetryCount(), aEndpoint.publicationWaitForNextAttemptMillis(), aEndpoint.publicationAttemptTimeoutMillis(),
                aEndpoint.showPublicationProgressIfPossible(), aEndpoint.configuration());
    }

    private static Object AIcValue(Map<String, Object> aMap, String... aKeys) {
        for (String locKey : aKeys) if (aMap.containsKey(locKey)) return aMap.get(locKey);
        return null;
    }
    private static Object AIcValueOrDefault(Map<String, Object> aMap, Object aDefault, String... aKeys) {
        Object locValue = AIcValue(aMap, aKeys); return locValue == null ? aDefault : locValue;
    }
    private static int AIcInt(Map<String, Object> aMap, int aDefault, String... aKeys) {
        Object locValue = AIcValue(aMap, aKeys); return locValue == null ? aDefault : ((Number) locValue).intValue();
    }
    private static long AIcLong(Map<String, Object> aMap, long aDefault, String... aKeys) {
        Object locValue = AIcValue(aMap, aKeys); return locValue == null ? aDefault : ((Number) locValue).longValue();
    }
    private static Long AIcNullableLong(Map<String, Object> aMap, String... aKeys) {
        Object locValue = AIcValue(aMap, aKeys); return locValue == null ? null : ((Number) locValue).longValue();
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> AIcObjectMap(Object aValue) {
        return aValue instanceof Map<?, ?> ? Map.copyOf((Map<String, Object>) aValue) : Map.of();
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> AIcMaps(Object aValue) {
        if (aValue == null) return List.of();
        if (!(aValue instanceof List<?> locList)) throw new IllegalArgumentException("Expected a list.");
        return locList.stream().map(locItem -> {
            if (!(locItem instanceof Map<?, ?> locMap)) throw new IllegalArgumentException("Expected an object item.");
            return (Map<String, Object>) locMap;
        }).toList();
    }
}
