package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.execution.AIngBuildExecutionFailurePolicy_1;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import eu.algites.pltf.modustro.builder.model.publication.*;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationExecutionLineage;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationExecutionStep;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayloadFile;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationResult;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationOutputKind;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationStability;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Exercises provider-aware post-release snapshot cleanup against mock Cloudsmith and Repsy APIs. */
public final class AIcRemoveCorrespondingSnapshotsChecks {
    public static void main(String[] aArguments) throws Exception { run(); System.out.println("SNAPSHOT_CLEANUP_CHECKS_OK"); }
    public static void run() throws Exception {
        var locChecks = new AIcRemoveCorrespondingSnapshotsChecks();
        locChecks.AIcDeletesExactCloudsmithMavenSnapshot();
        locChecks.AIcDeletesCloudsmithPythonSnapshotSeries();
        locChecks.AIcDeletesExactRepsyMavenSnapshot();
        locChecks.AIcDeletesCompleteRepsyPythonSnapshotSeries();
    }

    public void AIcDeletesExactCloudsmithMavenSnapshot() throws Exception {
        List<String> locRequests = new CopyOnWriteArrayList<>();
        List<String> locAuthorizations = new CopyOnWriteArrayList<>();
        HttpServer locServer = AIcServer(aExchange -> {
            locRequests.add(aExchange.getRequestMethod() + " " + aExchange.getRequestURI());
            locAuthorizations.add(aExchange.getRequestHeaders().getFirst("Authorization"));
            if ("GET".equals(aExchange.getRequestMethod())) {
                AIcRespond(aExchange, 200,
                        "[{\"name\":\"library\",\"version\":\"1.2.3-SNAPSHOT\",\"format\":\"maven\",\"identifier_perm\":\"pkg-java\"}]");
            } else if ("DELETE".equals(aExchange.getRequestMethod())
                    && aExchange.getRequestURI().getPath().endsWith("/pkg-java/")) {
                AIcRespond(aExchange, 204, "");
            } else {
                AIcRespond(aExchange, 404, "");
            }
        });
        try {
            AIcPublicationEndpoint locTarget = AIcEndpoint(
                    "java-snapshot",
                    URI.create("https://maven.cloudsmith.io/algites/java-snapshots-pub/"),
                    Map.of(
                            "Provider", "cloudsmith",
                            "Workspace", "algites",
                            "Repository", "java-snapshots-pub",
                            "ManagementApiUri", "http://127.0.0.1:" + locServer.getAddress().getPort() + "/v1/packages/"));
            AIcPublicationPayload locPayload = AIcPayload(
                    "java", "eu.algites.test", "library", "1.2.3", "1.2.3");
            AIcPublicationFinalizationActionResult locResult = new AIcRemoveCorrespondingSnapshotsVersionScopePublicationFinalizationActionAdapter()
                    .execute(AIcContext(locPayload, locTarget, Map.of("username", "algites", "password", "api-key")));
            AIcPublicationCheckAssertions.assertTrue(locResult.success());
            AIcPublicationCheckAssertions.assertEquals(locResult.metadata().get("DeletedPackageVersions"), 1);
            AIcPublicationCheckAssertions.assertTrue(locRequests.stream().anyMatch(locValue -> locValue.startsWith("GET /v1/packages/algites/java-snapshots-pub/?")));
            AIcPublicationCheckAssertions.assertTrue(locRequests.stream().anyMatch(locValue -> locValue.equals("DELETE /v1/packages/algites/java-snapshots-pub/pkg-java/")));
            AIcPublicationCheckAssertions.assertTrue(locRequests.stream().anyMatch(locValue -> locValue.contains("version%3A%5E1.2.3-SNAPSHOT%24")));
            AIcPublicationCheckAssertions.assertTrue(locAuthorizations.stream().allMatch("token api-key"::equals));
        } finally {
            locServer.stop(0);
        }
    }


