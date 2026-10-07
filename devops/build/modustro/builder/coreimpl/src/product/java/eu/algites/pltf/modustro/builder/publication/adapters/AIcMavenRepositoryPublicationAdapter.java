package eu.algites.pltf.modustro.builder.publication.adapters;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayloadFile;
import eu.algites.pltf.modustro.builder.publication.AIcPublicationAttemptContext;
import eu.algites.pltf.modustro.builder.publication.AIiPublicationAdapter;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/** Publishes Maven-layout payload files to an HTTP(S) Maven repository using idempotent PUT requests. */
public final class AIcMavenRepositoryPublicationAdapter implements AIiPublicationAdapter {
    /** Canonical adapter id. */
    public static final String ADAPTER_ID = "maven-repository";

    @Override
    public String adapterId() {
        return ADAPTER_ID;
    }

    @Override
    public boolean isRetrySafe(AIcPublicationPayload aPayload, AIcPublicationEndpoint aEndpoint) {
        return true;
    }

    @Override
    public void publish(AIcPublicationAttemptContext aContext) throws Exception {
        Map<String, String> locCoordinates = aContext.payload().coordinates();
        String locGroupId = AIcRequiredCoordinate(locCoordinates, "groupId");
        String locArtifactId = AIcRequiredCoordinate(locCoordinates, "artifactId");
        String locVersion = AIcRequiredCoordinate(locCoordinates, "version");
        URI locRoot = AIcRepositoryRoot(aContext.endpoint().publicationUri());
        String locBasePath = locGroupId.replace('.', '/') + "/" + locArtifactId + "/" + locVersion + "/";

        long locTotal = 0L;
        for (AIcPublicationPayloadFile locFile : aContext.payload().files()) {
            locTotal = Math.addExact(locTotal, Files.size(locFile.path()));
        }
        long locCompleted = 0L;
        aContext.progressReporter().started(
                "Publishing " + locGroupId + ":" + locArtifactId + ":" + locVersion + " to " + locRoot);

        for (AIcPublicationPayloadFile locPayloadFile : aContext.payload().files()) {
            AIcCheckCancellation(aContext);
            String locFileName = Path.of(locPayloadFile.logicalName()).getFileName().toString();
            URI locTarget = locRoot.resolve(locBasePath + locFileName);
            byte[] existing=AIcGet(locTarget,aContext);
            if(existing!=null){
                var digest=java.security.MessageDigest.getInstance("SHA-256");
                String remote=java.util.HexFormat.of().formatHex(digest.digest(existing));
                String local=AIcHash(locPayloadFile.path());
                if(!remote.equals(local))throw new IllegalStateException("Refusing to overwrite immutable Maven publication '"+locTarget+"' with different bytes.");
                locCompleted+=Files.size(locPayloadFile.path());
                continue;
            }
            HttpURLConnection locConnection = (HttpURLConnection) new URL(locTarget.toString()).openConnection();
            locConnection.setRequestMethod("PUT");
            locConnection.setDoOutput(true);
            locConnection.setUseCaches(false);
            AIcApplyTimeouts(locConnection, aContext.deadline());
            AIcApplyCredentials(locConnection, aContext.credentials());
            long locSize = Files.size(locPayloadFile.path());
            if (locSize <= Integer.MAX_VALUE) {
                locConnection.setFixedLengthStreamingMode((int) locSize);
            } else {
                locConnection.setFixedLengthStreamingMode(locSize);
            }
            try (OutputStream locOutput = locConnection.getOutputStream(); var locInput = Files.newInputStream(locPayloadFile.path())) {
                byte[] locBuffer = new byte[1024 * 1024];
                while (true) {
                    AIcCheckCancellation(aContext);
                    int locRead = locInput.read(locBuffer);
                    if (locRead < 0) {
                        break;
                    }
                    locOutput.write(locBuffer, 0, locRead);
                    locCompleted += locRead;
                    if (locTotal > 0L) {
                        aContext.progressReporter().progress(
                                locCompleted,
                                locTotal,
                                "bytes",
                                "Publishing " + locFileName);
                    }
                }
            }
            int locStatus = locConnection.getResponseCode();
            if (locStatus < 200 || locStatus >= 300) {
                String locMessage = locConnection.getResponseMessage();
                locConnection.disconnect();
                throw new IllegalStateException(
                        "Maven repository PUT failed for '" + locTarget + "' with HTTP " + locStatus + " " + locMessage + ".");
            }
            locConnection.disconnect();
        }
        if(locCoordinates.containsKey("snapshotTimestamp"))AIcSnapshotMetadata(locRoot.resolve(locBasePath+"maven-metadata.xml"),aContext);
        aContext.progressReporter().completed("Maven repository publication completed.");
    }


