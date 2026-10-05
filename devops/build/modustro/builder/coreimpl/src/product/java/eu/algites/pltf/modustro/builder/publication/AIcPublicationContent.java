package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayloadFile;
import java.net.URI;

/** Resolves the single canonical content URI exposed to post-publication actions. */
final class AIcPublicationContent {
    private AIcPublicationContent() {
    }

    static URI inputUri(AIcPublicationPayload aPayload) {
        return primaryFile(aPayload).contentUri();
    }

    static URI publishedUri(AIcPublicationPayload aPayload, eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint aEndpoint) {
        AIcPublicationPayloadFile locFile = primaryFile(aPayload);
        String locAdapter = aEndpoint.publicationAdapter();
        URI locRoot = aEndpoint.publicationUri();
        if (locRoot == null) {
            return locFile.contentUri();
        }
        if ("maven-repository".equals(locAdapter) || "maven-local-repository".equals(locAdapter)) {
            String locGroupId = required(aPayload, "groupId").replace('.', '/');
            String locArtifactId = required(aPayload, "artifactId");
            String locVersion = required(aPayload, "version");
            String locRootText = locRoot.toString().endsWith("/") ? locRoot.toString() : locRoot + "/";
            return URI.create(locRootText).resolve(locGroupId + "/" + locArtifactId + "/" + locVersion + "/" + locFile.logicalName());
        }
        if ("local-copy".equals(locAdapter) || "http-directory".equals(locAdapter) || "s3-object-storage".equals(locAdapter)) {
            String locRootText = locRoot.toString().endsWith("/") ? locRoot.toString() : locRoot + "/";
            return URI.create(locRootText).resolve(locFile.logicalName());
        }
        if ("git-branch".equals(locAdapter)) {
            return locRoot;
        }
        if ("python-repository".equals(locAdapter)) {
            return locRoot;
        }
        return locRoot;
    }

    private static AIcPublicationPayloadFile primaryFile(AIcPublicationPayload aPayload) {
        if (aPayload.files().isEmpty()) {
            throw new IllegalArgumentException("Publication payload contains no content file.");
        }
        for (AIcPublicationPayloadFile locFile : aPayload.files()) {
            if (!locFile.logicalName().endsWith(".pom")) {
                return locFile;
            }
        }
        return aPayload.files().get(0);
    }

    private static String required(AIcPublicationPayload aPayload, String aKey) {
        String locValue = aPayload.coordinates().get(aKey);
        if (locValue == null || locValue.isBlank()) {
            throw new IllegalArgumentException("Publication payload requires coordinate '" + aKey + "'.");
        }
        return locValue;
    }
}