    public void AIcDeletesCloudsmithPythonSnapshotSeries() throws Exception {
        List<String> locRequests = new CopyOnWriteArrayList<>();
        HttpServer locServer = AIcServer(aExchange -> {
            locRequests.add(aExchange.getRequestMethod() + " " + aExchange.getRequestURI());
            if ("GET".equals(aExchange.getRequestMethod())) {
                AIcRespond(aExchange, 200,
                        "[{\"name\":\"eu-algites-example-tool\",\"version\":\"2.1.dev20261005010101\",\"format\":\"python\",\"slug_perm\":\"pkg-python-1\"},"
                                + "{\"name\":\"eu-algites-example-tool\",\"version\":\"2.1.dev20261005020202\",\"format\":\"python\",\"slug_perm\":\"pkg-python-2\"}]");
            } else if ("DELETE".equals(aExchange.getRequestMethod())) {
                AIcRespond(aExchange, 204, "");
            } else {
                AIcRespond(aExchange, 404, "");
            }
        });
        try {
            AIcPublicationEndpoint locTarget = AIcEndpoint(
                    "python-snapshot",
                    URI.create("https://python.cloudsmith.io/algites/python-snapshots-pub/"),
                    Map.of(
                            "Provider", "cloudsmith",
                            "Workspace", "algites",
                            "Repository", "python-snapshots-pub",
                            "ManagementApiUri", "http://127.0.0.1:" + locServer.getAddress().getPort() + "/v1/packages/"));
            AIcPublicationPayload locPayload = AIcPayload(
                    "python", "eu.algites.test", "example_tool", "2.1", "2.1");
            AIcPublicationFinalizationActionResult locResult = new AIcRemoveCorrespondingSnapshotsVersionScopePublicationFinalizationActionAdapter()
                    .execute(AIcContext(locPayload, locTarget, Map.of("password", "api-key")));
            AIcPublicationCheckAssertions.assertTrue(locResult.success());
            AIcPublicationCheckAssertions.assertEquals(locResult.metadata().get("DeletedPackageVersions"), 2);
            AIcPublicationCheckAssertions.assertTrue(locRequests.stream().anyMatch(locValue -> locValue.contains("format%3Apython")));
            AIcPublicationCheckAssertions.assertTrue(locRequests.stream().anyMatch(locValue -> locValue.contains("version%3A%5E2.1.dev")));
            AIcPublicationCheckAssertions.assertTrue(locRequests.contains("DELETE /v1/packages/algites/python-snapshots-pub/pkg-python-1/"));
            AIcPublicationCheckAssertions.assertTrue(locRequests.contains("DELETE /v1/packages/algites/python-snapshots-pub/pkg-python-2/"));
        } finally {
            locServer.stop(0);
        }
    }

    public void AIcDeletesExactRepsyMavenSnapshot() throws Exception {
        List<String> locRequests = new CopyOnWriteArrayList<>();
        HttpServer locServer = AIcServer(aExchange -> {
            locRequests.add(aExchange.getRequestMethod() + " " + aExchange.getRequestURI());
            if ("DELETE".equals(aExchange.getRequestMethod())) {
                AIcRespond(aExchange, 204, "");
            } else {
                AIcRespond(aExchange, 404, "");
            }
        });
        try {
            AIcPublicationEndpoint locTarget = AIcEndpoint(
                    "java-snapshot",
                    URI.create("https://repo.repsy.io/algites/java-snapshots-priv/"),
                    Map.of(
                            "Provider", "repsy",
                            "Repository", "java-snapshots-priv",
                            "ManagementApiUri", "http://127.0.0.1:" + locServer.getAddress().getPort() + "/"));
            AIcPublicationPayload locPayload = AIcPayload(
                    "java", "eu.algites.test", "library", "1.2.3", "1.2.3");
            AIcPublicationFinalizationActionResult locResult = new AIcRemoveCorrespondingSnapshotsVersionScopePublicationFinalizationActionAdapter()
                    .execute(AIcContext(locPayload, locTarget, Map.of("username", "user", "password", "secret")));
            AIcPublicationCheckAssertions.assertTrue(locResult.success());
            AIcPublicationCheckAssertions.assertEquals(locResult.metadata().get("DeletedPackageVersions"), 1);
            AIcPublicationCheckAssertions.assertTrue(locRequests.contains(
                    "DELETE /api/mvn/artifacts/java-snapshots-priv/eu.algites.test/library/versions/1.2.3-SNAPSHOT"));
        } finally {
            locServer.stop(0);
        }
    }

