package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.*;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes snapshot package versions corresponding to a successfully published release. */
public final class AIcRemoveCorrespondingSnapshotsVersionScopePublicationFinalizationActionAdapter implements AIiVersionScopePublicationFinalizationActionAdapter {
    public static final String ADAPTER_ID = "modustro-remove-corresponding-snapshots";
    private static final Pattern CLOUDSMITH_IDENTIFIER = Pattern.compile("\\\"(?:slug_perm|identifier_perm)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern REPSY_VERSION = Pattern.compile("\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    @Override
    public String adapterId() {
        return ADAPTER_ID;
    }

    @Override
    public boolean isRetrySafe(AIcVersionScopePublicationFinalizationActionAttemptContext aContext) {
        return true;
    }

    @Override
    public AIcPublicationFinalizationActionResult execute(AIcVersionScopePublicationFinalizationActionAttemptContext aContext) throws Exception {
        Objects.requireNonNull(aContext, "context");
        if (aContext.versionScopeExecution().stability() != AInPublicationStability.RELEASE) {
            throw new IllegalArgumentException("Corresponding-snapshot cleanup may run only after a release Version Scope publication.");
        }
        aContext.progressReporter().started("Resolving corresponding snapshot targets for completed Version Scope release.");
        LinkedHashSet<String> locSeen = new LinkedHashSet<>();
        ArrayList<Map<String, Object>> locOperations = new ArrayList<>();
        int locDeletedTotal = 0;
        for (AIcArtifactPublicationExecutionResult locArtifact : aContext.versionScopeExecution().artifacts()) {
            for (AIcOutputPublicationExecutionResult locOutput : locArtifact.outputs()) {
                if (locOutput.stability() != AInPublicationStability.RELEASE || !locOutput.success() || locOutput.publications().isEmpty()) {
                    continue;
                }
                AIcPublicationPayload locPayload = locOutput.publications().get(0).payload();
                Map<String, String> locCoordinates = locPayload.coordinates();
                String locTechnologyKind = AIcRequired(locCoordinates, "technologyKind").toLowerCase(Locale.ROOT);
                if (!locTechnologyKind.equals("java") && !locTechnologyKind.equals("python")) {
                    continue;
                }
                String locGroupId = AIcRequired(locCoordinates, "groupId");
                String locArtifactId = AIcRequired(locCoordinates, "artifactId");
                String locLogicalReleaseVersion = locCoordinates.getOrDefault("logicalVersion",
                        locCoordinates.getOrDefault("version", locOutput.version()));
                if (locLogicalReleaseVersion == null || locLogicalReleaseVersion.isBlank() || locLogicalReleaseVersion.endsWith("-SNAPSHOT")) {
                    throw new IllegalArgumentException("Corresponding-snapshot cleanup requires a concrete release version.");
                }
                for (AIcPublicationEndpoint locTarget : locOutput.snapshotPublicationEndpoints().values()) {
                    if (!locTarget.executionEnabled()) continue;
                    String locKey = locTarget.publicationUri() + "|" + locTechnologyKind + "|" + locGroupId + "|"
                            + locArtifactId + "|" + locLogicalReleaseVersion;
                    if (!locSeen.add(locKey)) continue;
                    Map<String, String> locCredentials = aContext.credentialsFor(locTarget);
                    int locDeleted = AIcDeleteOne(aContext, locTarget, locCredentials, locTechnologyKind, locGroupId,
                            locArtifactId, locLogicalReleaseVersion);
                    locDeletedTotal += locDeleted;
                    locOperations.add(Map.of(
                            "TargetPublicationEndpointId", locTarget.id(),
                            "TechnologyKind", locTechnologyKind,
                            "GroupId", locGroupId,
                            "ArtifactId", locArtifactId,
                            "ReleaseVersion", locLogicalReleaseVersion,
                            "DeletedPackageVersions", locDeleted));
                }
            }
        }
        aContext.progressReporter().completed("Corresponding-snapshot cleanup completed; deleted " + locDeletedTotal + " package version(s).");
        return new AIcPublicationFinalizationActionResult(
                aContext.action().id(), true, true, false, 1, Duration.ZERO, null,
                Map.of("DeletedPackageVersions", locDeletedTotal, "Operations", List.copyOf(locOperations)), null);
    }

    private static int AIcDeleteOne(
            AIcVersionScopePublicationFinalizationActionAttemptContext aContext,
            AIcPublicationEndpoint aTarget,
            Map<String, String> aCredentials,
            String aTechnologyKind,
            String aGroupId,
            String aArtifactId,
            String aLogicalReleaseVersion) throws Exception {
        String locPackageName;
        String locVersionSelector;
        boolean locVersionPrefix;
        String locFormat;
        switch (aTechnologyKind) {
            case "java" -> {
                locPackageName = aArtifactId;
                locVersionSelector = aLogicalReleaseVersion + "-SNAPSHOT";
                locVersionPrefix = false;
                locFormat = "maven";
            }
            case "python" -> {
                locPackageName = AIcPythonDistributionName(aGroupId, aArtifactId);
                locVersionSelector = aLogicalReleaseVersion.replace('-', '.') + ".dev";
                locVersionPrefix = true;
                locFormat = "python";
            }
            default -> throw new IllegalArgumentException("Unsupported TechnologyKind '" + aTechnologyKind + "'.");
        }
        String locProvider = AIcProvider(aTarget);
        return switch (locProvider) {
            case "cloudsmith" -> AIcDeleteCloudsmith(aContext, aTarget, aCredentials, locPackageName, locVersionSelector, locVersionPrefix, locFormat);
            case "repsy" -> AIcDeleteRepsy(aContext, aTarget, aCredentials, locPackageName, locVersionSelector, locVersionPrefix, locFormat, aGroupId);
            default -> throw new IllegalArgumentException("Unsupported corresponding-snapshot cleanup provider '" + locProvider + "'.");
        };
    }

    private static int AIcDeleteCloudsmith(
            AIcVersionScopePublicationFinalizationActionAttemptContext aContext,
            AIcPublicationEndpoint aEndpoint,
            Map<String, String> aCredentials,
            String aPackageName,
            String aVersionSelector,
            boolean aVersionPrefix,
            String aFormat) throws Exception {
        List<String> locSegments = AIcPathSegments(aEndpoint.publicationUri());
        String locOwner = AIcConfigurationString(aEndpoint, "Workspace", locSegments.size() > 0 ? locSegments.get(0) : null);
        String locRepository = AIcConfigurationString(aEndpoint, "Repository", locSegments.size() > 1 ? locSegments.get(1) : null);
        if (locOwner == null || locRepository == null) {
            throw new IllegalArgumentException(
                    "Cloudsmith snapshot endpoint '" + aEndpoint.id() + "' must identify workspace/repository in PublicationUri or Configuration.");
        }
        URI locManagementRoot = AIcManagementRoot(aEndpoint, URI.create("https://api.cloudsmith.io/v1/packages/"));
        URI locRepositoryRoot = AIcEnsureTrailingSlash(locManagementRoot.resolve(
                AIcPathSegment(locOwner) + "/" + AIcPathSegment(locRepository) + "/"));
        Map<String, String> locHeaders = AIcCloudsmithHeaders(aCredentials);
        String locVersionQuery = aVersionPrefix ? "version:^" + aVersionSelector : "version:^" + aVersionSelector + "$";
        String locQuery = "name:^" + aPackageName + "$ AND " + locVersionQuery + " AND format:" + aFormat;
        int locDeleted = 0;
        int locPage = 1;
        while (true) {
            AIcCheckCancellation(aContext);
            String locQueryString = "page_size=500&page=" + locPage + "&query="
                    + URLEncoder.encode(locQuery, StandardCharsets.UTF_8);
            AIcHttpResponse locResponse = AIcRequest(aContext, "GET", URI.create(locRepositoryRoot + "?" + locQueryString), locHeaders);
            if (locResponse.status() < 200 || locResponse.status() >= 300) {
                throw new IllegalStateException(
                        "Cloudsmith package lookup failed for endpoint '" + aEndpoint.id() + "' with HTTP "
                                + locResponse.status() + ": " + locResponse.body());
            }
            List<String> locIdentifiers = AIcJsonStringValues(locResponse.body(), CLOUDSMITH_IDENTIFIER);
            for (String locIdentifier : locIdentifiers) {
                AIcCheckCancellation(aContext);
                AIcHttpResponse locDelete = AIcRequest(
                        aContext, "DELETE", locRepositoryRoot.resolve(AIcPathSegment(locIdentifier) + "/"), locHeaders);
                if (locDelete.status() != 204 && locDelete.status() != 404) {
                    throw new IllegalStateException(
                            "Cloudsmith package deletion failed for identifier '" + locIdentifier + "' at endpoint '"
                                    + aEndpoint.id() + "' with HTTP " + locDelete.status() + ": " + locDelete.body());
                }
                if (locDelete.status() == 204) {
                    locDeleted++;
                }
            }
            if (locIdentifiers.size() < 500) {
                break;
            }
            locPage++;
        }
        return locDeleted;
    }

    private static int AIcDeleteRepsy(
            AIcVersionScopePublicationFinalizationActionAttemptContext aContext,
            AIcPublicationEndpoint aEndpoint,
            Map<String, String> aCredentials,
            String aPackageName,
            String aVersionSelector,
            boolean aVersionPrefix,
            String aFormat,
            String aGroupId) throws Exception {
        List<String> locSegments = AIcPathSegments(aEndpoint.publicationUri());
        String locRepository = AIcConfigurationString(
                aEndpoint, "Repository", locSegments.isEmpty() ? null : locSegments.get(locSegments.size() - 1));
        if (locRepository == null) {
            throw new IllegalArgumentException(
                    "Repsy snapshot endpoint '" + aEndpoint.id() + "' must identify a repository in PublicationUri or Configuration.Repository.");
        }
        URI locManagementRoot = AIcManagementRoot(aEndpoint, AIcOrigin(aEndpoint.publicationUri()));
        Map<String, String> locHeaders = AIcRepsyHeaders(aCredentials);
        if ("maven".equals(aFormat)) {
            URI locDeleteUri = locManagementRoot.resolve(
                    "api/mvn/artifacts/" + AIcPathSegment(locRepository) + "/" + AIcPathSegment(aGroupId) + "/"
                            + AIcPathSegment(aPackageName) + "/versions/" + AIcPathSegment(aVersionSelector));
            AIcHttpResponse locDelete = AIcRequest(aContext, "DELETE", locDeleteUri, locHeaders);
            if (locDelete.status() == 404) {
                return 0;
            }
            if (locDelete.status() < 200 || locDelete.status() >= 300) {
                throw new IllegalStateException(
                        "Repsy Maven snapshot deletion failed at endpoint '" + aEndpoint.id() + "' with HTTP "
                                + locDelete.status() + ": " + locDelete.body());
            }
            return 1;
        }
        if (!"python".equals(aFormat) || !aVersionPrefix) {
            throw new IllegalArgumentException("Unsupported Repsy cleanup selector for format '" + aFormat + "'.");
        }
        LinkedHashSet<String> locVersions = new LinkedHashSet<>();
        int locPage = 0;
        while (true) {
            AIcCheckCancellation(aContext);
            URI locListUri = URI.create(locManagementRoot.resolve(
                    "api/pypi/packages/" + AIcPathSegment(locRepository) + "/" + AIcPathSegment(aPackageName) + "/versions").toString()
                    + "?q=" + URLEncoder.encode(aVersionSelector, StandardCharsets.UTF_8)
                    + "&page=" + locPage + "&size=100&sort=version,desc");
            AIcHttpResponse locList = AIcRequest(aContext, "GET", locListUri, locHeaders);
            if (locList.status() == 404) {
                break;
            }
            if (locList.status() < 200 || locList.status() >= 300) {
                throw new IllegalStateException(
                        "Repsy Python snapshot lookup failed at endpoint '" + aEndpoint.id() + "' with HTTP "
                                + locList.status() + ": " + locList.body());
            }
            List<String> locRawPageVersions = AIcJsonStringValues(locList.body(), REPSY_VERSION);
            List<String> locPageVersions = locRawPageVersions.stream()
                    .filter(locVersion -> locVersion.startsWith(aVersionSelector))
                    .toList();
            locVersions.addAll(locPageVersions);
            if (locRawPageVersions.size() < 100) {
                break;
            }
            locPage++;
        }
        int locDeleted = 0;
        for (String locVersion : locVersions) {
            AIcCheckCancellation(aContext);
            URI locDeleteUri = locManagementRoot.resolve(
                    "api/pypi/packages/" + AIcPathSegment(locRepository) + "/" + AIcPathSegment(aPackageName)
                            + "/versions/" + AIcPathSegment(locVersion));
            AIcHttpResponse locDelete = AIcRequest(aContext, "DELETE", locDeleteUri, locHeaders);
            if (locDelete.status() == 404) {
                continue;
            }
            if (locDelete.status() < 200 || locDelete.status() >= 300) {
                throw new IllegalStateException(
                        "Repsy Python snapshot deletion failed for version '" + locVersion + "' at endpoint '"
                                + aEndpoint.id() + "' with HTTP " + locDelete.status() + ": " + locDelete.body());
            }
            locDeleted++;
        }
        return locDeleted;
    }

    private static String AIcProvider(AIcPublicationEndpoint aEndpoint) {
        String locConfigured = AIcConfigurationString(aEndpoint, "Provider", null);
        if (locConfigured != null) {
            return locConfigured.toLowerCase(Locale.ROOT);
        }
        String locHost = Objects.toString(aEndpoint.publicationUri().getHost(), "").toLowerCase(Locale.ROOT);
        if (locHost.endsWith("cloudsmith.io")) {
            return "cloudsmith";
        }
        if (locHost.endsWith("repsy.io")) {
            return "repsy";
        }
        throw new IllegalArgumentException(
                "Cannot infer cleanup provider from PublicationUri '" + aEndpoint.publicationUri()
                        + "'. Set target endpoint Configuration.Provider.");
    }

    private static URI AIcManagementRoot(AIcPublicationEndpoint aEndpoint, URI aDefault) {
        String locConfigured = AIcConfigurationString(aEndpoint, "ManagementApiUri", null);
        return AIcEnsureTrailingSlash(locConfigured == null ? aDefault : URI.create(locConfigured));
    }

    private static String AIcConfigurationString(AIcPublicationEndpoint aEndpoint, String aName, String aDefault) {
        Object locValue = aEndpoint.configuration().get(aName);
        if (locValue == null) {
            return aDefault;
        }
        String locText = locValue.toString().trim();
        return locText.isEmpty() ? aDefault : locText;
    }

    private static Map<String, String> AIcCloudsmithHeaders(Map<String, String> aCredentials) {
        String locApiKey = aCredentials.get("apiKey");
        if (locApiKey == null || locApiKey.isBlank()) {
            locApiKey = aCredentials.get("password");
        }
        if (locApiKey != null && !locApiKey.isBlank()) {
            return Map.of("Authorization", "token " + locApiKey);
        }
        String locBearer = aCredentials.get("bearerToken");
        if (locBearer != null && !locBearer.isBlank()) {
            return Map.of("Authorization", "Bearer " + locBearer);
        }
        throw new IllegalArgumentException(
                "Cloudsmith corresponding-snapshot cleanup requires an API key/bearer credential. "
                        + "A basic publication profile may reuse its password as the Cloudsmith API key.");
    }

    private static Map<String, String> AIcRepsyHeaders(Map<String, String> aCredentials) {
        String locUsername = aCredentials.get("username");
        String locPassword = aCredentials.get("password");
        if (locUsername != null && locPassword != null) {
            String locEncoded = Base64.getEncoder().encodeToString(
                    (locUsername + ":" + locPassword).getBytes(StandardCharsets.UTF_8));
            return Map.of("Authorization", "Basic " + locEncoded);
        }
        String locBearer = aCredentials.get("bearerToken");
        if (locBearer != null && !locBearer.isBlank()) {
            return Map.of("Authorization", "Bearer " + locBearer);
        }
        throw new IllegalArgumentException("Repsy corresponding-snapshot cleanup requires basic or bearer credentials.");
    }

    private static AIcHttpResponse AIcRequest(
            AIcVersionScopePublicationFinalizationActionAttemptContext aContext,
            String aMethod,
            URI aUri,
            Map<String, String> aHeaders) throws Exception {
        AIcCheckCancellation(aContext);
        HttpURLConnection locConnection = (HttpURLConnection) aUri.toURL().openConnection();
        locConnection.setRequestMethod(aMethod);
        locConnection.setUseCaches(false);
        locConnection.setInstanceFollowRedirects(true);
        locConnection.setRequestProperty("Accept", "application/json");
        for (Map.Entry<String, String> locHeader : aHeaders.entrySet()) {
            locConnection.setRequestProperty(locHeader.getKey(), locHeader.getValue());
        }
        AIcApplyTimeouts(locConnection, aContext.deadline());
        try {
            int locStatus = locConnection.getResponseCode();
            InputStream locStream = locStatus >= 200 && locStatus < 300
                    ? locConnection.getInputStream() : locConnection.getErrorStream();
            String locBody = locStream == null ? "" : new String(locStream.readAllBytes(), StandardCharsets.UTF_8);
            return new AIcHttpResponse(locStatus, locBody);
        } finally {
            locConnection.disconnect();
        }
    }

    private static void AIcApplyTimeouts(HttpURLConnection aConnection, Instant aDeadline) {
        if (aDeadline == null) {
            aConnection.setConnectTimeout(30_000);
            aConnection.setReadTimeout(60_000);
            return;
        }
        long locMillis = Math.max(1L, Duration.between(Instant.now(), aDeadline).toMillis());
        int locTimeout = (int) Math.min(Integer.MAX_VALUE, locMillis);
        aConnection.setConnectTimeout(locTimeout);
        aConnection.setReadTimeout(locTimeout);
    }

    private static void AIcCheckCancellation(AIcVersionScopePublicationFinalizationActionAttemptContext aContext) throws InterruptedException {
        if (aContext.cancellationToken().isCancellationRequested()) {
            throw new InterruptedException("Version Scope publication finalization attempt cancelled.");
        }
    }

    private static URI AIcOrigin(URI aUri) {
        if (aUri == null || aUri.getScheme() == null || aUri.getAuthority() == null) {
            throw new IllegalArgumentException("PublicationUri must be an absolute HTTP(S) URI.");
        }
        return URI.create(aUri.getScheme() + "://" + aUri.getAuthority() + "/");
    }

    private static URI AIcEnsureTrailingSlash(URI aUri) {
        String locText = aUri.toString();
        return URI.create(locText.endsWith("/") ? locText : locText + "/");
    }

    private static List<String> AIcPathSegments(URI aUri) {
        if (aUri == null || aUri.getPath() == null) {
            return List.of();
        }
        ArrayList<String> locSegments = new ArrayList<>();
        for (String locSegment : aUri.getPath().split("/")) {
            if (!locSegment.isBlank()) {
                locSegments.add(locSegment);
            }
        }
        return List.copyOf(locSegments);
    }

    private static String AIcPathSegment(String aValue) {
        return URLEncoder.encode(aValue, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static List<String> AIcJsonStringValues(String aJson, Pattern aPattern) {
        LinkedHashSet<String> locValues = new LinkedHashSet<>();
        Matcher locMatcher = aPattern.matcher(aJson == null ? "" : aJson);
        while (locMatcher.find()) {
            locValues.add(AIcJsonUnescape(locMatcher.group(1)));
        }
        return List.copyOf(locValues);
    }

    private static String AIcJsonUnescape(String aValue) {
        return aValue.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\/", "/");
    }

    private static String AIcPythonDistributionName(String aGroupId, String aArtifactId) {
        String[] locGroupSegments = aGroupId.split("\\.");
        ArrayList<String> locPrefixSegments = new ArrayList<>();
        for (String locSegment : locGroupSegments) {
            if (!locSegment.isBlank()) {
                locPrefixSegments.add(locSegment.trim());
                if (locPrefixSegments.size() == 2) {
                    break;
                }
            }
        }
        String locOwnerPrefix = AIcNormalizePythonName(String.join("-", locPrefixSegments));
        String locArtifact = AIcNormalizePythonName(aArtifactId);
        return locOwnerPrefix.isBlank() ? locArtifact : locOwnerPrefix + "-" + locArtifact;
    }

    private static String AIcNormalizePythonName(String aValue) {
        return aValue.toLowerCase(Locale.ROOT).replaceAll("[._-]+", "-").replaceAll("^-|-$", "");
    }

    private static String AIcRequired(Map<String, String> aValues, String aName) {
        String locValue = aValues.get(aName);
        if (locValue == null || locValue.isBlank()) {
            throw new IllegalArgumentException("Corresponding-snapshot cleanup requires coordinate '" + aName + "'.");
        }
        return locValue;
    }

    private record AIcHttpResponse(int status, String body) {
    }
}
