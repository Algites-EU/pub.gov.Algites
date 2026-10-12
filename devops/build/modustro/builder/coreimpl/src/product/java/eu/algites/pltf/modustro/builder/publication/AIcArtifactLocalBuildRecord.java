package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayloadFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes one invocation-local record covering every staged output of an artifact. */
public final class AIcArtifactLocalBuildRecord {
    private AIcArtifactLocalBuildRecord() { }

    public static Path write(Path aRoot, String aArtifactPath, List<AIcPublicationPayload> aOutputs) throws Exception {
        if (aOutputs.isEmpty()) throw new IllegalArgumentException("Artifact record requires outputs.");
        AIcPublicationPayload locFirst = aOutputs.get(0);
        Map<String, String> locCoordinates = locFirst.coordinates();
        String locGroupId = locCoordinates.getOrDefault("groupId", "unknown");
        String locArtifactId = locCoordinates.getOrDefault("artifactId", locFirst.artifactIdentity());
        String locVersion = locCoordinates.getOrDefault("logicalVersion", locCoordinates.getOrDefault("version", locFirst.version()));
        List<Map<String, Object>> locOutputs = new ArrayList<>();
        for (AIcPublicationPayload locOutput : aOutputs) {
            List<Map<String, Object>> locFiles = new ArrayList<>();
            for (AIcPublicationPayloadFile locFile : locOutput.files()) {
                if (!Files.isRegularFile(locFile.path())) continue;
                locFiles.add(Map.of("Filename", locFile.logicalName(), "Size", Files.size(locFile.path()),
                        "Sha256", AIcBuildRecordPublicationFinalizationActionAdapter.hash(locFile.path())));
            }
            locFiles.sort(Comparator.comparing(aFile -> aFile.get("Filename").toString()));
            locOutputs.add(Map.of("TechnologyKind", locOutput.coordinates().getOrDefault("technologyKind", "unknown"),
                    "OutputType", locOutput.outputKind().descriptorName(), "Files", locFiles));
        }
        locOutputs.sort(Comparator.comparing(aOutput -> aOutput.get("TechnologyKind") + "/" + aOutput.get("OutputType")));
        Map<String, Object> locRecord = new LinkedHashMap<>();
        locRecord.put("BuildRecordVersion", 1);
        locRecord.put("Artifact", Map.of("GroupId", locGroupId, "ArtifactCoordinateId", locArtifactId,
                "Version", locVersion, "ArtifactPath", aArtifactPath));
        locRecord.put("Outputs", locOutputs);
        Path locDirectory = aRoot.resolve("build-records").resolve(locGroupId.replace('.', '/'))
                .resolve(locArtifactId).resolve(locVersion.replace('/', '_'));
        Files.createDirectories(locDirectory);
        Path locTarget = locDirectory.resolve("modustro-artifact-build-record.yml");
        Files.writeString(locTarget, AIcBuildRecordPublicationFinalizationActionAdapter.json(locRecord) + "\n");
        return locTarget;
    }
}