    public void AIcDeletesCompleteRepsyPythonSnapshotSeries() throws Exception {
        List<String> locRequests = new CopyOnWriteArrayList<>();
        List<String> locAuthorizations = new CopyOnWriteArrayList<>();
        HttpServer locServer = AIcServer(aExchange -> {
            locRequests.add(aExchange.getRequestMethod() + " " + aExchange.getRequestURI());
            locAuthorizations.add(aExchange.getRequestHeaders().getFirst("Authorization"));
            if ("GET".equals(aExchange.getRequestMethod())) {
                AIcRespond(aExchange, 200,
                        "{\"data\":{\"content\":[{\"version\":\"2.1.dev20261005010101\"},{\"version\":\"2.1.dev20261005020202\"},{\"version\":\"2.0.dev20261001010101\"}]}}" );
            } else if ("DELETE".equals(aExchange.getRequestMethod())) {
                AIcRespond(aExchange, 204, "");
            } else {
                AIcRespond(aExchange, 404, "");
            }
        });
        try {
            AIcPublicationEndpoint locTarget = AIcEndpoint(
                    "python-snapshot",
                    URI.create("https://repo.repsy.io/algites/python-snapshots-priv/simple/"),
                    Map.of(
                            "Provider", "repsy",
                            "Repository", "python-snapshots-priv",
                            "ManagementApiUri", "http://127.0.0.1:" + locServer.getAddress().getPort() + "/"));
            AIcPublicationPayload locPayload = AIcPayload(
                    "python", "eu.algites.test", "example_tool", "2.1", "2.1");
            AIcPublicationFinalizationActionResult locResult = new AIcRemoveCorrespondingSnapshotsVersionScopePublicationFinalizationActionAdapter()
                    .execute(AIcContext(locPayload, locTarget, Map.of("username", "user", "password", "secret")));
            AIcPublicationCheckAssertions.assertTrue(locResult.success());
            AIcPublicationCheckAssertions.assertEquals(locResult.metadata().get("DeletedPackageVersions"), 2);
            AIcPublicationCheckAssertions.assertTrue(locRequests.stream().anyMatch(locValue -> locValue.startsWith(
                    "GET /api/pypi/packages/python-snapshots-priv/eu-algites-example-tool/versions?")));
            AIcPublicationCheckAssertions.assertTrue(locRequests.contains(
                    "DELETE /api/pypi/packages/python-snapshots-priv/eu-algites-example-tool/versions/2.1.dev20261005010101"));
            AIcPublicationCheckAssertions.assertTrue(locRequests.contains(
                    "DELETE /api/pypi/packages/python-snapshots-priv/eu-algites-example-tool/versions/2.1.dev20261005020202"));
            AIcPublicationCheckAssertions.assertFalse(locRequests.contains(
                    "DELETE /api/pypi/packages/python-snapshots-priv/eu-algites-example-tool/versions/2.0.dev20261001010101"));
            AIcPublicationCheckAssertions.assertTrue(locAuthorizations.stream().allMatch(locValue -> locValue != null && locValue.startsWith("Basic ")));
        } finally {
            locServer.stop(0);
        }
    }

