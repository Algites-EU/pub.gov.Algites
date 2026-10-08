package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputTypeGroup;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationOutputKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Core-owned sparse descriptor projection. Expand each hierarchy layer before merging layers. */
public final class AIcPublicationConfiguration {
    private static final String SCHEMA_FIELD_NAME__EXECUTION_FAILURE_POLICY = "ExecutionFailurePolicy";
    private static final Set<String> BOOLEAN_PROPERTIES = Set.of(
            "ExecutionEnabled", "PublicationEnabled", "ShowPublicationProgressIfPossible", "ShowProgressIfPossible");
    private static final Set<String> INTEGER_PROPERTIES = Set.of(
            "ExecutionOrder", "PublicationRetryCount", "PublicationWaitForNextAttemptMillis", "PublicationAttemptTimeoutMillis",
            "RetryCount", "WaitForNextAttemptMillis", "AttemptTimeoutMillis");
    private static final Set<String> DIRECT_PROPERTIES = Set.of(
            "Id", "OutputSelector", "TechnologyKind", "ExecutionEnabled", "PublicationEnabled", "Classifier", "Extension",
            "PublicationFinalizationActionAdapter", "FinalizationActionAdapter",
            "OutputPublicationFinalizationActionAdapter", "ArtifactPublicationFinalizationActionAdapter",
            "VersionScopePublicationFinalizationActionAdapter", "TargetPublicationEndpointId", "ExecutionOrder", SCHEMA_FIELD_NAME__EXECUTION_FAILURE_POLICY,
            "RetryCount", "WaitForNextAttemptMillis", "AttemptTimeoutMillis", "ShowProgressIfPossible",
            "PublicationUri", "PublicationAdapter", "PublicationCredentialProfile",
            "PublicationRetryCount", "PublicationWaitForNextAttemptMillis",
            "PublicationAttemptTimeoutMillis", "ShowPublicationProgressIfPossible");
    private static final Set<String> CHILD_LIST_PROPERTIES = Set.of(
            "Publications", "PublicationFinalizationActions", "FinalizationActions",
            "OutputPublicationFinalizationActions", "ArtifactPublicationFinalizationActions",
            "VersionScopePublicationFinalizationActions");

    private AIcPublicationConfiguration() { }

    public static List<String> outputs(String aSelector) {
        for (AInBuildOutputTypeGroup locGroup : AInBuildOutputTypeGroup.values()) {
            if (locGroup.descriptorName().equals(aSelector)) return locGroup.outputs();
        }
        for (AInPublicationOutputKind locKind : AInPublicationOutputKind.values()) {
            if (locKind.descriptorName().equals(aSelector)) return List.of(aSelector);
        }
        throw new IllegalArgumentException("Unknown OutputSelector '" + aSelector + "'.");
    }

    public static int rank(String aSelector) {
        List<String> locOutputs = outputs(aSelector);
        return aSelector.equals("native_outputs") ? 0 : locOutputs.size() > 1 ? 1 : 2;
    }

