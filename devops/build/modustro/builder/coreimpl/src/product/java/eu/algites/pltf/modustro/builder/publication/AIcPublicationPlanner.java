package eu.algites.pltf.modustro.builder.publication;
import eu.algites.pltf.modustro.builder.model.publication.*;
import java.net.URI;import java.nio.file.*;import java.time.*;import java.time.format.DateTimeFormatter;import java.util.*;
/** Converts endpoint forms and explicit extension trees to independent scheduler jobs. */
public final class AIcPublicationPlanner {
 private final Map<String,AIiPublicationProducer> producers;
 public AIcPublicationPlanner(){this(builtins());}
 private static List<AIiPublicationProducer> builtins(){var p=new ArrayList<AIiPublicationProducer>();p.add(new AIcBuildRecordPublicationProducer());ServiceLoader.load(AIiPublicationProducer.class,AIcPublicationPlanner.class.getClassLoader()).forEach(p::add);return p;}
 public AIcPublicationPlanner(List<AIiPublicationProducer> items){Map<String,AIiPublicationProducer> p=new LinkedHashMap<>();for(var item:items)if(p.putIfAbsent(item.producerId(),item)!=null)throw new IllegalArgumentException("Duplicate publication producer "+item.producerId());producers=Map.copyOf(p);}
 public List<AIcPublicationJob> plan(AIcPublishingPayload root,Map<String,Object> config,Map<String,Object> context,Path directory)throws Exception{
  if(!Boolean.TRUE.equals(config.get("publishingEnabled")))return List.of();
  if(root.coordinates().getOrDefault("groupId","").isBlank())throw new IllegalArgumentException("Publication requires effective GroupId before upload.");
  var jobs=new ArrayList<AIcPublicationJob>();var identities=new HashSet<String>();var targets=new HashSet<String>();
  Path frozen=directory.resolve("payloads").resolve(UUID.randomUUID().toString()).toAbsolutePath();var files=new ArrayList<AIcPublishingPayloadFile>();
  for(var file:root.files()){Path path=frozen.resolve(file.logicalName()).normalize();if(!path.startsWith(frozen))throw new IllegalArgumentException("Invalid payload filename.");Files.createDirectories(path.getParent());Files.copy(file.path(),path);files.add(new AIcPublishingPayloadFile(path,file.logicalName()));}
  root=new AIcPublishingPayload(root.outputKind(),root.stability(),root.artifactIdentity(),root.version(),files,root.coordinates());
  for(var declaration:maps(config.get("endpointPublications"))){var endpoint=endpoint(declaration,null,Objects.toString(declaration.get("id"),Objects.toString(declaration.get("Id"),"")));if(!endpoint.enabled())continue;
   List<Map<String,Object>> forms=declaration.containsKey("publications")&&declaration.get("publications")!=null?maps(declaration.get("publications")):declaration.containsKey("Publications")?maps(declaration.get("Publications")):List.of(Map.of("Id","standard"));
   int index=0;for(var form:forms){if(Boolean.FALSE.equals(form.get("Enabled")))continue;String id=endpoint.id()+"/"+form.get("Id");if(!identities.add(id))throw new IllegalArgumentException("Duplicate publication path "+id);
    AIcPublishingPayload payload=form(root,form,endpoint,index++==0,context);
    for(var file:payload.files())if(!targets.add(endpoint.publishingUrl()+"/"+file.logicalName()))throw new IllegalArgumentException("Duplicate publication target "+file.logicalName());
    var main=withId(endpoint,id);jobs.add(new AIcPublicationJob(id,null,main,(parent,result)->payload));
    var extensions=new ArrayList<>(maps(form.get("ExtendedPublications")));
    if(extensions.stream().noneMatch(e->"build-record".equals(e.get("Id"))))extensions.add(Map.of("Id","build-record","PublicationProducer","modustro-build-record","PublishingEnabled",true));
    extend(jobs,identities,id,main,extensions,context,directory,0);
   }
  }return List.copyOf(jobs);
 }
 private void extend(List<AIcPublicationJob> jobs,Set<String> ids,String parentId,AIcPublishingEndpoint parent,List<Map<String,Object>> items,Map<String,Object> context,Path directory,int depth){
  if(depth>64)throw new IllegalArgumentException("ExtendedPublications nesting exceeds 64.");Set<String> siblings=new HashSet<>();
  for(var item:items){String local=Objects.toString(item.get("Id"),"");if(!local.matches("[a-z0-9]+(?:-[a-z0-9]+)*")||!siblings.add(local))throw new IllegalArgumentException("Extension Id must be unique among siblings.");
   if(Boolean.FALSE.equals(item.get("PublishingEnabled"))||Boolean.FALSE.equals(item.get("Enabled")))continue;
   String id=parentId+"/"+local;if(!ids.add(id))throw new IllegalArgumentException("Duplicate publication path "+id);
   String producerId=Objects.toString(item.get("PublicationProducer"),"");var producer=producers.get(producerId);if(producer==null)throw new IllegalArgumentException("Unknown PublicationProducer '"+producerId+"'.");
   var endpoint=endpoint(item,parent,id);if(!endpoint.enabled())continue;
   if(endpoint.publishingOrder()<parent.publishingOrder())throw new IllegalArgumentException("Extension PublishingOrder cannot precede its parent.");
   if(producerId.equals("modustro-build-record")&&endpoint.publishingAdapter().equals("python-repository"))throw new IllegalArgumentException("Python package indexes cannot store YAML build records. Configure a separate URL and adapter or explicitly disable the record.");
   Path out=directory.resolve("extensions").resolve(UUID.randomUUID().toString()).toAbsolutePath();
   jobs.add(new AIcPublicationJob(id,parentId,endpoint,(payload,result)->producer.produce(payload,result,context,out)));
   extend(jobs,ids,id,endpoint,maps(item.get("ExtendedPublications")),context,directory,depth+1);
  }
 }
 private static AIcPublishingPayload form(AIcPublishingPayload root,Map<String,Object> form,AIcPublishingEndpoint endpoint,boolean pom,Map<String,Object> context){
  Map<String,String> coords=new LinkedHashMap<>(root.coordinates());List<AIcPublishingPayloadFile> files=new ArrayList<>();String published=root.version();
  boolean java=coords.getOrDefault("technologyKind","").equals("java")||endpoint.publishingAdapter().startsWith("maven-");
  String classifier=Objects.toString(form.get("Classifier"),coords.getOrDefault("classifier",""));String extension=Objects.toString(form.get("Extension"),coords.getOrDefault("extension","jar"));
  if(form.containsKey("Classifier")||form.containsKey("Extension")){if(!java)throw new IllegalArgumentException("Classifier/Extension requires a Maven-compatible publication form.");if(!classifier.matches("[A-Za-z0-9_.-]*")||!extension.matches("[A-Za-z0-9][A-Za-z0-9_.-]*"))throw new IllegalArgumentException("Unsafe Classifier/Extension.");}
  if(java&&root.stability()==AInPublishingStability.SNAPSHOT&&endpoint.publishingAdapter().equals("maven-repository")){
   String base=coords.getOrDefault("version",Objects.toString(root.version(),""));if(!base.endsWith("-SNAPSHOT"))throw new IllegalArgumentException("Maven snapshot requires -SNAPSHOT logical version.");
   var invocation=(Map<String,Object>)context.getOrDefault("Invocation",Map.of());Instant now=Instant.parse(Objects.toString(invocation.getOrDefault("StartedAt",Instant.now().toString())));
   String stamp=DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss").withZone(ZoneOffset.UTC).format(now);String number=Long.toString(now.getNano()+1L);published=base.substring(0,base.length()-9)+"-"+stamp+"-"+number;coords.put("snapshotTimestamp",stamp);coords.put("snapshotBuildNumber",number);
  }
  for(var f:root.files()){
   if(f.logicalName().endsWith(".pom")){if(pom)files.add(new AIcPublishingPayloadFile(f.path(),java?coords.get("artifactId")+"-"+published+".pom":f.logicalName()));continue;}
   String name=f.logicalName();if(java&&(form.containsKey("Classifier")||form.containsKey("Extension")))name=coords.get("artifactId")+"-"+published+(classifier.isEmpty()?"":"-"+classifier)+"."+extension;
   else if(java&&!Objects.equals(published,root.version()))name=name.replace(Objects.toString(root.version(),""),published);
   files.add(new AIcPublishingPayloadFile(f.path(),name));
  }
  if(java){coords.put("classifier",classifier);coords.put("extension",extension);}return new AIcPublishingPayload(root.outputKind(),root.stability(),root.artifactIdentity(),published,files,coords);
 }
 public static AIcPublishingEndpoint endpoint(Map<String,Object> item,AIcPublishingEndpoint parent,String id){
  java.util.function.BiFunction<String,Object,Object> v=(key,defaultValue)->item.containsKey(key)?item.get(key):item.getOrDefault(Character.toLowerCase(key.charAt(0))+key.substring(1),defaultValue);
  String url=Objects.toString(v.apply("PublishingUrl",parent==null?null:parent.publishingUrl()),null);String adapter=Objects.toString(v.apply("PublishingAdapter",parent==null?null:parent.publishingAdapter()),null);
  return new AIcPublishingEndpoint(id,!Boolean.FALSE.equals(v.apply("Enabled",true)),url==null?null:URI.create(url),adapter,Objects.toString(v.apply("PublishingCredentialProfile",parent==null?null:parent.publishingCredentialProfile()),null),((Number)v.apply("PublishingOrder",parent==null?0:parent.publishingOrder())).intValue(),AInPublishingFailurePolicy.valueOf(Objects.toString(v.apply("PublishingFailurePolicy",parent==null?"FAIL_BUILD_ON_PUBLISHING_FAILURE":parent.publishingFailurePolicy()))),((Number)v.apply("PublishingRetryCount",parent==null?0:parent.publishingRetryCount())).intValue(),((Number)v.apply("PublishingRetryDelayMillis",parent==null?1000L:parent.publishingRetryDelayMillis())).longValue(),v.apply("PublishingAttemptTimeoutMillis",parent==null?null:parent.publishingAttemptTimeoutMillis())==null?null:((Number)v.apply("PublishingAttemptTimeoutMillis",parent==null?null:parent.publishingAttemptTimeoutMillis())).longValue(),!Boolean.FALSE.equals(v.apply("ShowPublishingProgressIfPossible",parent==null?true:parent.showPublishingProgressIfPossible())));
 }
 private static AIcPublishingEndpoint withId(AIcPublishingEndpoint e,String id){return new AIcPublishingEndpoint(id,e.enabled(),e.publishingUrl(),e.publishingAdapter(),e.publishingCredentialProfile(),e.publishingOrder(),e.publishingFailurePolicy(),e.publishingRetryCount(),e.publishingRetryDelayMillis(),e.publishingAttemptTimeoutMillis(),e.showPublishingProgressIfPossible());}
 private static List<Map<String,Object>> maps(Object value){if(value==null)return List.of();if(!(value instanceof List<?> list))throw new IllegalArgumentException("Publications must be a list.");return list.stream().map(i->{if(!(i instanceof Map<?,?> m))throw new IllegalArgumentException("Publication item must be an object.");return (Map<String,Object>)m;}).toList();}
}