    private static AIcVersionScopePublicationFinalizationActionAttemptContext AIcContext(
            AIcPublicationPayload aPayload, AIcPublicationEndpoint aTarget, Map<String, String> aCredentials) {
        var locAction = new AIcVersionScopePublicationFinalizationAction("remove-corresponding-snapshots", true,
                AIcRemoveCorrespondingSnapshotsVersionScopePublicationFinalizationActionAdapter.ADAPTER_ID, 0,
                AIngBuildExecutionFailurePolicy_1.IGNORE_FAILURE, 0, 0L, null, true, Map.of());
        var locPublication = new AIcPublicationExecutionResult("release/standard", aTarget, aPayload, Map.of(),
                new AIcPublicationResult("release", true, true, false, 1, Duration.ZERO, URI.create("https://release.invalid/artifact"), Map.of(), null), List.of());
        var locOutput = new AIcOutputPublicationExecutionResult(aPayload.artifactIdentity(), aPayload.coordinates().get("technologyKind"),
                aPayload.outputKind(), aPayload.stability(), aPayload.coordinates().get("logicalVersion"),
                Map.of(aTarget.id(), aTarget), Map.of(aTarget.id(), aTarget), List.of(locPublication), List.of());
        var locArtifact = new AIcArtifactPublicationExecutionResult(aPayload.artifactIdentity(), "artifact", ".", locOutput.version(),
                aPayload.stability(), List.of(locOutput), List.of());
        var locScope = new AIcVersionScopePublicationExecutionResult(".", locOutput.version(), aPayload.stability(),
                AInVersionScopePublicationAttemptState.FINALIZING, List.of(locArtifact), List.of());
        return new AIcVersionScopePublicationFinalizationActionAttemptContext(locAction, locScope, 1, 1, null, null,
                Map.of(aTarget.id(), aCredentials), () -> false, AIcProgress());
    }

    private static AIcPublicationPayload AIcPayload(
            String aTechnologyKind,
            String aGroupId,
            String aArtifactId,
            String aVersion,
            String aLogicalVersion) throws Exception {
        var locFile = Files.createTempFile("modustro-release-", ".bin");
        Files.writeString(locFile, "release");
        return new AIcPublicationPayload(
                AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES,
                AInPublicationStability.RELEASE,
                aGroupId + ":" + aArtifactId,
                aVersion,
                List.of(new AIcPublicationPayloadFile(locFile, locFile.getFileName().toString())),
                Map.of(
                        "groupId", aGroupId,
                        "artifactId", aArtifactId,
                        "version", aVersion,
                        "logicalVersion", aLogicalVersion,
                        "technologyKind", aTechnologyKind));
    }

    private static AIcPublicationEndpoint AIcEndpoint(String aId, URI aUri, Map<String, Object> aConfiguration) {
        return new AIcPublicationEndpoint(
                aId,
                true,
                aUri,
                "mock-publication",
                "credentials",
                0,
                AIngBuildExecutionFailurePolicy_1.FAIL_BUILD_ON_FAILURE,
                0,
                0L,
                null,
                true,
                aConfiguration);
    }

    private static AIiPublicationProgressReporter AIcProgress() {
        return new AIiPublicationProgressReporter() {
            @Override
            public void started(String aMessage) {
            }

            @Override
            public void progress(long aCompleted, long aTotal, String aUnit, String aMessage) {
            }

            @Override
            public void indeterminate(String aMessage) {
            }

            @Override
            public void completed(String aMessage) {
            }
        };
    }

    private static HttpServer AIcServer(AIiExchangeHandler aHandler) throws Exception {
        HttpServer locServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        locServer.createContext("/", aExchange -> {
            try {
                aHandler.handle(aExchange);
            } catch (Exception locFailure) {
                throw new RuntimeException(locFailure);
            } finally {
                aExchange.close();
            }
        });
        locServer.start();
        return locServer;
    }

    private static void AIcRespond(HttpExchange aExchange, int aStatus, String aBody) throws Exception {
        byte[] locBody = aBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (locBody.length == 0) {
            aExchange.sendResponseHeaders(aStatus, -1);
        } else {
            aExchange.sendResponseHeaders(aStatus, locBody.length);
            aExchange.getResponseBody().write(locBody);
        }
    }

    @FunctionalInterface
    private interface AIiExchangeHandler {
        void handle(HttpExchange aExchange) throws Exception;
    }
}
