package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Combines logical Maven outputs into one physical publication per repository and Maven GAV. */
public final class AIcMavenPublicationBatchPlanner {
    /** Retains a mapping for reporting every logical output independently. */
    public record AIcdBatch(List<AIcPublicationJob> physicalJobs, Map<String, String> physicalIdByLogicalId) {
        public AIcdBatch {
            physicalJobs = List.copyOf(physicalJobs);
            physicalIdByLogicalId = Map.copyOf(physicalIdByLogicalId);
        }
    }

    /** Plans one artifact/version across all enabled output kinds before starting transport. */
    public AIcdBatch plan(List<AIcPublicationJob> aJobs) throws Exception {
        LinkedHashMap<String, List<AIcPublicationJob>> locGroups = new LinkedHashMap<>();
        for (AIcPublicationJob locJob : aJobs) {
            String locKey = AIcPhysicalKey(locJob);
            locGroups.computeIfAbsent(locKey, ignored -> new ArrayList<>()).add(locJob);
        }
        List<AIcPublicationJob> locPhysicalJobs = new ArrayList<>();
        Map<String, String> locMembers = new LinkedHashMap<>();
        for (List<AIcPublicationJob> locGroup : locGroups.values()) {
            AIcPublicationJob locFirst = locGroup.get(0);
            if (!"maven-repository".equals(locFirst.endpoint().publicationAdapter())) {
                locPhysicalJobs.add(AIcWithId(locFirst, locFirst.id()));
                locMembers.put(locFirst.id(), locFirst.id());
                continue;
            }
            AIcValidateGroup(locGroup);
            LinkedHashMap<String, AIcPublicationPayloadFile> locFiles = new LinkedHashMap<>();
            for (AIcPublicationJob locJob : locGroup) {
                for (AIcPublicationPayloadFile locFile : locJob.payload().files()) {
                    AIcPublicationPayloadFile locExisting = locFiles.putIfAbsent(locFile.logicalName(), locFile);
                    if (locExisting != null && !Arrays.equals(Files.readAllBytes(locExisting.path()), Files.readAllBytes(locFile.path()))) {
                        throw new IllegalArgumentException("Conflicting Maven bundle filename: " + locFile.logicalName());
                    }
                }
            }
            /* Reject legacy and new artifact BuildRecord filenames before any Maven upload. */
            if (locFiles.keySet().stream().anyMatch(aName ->
                    aName.endsWith("-build-record.yml") ||
                    aName.endsWith(".modustro-build-record.yml") ||
                    aName.endsWith("modustro-build-record.yml") ||
                    aName.endsWith("modustro-artifact-build-record.yml"))) {
                throw new IllegalArgumentException("Artifact BuildRecord must not be published as a Maven package.");
            }
            String locArtifactId = locFirst.payload().coordinates().get("artifactId");
            String locVersion = locFirst.payload().version();
            String locPrefix = locArtifactId + "-" + locVersion;
            if (!locFiles.containsKey(locPrefix + ".jar") || !locFiles.containsKey(locPrefix + ".pom") ||
                    !locFiles.containsKey(locPrefix + ".module")) {
                throw new IllegalArgumentException("Maven bundle " + locArtifactId + ":" + locVersion +
                        " must include the main JAR, POM and Gradle module metadata before uploading any file; found " + locFiles.keySet());
            }
            String locPhysicalId = "maven-bundle:" + locFirst.id();
            AIcPublicationPayload locPhysicalPayload = new AIcPublicationPayload(locFirst.payload().outputKind(),
                    locFirst.payload().stability(), locFirst.payload().artifactIdentity(), locVersion,
                    List.copyOf(locFiles.values()), locFirst.payload().coordinates());
            locPhysicalJobs.add(new AIcPublicationJob(locPhysicalId, AIcEndpointWithId(locFirst.endpoint(), locPhysicalId), locPhysicalPayload,
                    locFirst.configuration(), List.of(), locFirst.publicationEndpointRegistry()));
            for (AIcPublicationJob locMember : locGroup) locMembers.put(locMember.id(), locPhysicalId);
        }
        return new AIcdBatch(locPhysicalJobs, locMembers);
    }

    private static AIcPublicationJob AIcWithId(AIcPublicationJob aJob, String aId) {
        return new AIcPublicationJob(aId, AIcEndpointWithId(aJob.endpoint(), aId), aJob.payload(),
                aJob.configuration(), aJob.publicationFinalizationActions(), aJob.publicationEndpointRegistry());
    }

    private static AIcPublicationEndpoint AIcEndpointWithId(AIcPublicationEndpoint aEndpoint, String aId) {
        return new AIcPublicationEndpoint(aId, aEndpoint.executionEnabled(), aEndpoint.publicationUri(),
                aEndpoint.publicationAdapter(), aEndpoint.publicationCredentialProfile(),
                aEndpoint.executionOrder(), aEndpoint.executionFailurePolicy(), aEndpoint.publicationRetryCount(),
                aEndpoint.publicationWaitForNextAttemptMillis(), aEndpoint.publicationAttemptTimeoutMillis(),
                aEndpoint.showPublicationProgressIfPossible(), aEndpoint.configuration());
    }

    private static String AIcPhysicalKey(AIcPublicationJob aJob) {
        AIcPublicationEndpoint locEndpoint = aJob.endpoint();
        if (!"maven-repository".equals(locEndpoint.publicationAdapter())) return "independent:" + aJob.id();
        Map<String, String> locCoordinates = aJob.payload().coordinates();
        URI locUri = locEndpoint.publicationUri().normalize();
        String locRepository = locUri.toString().replaceAll("/+$", "");
        return String.join("|", "maven-repository", locRepository,
                locCoordinates.getOrDefault("groupId", ""), locCoordinates.getOrDefault("artifactId", ""),
                locCoordinates.getOrDefault("version", ""), aJob.payload().stability().name());
    }

    private static void AIcValidateGroup(List<AIcPublicationJob> aGroup) {
        AIcPublicationJob locFirst = aGroup.get(0);
        AIcPublicationEndpoint locExpected = locFirst.endpoint();
        for (AIcPublicationJob locJob : aGroup) {
            AIcPublicationEndpoint locActual = locJob.endpoint();
            if (!Objects.equals(locJob.payload().version(), locFirst.payload().version())) {
                throw new IllegalArgumentException("Conflicting concrete Maven snapshot versions for the same " +
                        "GroupId/ArtifactId/Version: " + locFirst.id() + " versus " + locJob.id());
            }
            if (locActual.executionOrder() != locExpected.executionOrder() ||
                    locActual.executionFailurePolicy() != locExpected.executionFailurePolicy() ||
                    locActual.publicationRetryCount() != locExpected.publicationRetryCount() ||
                    locActual.publicationWaitForNextAttemptMillis() != locExpected.publicationWaitForNextAttemptMillis() ||
                    !Objects.equals(locActual.publicationAttemptTimeoutMillis(), locExpected.publicationAttemptTimeoutMillis()) ||
                    !Objects.equals(locActual.publicationCredentialProfile(), locExpected.publicationCredentialProfile()) ||
                    !Objects.equals(locActual.configuration(), locExpected.configuration())) {
                throw new IllegalArgumentException("All outputs targeting the same Maven package must have compatible " +
                        "ExecutionOrder, failure/retry/timeout policy and credential/configuration: " +
                        locFirst.id() + " versus " + locJob.id());
            }
            if (!locJob.publicationFinalizationActions().isEmpty()) {
                throw new IllegalArgumentException("Maven bundle member " + locJob.id() +
                        " cannot run a second package publication via a post-upload finalization action.");
            }
        }
    }
}