    private static final java.util.concurrent.ConcurrentHashMap<String,Object> METADATA_LOCKS=new java.util.concurrent.ConcurrentHashMap<>();
    private static byte[] AIcGet(URI uri,AIcPublicationAttemptContext context)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();connection.setRequestMethod("GET");connection.setUseCaches(false);AIcApplyTimeouts(connection,context.deadline());AIcApplyCredentials(connection,context.credentials());
        try{int code=connection.getResponseCode();if(code==404)return null;if(code!=200)throw new IllegalStateException("Maven repository GET failed for "+uri+": HTTP "+code);
            try(var input=connection.getInputStream()){return input.readAllBytes();}
        }finally{connection.disconnect();}
    }
    /** Compound extensions are ordinary Maven snapshotVersion entries; all forms share the reserved instance. */
    private static void AIcSnapshotMetadata(URI uri,AIcPublicationAttemptContext context)throws Exception{
        synchronized(METADATA_LOCKS.computeIfAbsent(uri.toString(),ignored->new Object())){
            byte[] old=AIcGet(uri,context);var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            var builder=factory.newDocumentBuilder();var document=old==null?builder.newDocument():builder.parse(new java.io.ByteArrayInputStream(old));
            var metadata=old==null?document.createElement("metadata"):document.getDocumentElement();if(old==null)document.appendChild(metadata);
            if(!metadata.getTagName().equals("metadata"))throw new IllegalArgumentException("Invalid Maven metadata root.");
            var coords=context.payload().coordinates();set(document,metadata,"groupId",coords.get("groupId"));set(document,metadata,"artifactId",coords.get("artifactId"));set(document,metadata,"version",coords.get("version"));
            var versioning=child(document,metadata,"versioning");String updated=coords.get("snapshotTimestamp").replace(".","");
            var last=child(document,versioning,"lastUpdated");
            if(last.getTextContent().compareTo(updated)<=0){last.setTextContent(updated);var snapshot=child(document,versioning,"snapshot");set(document,snapshot,"timestamp",coords.get("snapshotTimestamp"));set(document,snapshot,"buildNumber",coords.get("snapshotBuildNumber"));}
            var versions=child(document,versioning,"snapshotVersions");
            for(var file:context.payload().files()){
                boolean pom=file.logicalName().endsWith(".pom");
                boolean module=file.logicalName().endsWith(".module");
                String extension=pom?"pom":module?"module":coords.get("extension");
                String classifier=(pom||module)?"":coords.getOrDefault("classifier","");
                if(extension==null||extension.isBlank())throw new IllegalArgumentException("Snapshot payload requires extension coordinate.");
                org.w3c.dom.Element entry=null;
                for(var node=versions.getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof org.w3c.dom.Element element&&element.getTagName().equals("snapshotVersion")&&text(element,"extension").equals(extension)&&text(element,"classifier").equals(classifier)){entry=element;break;}
                if(entry==null){entry=document.createElement("snapshotVersion");versions.appendChild(entry);}else if(text(entry,"updated").compareTo(updated)>0)continue;
                set(document,entry,"extension",extension);if(!classifier.isEmpty())set(document,entry,"classifier",classifier);set(document,entry,"value",context.payload().version());set(document,entry,"updated",updated);
            }
            var transformer=javax.xml.transform.TransformerFactory.newInstance().newTransformer();transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT,"yes");var buffer=new java.io.ByteArrayOutputStream();transformer.transform(new javax.xml.transform.dom.DOMSource(document),new javax.xml.transform.stream.StreamResult(buffer));
            HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();connection.setRequestMethod("PUT");connection.setDoOutput(true);AIcApplyTimeouts(connection,context.deadline());AIcApplyCredentials(connection,context.credentials());byte[] bytes=buffer.toByteArray();connection.setFixedLengthStreamingMode(bytes.length);
            try{try(var out=connection.getOutputStream()){out.write(bytes);}int code=connection.getResponseCode();if(code<200||code>=300)throw new IllegalStateException("Maven metadata PUT failed: HTTP "+code);}finally{connection.disconnect();}
        }
    }
    private static org.w3c.dom.Element child(org.w3c.dom.Document document,org.w3c.dom.Element parent,String name){
        for(var node=parent.getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof org.w3c.dom.Element element&&element.getTagName().equals(name))return element;
        var element=document.createElement(name);parent.appendChild(element);return element;
    }
    private static void set(org.w3c.dom.Document document,org.w3c.dom.Element parent,String name,String value){child(document,parent,name).setTextContent(value);}
    private static String text(org.w3c.dom.Element parent,String name){for(var node=parent.getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof org.w3c.dom.Element element&&element.getTagName().equals(name))return element.getTextContent();return "";}

    private static URI AIcRepositoryRoot(URI aUri) {
        if (aUri == null || aUri.getScheme() == null) {
            throw new IllegalArgumentException("maven-repository publishing requires an absolute HTTP(S) PublicationUri.");
        }
        String locScheme = aUri.getScheme().toLowerCase();
        if (!"http".equals(locScheme) && !"https".equals(locScheme)) {
            throw new IllegalArgumentException("maven-repository publishing supports only http: and https: PublicationUri values.");
        }
        String locText = aUri.toString();
        return URI.create(locText.endsWith("/") ? locText : locText + "/");
    }

    private static void AIcApplyCredentials(HttpURLConnection aConnection, Map<String, String> aCredentials) {
        String locUsername = aCredentials.get("username");
        String locPassword = aCredentials.get("password");
        if (locUsername != null && locPassword != null) {
            String locValue = Base64.getEncoder().encodeToString((locUsername + ":" + locPassword).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            aConnection.setRequestProperty("Authorization", "Basic " + locValue);
            return;
        }
        String locBearerToken = aCredentials.get("bearerToken");
        if (locBearerToken != null) {
            aConnection.setRequestProperty("Authorization", "Bearer " + locBearerToken);
            return;
        }
        String locApiKey = aCredentials.get("apiKey");
        String locApiKeyHeader = aCredentials.get("apiKeyHeader");
        if (locApiKey != null && locApiKeyHeader != null) {
            aConnection.setRequestProperty(locApiKeyHeader, locApiKey);
        }
    }

    private static void AIcApplyTimeouts(HttpURLConnection aConnection, Instant aDeadline) {
        if (aDeadline == null) {
            return;
        }
        long locMillis = Math.max(1L, Duration.between(Instant.now(), aDeadline).toMillis());
        int locTimeout = (int) Math.min(Integer.MAX_VALUE, locMillis);
        aConnection.setConnectTimeout(locTimeout);
        aConnection.setReadTimeout(locTimeout);
    }

    private static String AIcHash(Path aPath) throws Exception {
        java.security.MessageDigest locDigest = java.security.MessageDigest.getInstance("SHA-256");
        try (var locInput = Files.newInputStream(aPath)) {
            byte[] locBuffer = new byte[65536];
            int locRead;
            while ((locRead = locInput.read(locBuffer)) >= 0) {
                locDigest.update(locBuffer, 0, locRead);
            }
        }
        return java.util.HexFormat.of().formatHex(locDigest.digest());
    }

    private static String AIcRequiredCoordinate(Map<String, String> aCoordinates, String aKey) {
        String locValue = aCoordinates.get(aKey);
        if (locValue == null || locValue.isBlank()) {
            throw new IllegalArgumentException("maven-repository publishing requires payload coordinate '" + aKey + "'.");
        }
        return locValue;
    }

    private static void AIcCheckCancellation(AIcPublicationAttemptContext aContext) throws InterruptedException {
        if (aContext.cancellationToken().isCancellationRequested()) {
            throw new InterruptedException("Publication attempt cancelled.");
        }
    }
}