    public static List<Map<String, Object>> list(Map<String, String> aValues, String aPrefix) {
        if (aValues.containsKey(aPrefix)) {
            if ("[]".equals(aValues.get(aPrefix))) return List.of();
            throw new IllegalArgumentException(aPrefix + " must be a list.");
        }
        TreeSet<Integer> locIndices = new TreeSet<>();
        for (String locKey : aValues.keySet()) {
            if (!locKey.startsWith(aPrefix + ".")) continue;
            String locFirst = locKey.substring(aPrefix.length() + 1).split("\\.")[0];
            try { locIndices.add(Integer.parseInt(locFirst)); } catch (NumberFormatException ignored) { }
        }
        List<Map<String, Object>> locResult = new ArrayList<>();
        Set<String> locIds = new HashSet<>();
        for (int locIndex : locIndices) {
            String locPrefix = aPrefix + "." + locIndex + ".";
            Map<String, Object> locItem = new LinkedHashMap<>();
            for (Map.Entry<String, String> locEntry : aValues.entrySet()) {
                if (!locEntry.getKey().startsWith(locPrefix)) continue;
                String locProperty = locEntry.getKey().substring(locPrefix.length());
                if (locProperty.contains(".")) continue;
                if (CHILD_LIST_PROPERTIES.contains(locProperty) || "Configuration".equals(locProperty)) continue;
                if (!DIRECT_PROPERTIES.contains(locProperty)) throw new IllegalArgumentException("Unknown publication property " + locPrefix + locProperty);
                Object locTyped = AIcTyped(locEntry.getValue(), locProperty, locPrefix);
                locItem.put(locProperty, locTyped);
            }
            String locConfigurationPrefix = locPrefix + "Configuration.";
            LinkedHashMap<String, Object> locConfiguration = new LinkedHashMap<>();
            for (Map.Entry<String, String> locEntry : aValues.entrySet()) {
                if (locEntry.getKey().startsWith(locConfigurationPrefix)) {
                    String locKey = locEntry.getKey().substring(locConfigurationPrefix.length());
                    if (!locKey.isBlank()) locConfiguration.put(locKey, locEntry.getValue());
                }
            }
            if (!locConfiguration.isEmpty()) locItem.put("Configuration", Collections.unmodifiableMap(locConfiguration));
            for (String locChild : CHILD_LIST_PROPERTIES) {
                String locChildPrefix = locPrefix + locChild;
                if (aValues.containsKey(locChildPrefix) || aValues.keySet().stream().anyMatch(locKey -> locKey.startsWith(locChildPrefix + "."))) {
                    locItem.put(locChild, list(aValues, locChildPrefix));
                }
            }
            if (!"OutputPublications".equals(aPrefix)) {
                Object locId = locItem.get("Id");
                if (locId == null || !locId.toString().matches("[a-z0-9]+(?:-[a-z0-9]+)*") || !locIds.add(locId.toString())) {
                    throw new IllegalArgumentException(aPrefix + " requires unique lowercase dash-separated Id values.");
                }
            }
            locResult.add(Collections.unmodifiableMap(locItem));
        }
        return List.copyOf(locResult);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> merge(List<Map<String, Object>> aBase, List<Map<String, Object>> aOverride) {
        if (aOverride == null) return aBase;
        if (aOverride.isEmpty()) return List.of();
        LinkedHashMap<String, Map<String, Object>> locItems = new LinkedHashMap<>();
        if (aBase != null) for (Map<String, Object> locItem : aBase) locItems.put(locItem.get("Id").toString(), locItem);
        for (Map<String, Object> locItem : aOverride) {
            String locId = locItem.get("Id").toString();
            Map<String, Object> locValue = new LinkedHashMap<>(locItems.getOrDefault(locId, Map.of()));
            for (Map.Entry<String, Object> locEntry : locItem.entrySet()) {
                if (CHILD_LIST_PROPERTIES.contains(locEntry.getKey())) {
                    locValue.put(locEntry.getKey(), merge(
                            (List<Map<String, Object>>) locValue.get(locEntry.getKey()),
                            (List<Map<String, Object>>) locEntry.getValue()));
                } else {
                    locValue.put(locEntry.getKey(), locEntry.getValue());
                }
            }
            locItems.put(locId, Collections.unmodifiableMap(locValue));
        }
        return List.copyOf(locItems.values());
    }

    public static Map<String, String> expand(Map<String, String> aValues) {
        LinkedHashMap<String, Map<String, Object>> locPolicies = new LinkedHashMap<>();
        TreeSet<Integer> locIndices = new TreeSet<>();
        for (String locKey : aValues.keySet()) {
            if (locKey.matches("OutputPublications\\.\\d+\\.OutputSelector")) {
                locIndices.add(Integer.parseInt(locKey.split("\\.")[1]));
            }
        }
        List<Integer> locOrdered = new ArrayList<>(locIndices);
        locOrdered.sort(Comparator.comparingInt(locIndex -> rank(aValues.get("OutputPublications." + locIndex + ".OutputSelector"))));
        for (int locIndex : locOrdered) {
            String locPrefix = "OutputPublications." + locIndex;
            String locTechnology = aValues.get(locPrefix + ".TechnologyKind");
            if (locTechnology == null || locTechnology.isBlank()) throw new IllegalArgumentException(locPrefix + " requires TechnologyKind.");
            if (!Set.of("java", "python", "mps", "modustro").contains(locTechnology)) throw new IllegalArgumentException("Invalid TechnologyKind " + locTechnology);
            for (String locOutput : outputs(aValues.get(locPrefix + ".OutputSelector"))) {
                AIcApply(locPolicies, locTechnology + "." + locOutput,
                        AIcBranch(aValues, locPrefix + ".Snapshot"), AIcBranch(aValues, locPrefix + ".Release"));
            }
        }
        Map<String, String> locResult = new LinkedHashMap<>(aValues);
        for (Map.Entry<String, Map<String, Object>> locEntry : locPolicies.entrySet()) {
            AIcFlatten(locResult, locEntry.getKey(), locEntry.getValue());
        }
        return locResult;
    }

    private static Object AIcTyped(String aValue, String aProperty, String aPrefix) {
        if (BOOLEAN_PROPERTIES.contains(aProperty)) {
            if (!"true".equals(aValue) && !"false".equals(aValue)) throw new IllegalArgumentException(aPrefix + aProperty + " must be boolean.");
            return Boolean.valueOf(aValue);
        }
        if (INTEGER_PROPERTIES.contains(aProperty)) {
            long locNumber = Long.parseLong(aValue);
            if (!Set.of("ExecutionOrder").contains(aProperty)
                    && (locNumber < 0 || Set.of("PublicationAttemptTimeoutMillis", "AttemptTimeoutMillis").contains(aProperty) && locNumber == 0)) {
                throw new IllegalArgumentException(aPrefix + aProperty + " out of range.");
            }
            return locNumber;
        }
        return aValue;
    }

    private static Map<String, Object> AIcBranch(Map<String, String> aValues, String aPrefix) {
        Map<String, Object> locBranch = new LinkedHashMap<>();
        String locEnabled = aValues.get(aPrefix + ".PublicationEnabled");
        if (locEnabled != null) {
            if (!"true".equals(locEnabled) && !"false".equals(locEnabled)) throw new IllegalArgumentException(aPrefix + " PublicationEnabled must be boolean.");
            locBranch.put("PublicationEnabled", Boolean.valueOf(locEnabled));
        }
        for (String locChild : List.of("PublicationEndpoints", "OutputPublicationFinalizationActions")) {
            String locPath = aPrefix + "." + locChild;
            if (aValues.containsKey(locPath) || aValues.keySet().stream().anyMatch(locKey -> locKey.startsWith(locPath + "."))) {
                locBranch.put(locChild, list(aValues, locPath));
            }
        }
        return locBranch;
    }

    private static void AIcApply(Map<String, Map<String, Object>> aPolicies, String aOutput,
            Map<String, Object> aSnapshot, Map<String, Object> aRelease) {
        if (aSnapshot.isEmpty() && aRelease.isEmpty()) return;
        Map<String, Object> locPolicy = aPolicies.computeIfAbsent(aOutput, aIgnored -> new LinkedHashMap<>());
        AIcMergeBranch(locPolicy, "Snapshot", aSnapshot);
        AIcMergeBranch(locPolicy, "Release", aRelease);
    }

    @SuppressWarnings("unchecked")
    private static void AIcMergeBranch(Map<String, Object> aPolicy, String aKey, Map<String, Object> aChange) {
        Map<String, Object> locBranch = new LinkedHashMap<>((Map<String, Object>) aPolicy.getOrDefault(aKey, Map.of()));
        if (aChange.containsKey("PublicationEnabled")) locBranch.put("PublicationEnabled", aChange.get("PublicationEnabled"));
        for (String locChild : List.of("PublicationEndpoints", "OutputPublicationFinalizationActions")) {
            if (aChange.containsKey(locChild)) {
                locBranch.put(locChild, merge((List<Map<String, Object>>) locBranch.get(locChild), (List<Map<String, Object>>) aChange.get(locChild)));
            }
        }
        aPolicy.put(aKey, locBranch);
    }

    private static void AIcFlatten(Map<String, String> aValues, String aPrefix, Object aValue) {
        if (aValue instanceof Map<?, ?> locMap) {
            for (Map.Entry<?, ?> locEntry : locMap.entrySet()) AIcFlatten(aValues, aPrefix + "." + locEntry.getKey(), locEntry.getValue());
        } else if (aValue instanceof List<?> locList) {
            aValues.keySet().removeIf(locKey -> locKey.equals(aPrefix) || locKey.startsWith(aPrefix + "."));
            if (locList.isEmpty()) aValues.put(aPrefix, "[]");
            else for (int locIndex = 0; locIndex < locList.size(); locIndex++) AIcFlatten(aValues, aPrefix + "." + locIndex, locList.get(locIndex));
        } else {
            aValues.put(aPrefix, Objects.toString(aValue, ""));
        }
    }
}
