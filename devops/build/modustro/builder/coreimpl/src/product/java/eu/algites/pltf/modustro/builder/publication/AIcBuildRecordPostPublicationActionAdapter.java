package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.*;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Creates and publishes the standard Modustro build-record sidecar for its immediate parent content. */
public final class AIcBuildRecordPostPublicationActionAdapter implements AIiPostPublicationActionAdapter {
    public static final String ADAPTER_ID = "modustro-build-record";

    @Override
    public String adapterId() { return ADAPTER_ID; }

    @Override
    public boolean isRetrySafe(AIcPostPublicationActionAttemptContext aContext) { return true; }

    @Override
    public AIcPostPublicationActionResult execute(AIcPostPublicationActionAttemptContext aContext) throws Exception {
        URI locInputUri = aContext.inputUri();
        AIcPublicationPayload locRoot = aContext.rootPublicationPayload();
        Map<String, String> locCoordinates = locRoot.coordinates();
        String locGroupId = locCoordinates.get("groupId");
        if (locGroupId == null || locGroupId.isBlank()) {
            throw new IllegalArgumentException("Build record requires effective GroupId.");
        }
        String locFilename = AIcFilename(locInputUri);
        long locSize = AIcSize(locInputUri);
        String locSha256 = AIcHash(locInputUri);
        Map<String, Object> locRecord = new LinkedHashMap<>();
        locRecord.put("BuildRecordVersion", 1);
        locRecord.put("Artifact", Map.of(
                "GroupId", locGroupId,
                "ArtifactCoordinateId", locCoordinates.getOrDefault("artifactId", locRoot.artifactIdentity()),
                "Version", locCoordinates.getOrDefault("logicalVersion", locCoordinates.getOrDefault("version", Objects.toString(locRoot.version(), "unknown")))));
        locRecord.put("Output", Map.of(
                "TechnologyKind", locCoordinates.getOrDefault("technologyKind", "unknown"),
                "OutputType", locRoot.outputKind().descriptorName(),
                "PublishedVersion", Objects.toString(locRoot.version(), "unknown"),
                "Files", List.of(Map.of("Filename", locFilename, "Sha256", locSha256, "Size", locSize))));
        locRecord.put("ExecutionLineage", aContext.lineage().steps().stream().map(locStep -> Map.of(
                "Id", locStep.id(),
                "Kind", locStep.kind(),
                "InputUri", Objects.toString(locStep.inputUri(), ""),
                "OutputUri", Objects.toString(locStep.outputUri(), ""),
                "Result", locStep.resultMetadata())).toList());

        Path locDirectory = Files.createTempDirectory("modustro-build-record-");
        Path locSidecar = locDirectory.resolve(locFilename + ".modustro-build-record.yml");
        Files.writeString(locSidecar, AIcJson(locRecord) + "\n");
        Map<String, String> locSidecarCoordinates = new LinkedHashMap<>(locCoordinates);
        locSidecarCoordinates.put("extension", locCoordinates.getOrDefault("extension", AIcExtension(locFilename)) + ".modustro-build-record.yml");
        AIcPublicationPayload locSidecarPayload = new AIcPublicationPayload(
                locRoot.outputKind(), locRoot.stability(), locRoot.artifactIdentity(), locRoot.version(),
                List.of(new AIcPublicationPayloadFile(locSidecar, locSidecar.getFileName().toString())), locSidecarCoordinates);
        if (aContext.targetPublicationEndpoint() == null) {
            throw new IllegalArgumentException("Build-record action requires an inherited or targeted publication endpoint.");
        }
        if ("python-repository".equals(aContext.targetPublicationEndpoint().publicationAdapter())) {
            throw new IllegalArgumentException("Python package indexes cannot store build-record YAML. Configure TargetPublicationEndpointId for a metadata-capable endpoint or disable the action.");
        }
        AIcPublicationResult locPublicationResult = aContext.publicationDelegate().publish(locSidecarPayload, aContext.targetPublicationEndpoint());
        if (!locPublicationResult.success()) {
            throw new IllegalStateException("Build-record publication failed.", locPublicationResult.failure());
        }
        return new AIcPostPublicationActionResult(aContext.action().id(), true, true, false, 1, Duration.ZERO,
                locPublicationResult.outputUri(), Map.of("PublicationId", locPublicationResult.publicationId()), null);
    }

    private static String AIcFilename(URI aUri) {
        String locPath = aUri.getPath();
        if (locPath == null || locPath.isBlank() || locPath.endsWith("/")) return "content";
        return locPath.substring(locPath.lastIndexOf('/') + 1);
    }

    private static long AIcSize(URI aUri) throws Exception {
        if ("file".equalsIgnoreCase(aUri.getScheme())) return Files.size(Path.of(aUri));
        try (InputStream locInput = aUri.toURL().openStream()) {
            long locSize = 0L;
            byte[] locBuffer = new byte[65536];
            int locRead;
            while ((locRead = locInput.read(locBuffer)) >= 0) locSize = Math.addExact(locSize, locRead);
            return locSize;
        }
    }

    private static String AIcHash(URI aUri) throws Exception {
        MessageDigest locDigest = MessageDigest.getInstance("SHA-256");
        try (InputStream locInput = "file".equalsIgnoreCase(aUri.getScheme()) ? Files.newInputStream(Path.of(aUri)) : aUri.toURL().openStream()) {
            byte[] locBuffer = new byte[65536];
            int locRead;
            while ((locRead = locInput.read(locBuffer)) >= 0) locDigest.update(locBuffer, 0, locRead);
        }
        return HexFormat.of().formatHex(locDigest.digest());
    }

    private static String AIcExtension(String aName) {
        if (aName.endsWith(".tar.gz")) return "tar.gz";
        int locDot = aName.lastIndexOf('.');
        return locDot < 0 ? "bin" : aName.substring(locDot + 1);
    }

    private static String AIcJson(Object aValue) {
        if (aValue == null) return "null";
        if (aValue instanceof Boolean || aValue instanceof Number) return aValue.toString();
        if (aValue instanceof Map<?, ?> locMap) {
            return "{" + String.join(",", locMap.entrySet().stream().map(locEntry -> AIcJson(locEntry.getKey().toString()) + ":" + AIcJson(locEntry.getValue())).toList()) + "}";
        }
        if (aValue instanceof Iterable<?> locIterable) {
            java.util.ArrayList<String> locItems = new java.util.ArrayList<>();
            for (Object locItem : locIterable) locItems.add(AIcJson(locItem));
            return "[" + String.join(",", locItems) + "]";
        }
        String locText = aValue.toString();
        StringBuilder locOut = new StringBuilder("\"");
        for (char locCharacter : locText.toCharArray()) {
            switch (locCharacter) {
                case '\\' -> locOut.append("\\\\");
                case '"' -> locOut.append("\\\"");
                case '\n' -> locOut.append("\\n");
                case '\r' -> locOut.append("\\r");
                case '\t' -> locOut.append("\\t");
                default -> { if (locCharacter < 32) locOut.append(String.format("\\u%04x", (int) locCharacter)); else locOut.append(locCharacter); }
            }
        }
        return locOut.append('"').toString();
    }

    /** Returns the SHA-256 hash of one local file. */
    public static String hash(Path aPath) throws Exception {
        return AIcHash(aPath.toAbsolutePath().normalize().toUri());
    }

    /** Serializes simple build-record structures deterministically. */
    public static String json(Object aValue) {
        return AIcJson(aValue);
    }
}
