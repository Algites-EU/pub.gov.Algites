package eu.algites.pltf.modustro.builder.publication;

import com.sun.net.httpserver.HttpServer;
import eu.algites.pltf.modustro.builder.model.publication.*;
import eu.algites.pltf.modustro.builder.publication.adapters.AIcMavenRepositoryPublishingAdapter;
import java.net.*;import java.nio.file.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import org.testng.annotations.Test;import static org.testng.Assert.*;

/** Exercises real Maven HTTP requests, compound extensions and retry immutability. */
public final class AItcMavenBuildRecordTest {
 @Test public void pairedSnapshotAndIdempotentRetry() throws Exception {
  var bytes=new ConcurrentHashMap<String,byte[]>();var puts=new ConcurrentHashMap<String,AtomicInteger>();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",exchange->{
   String path=exchange.getRequestURI().getPath();
   if(exchange.getRequestMethod().equals("PUT")){bytes.put(path,exchange.getRequestBody().readAllBytes());puts.computeIfAbsent(path,k->new AtomicInteger()).incrementAndGet();exchange.sendResponseHeaders(201,-1);}
   else {byte[] content=bytes.get(path);if(content==null)exchange.sendResponseHeaders(404,-1);else {exchange.sendResponseHeaders(200,content.length);exchange.getResponseBody().write(content);}}
   exchange.close();
  });server.start();
  try {
   Path tmp=Files.createTempDirectory("modustro-maven-record-");Path jar=tmp.resolve("library-1.0-SNAPSHOT-javadoc.jar");Files.writeString(jar,"unchanged documentation");
   var payload=new AIcPublishingPayload(AInPublishingOutputKind.NATIVE_PRODUCT_DOCUMENTATION,AInPublishingStability.SNAPSHOT,"test:library","1.0-SNAPSHOT",List.of(new AIcPublishingPayloadFile(jar,jar.getFileName().toString())),Map.of("groupId","test","artifactId","library","version","1.0-SNAPSHOT","technologyKind","java","classifier","javadoc","extension","jar"));
   var context=Map.<String,Object>of("RepositoryId","pub.test","Invocation",Map.of("Id","mock-http","StartedAt","2026-10-04T20:00:00.123456789Z"));
   var endpoint=Map.<String,Object>of("id","remote","publishingUrl","http://127.0.0.1:"+server.getAddress().getPort()+"/maven/","publishingAdapter","maven-repository");
   var jobs=new AIcPublicationPlanner().plan(payload,Map.of("publishingEnabled",true,"endpointPublications",List.of(endpoint)),context,tmp);
   var adapter=new AIcMavenRepositoryPublishingAdapter();
   var main=jobs.get(0).payloadFactory().prepare(null,null);
   var record=jobs.get(1).payloadFactory().prepare(main,new AIcPublishingEndpointResult("remote/standard",true,true,false,1,Duration.ZERO,null));
   var progress=new AIiPublishingProgressReporter(){public void started(String m){}public void progress(long c,long t,String u,String m){}public void indeterminate(String m){}public void completed(String m){}};
   for(int repeat=0;repeat<2;repeat++)for(int index=0;index<2;index++){
    var published=index==0?main:record;
    adapter.publish(new AIcPublishingAttemptContext(jobs.get(index).endpoint(),published,1,1,10000L,Instant.now().plusSeconds(10),Map.of(),()->false,progress));
   }
   String base="/maven/test/library/1.0-SNAPSHOT/";
   String original=main.files().get(0).logicalName();String sidecar=record.files().get(0).logicalName();
   assertEquals(sidecar,original+".modustro-build-record.yml");
   assertEquals(puts.get(base+original).get(),1);assertEquals(puts.get(base+sidecar).get(),1);
   var metadata=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes.get(base+"maven-metadata.xml")));
   var versions=metadata.getElementsByTagName("snapshotVersion");assertEquals(versions.getLength(),2);
   var extensions=new HashSet<String>();var values=new HashSet<String>();
   for(int i=0;i<versions.getLength();i++){var element=(org.w3c.dom.Element)versions.item(i);extensions.add(element.getElementsByTagName("extension").item(0).getTextContent());values.add(element.getElementsByTagName("value").item(0).getTextContent());assertEquals(element.getElementsByTagName("classifier").item(0).getTextContent(),"javadoc");}
   assertEquals(extensions,Set.of("jar","jar.modustro-build-record.yml"));assertEquals(values,Set.of(main.version()));
   Files.writeString(main.files().get(0).path(),"different bytes");
   try{adapter.publish(new AIcPublishingAttemptContext(jobs.get(0).endpoint(),main,1,1,10000L,Instant.now().plusSeconds(10),Map.of(),()->false,progress));fail("An immutable publication was overwritten");}catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("immutable"));}
  }finally{server.stop(0);}
 }
}
