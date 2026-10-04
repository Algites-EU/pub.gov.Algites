package eu.algites.pltf.modustro.builder.publication;
import eu.algites.pltf.modustro.builder.model.publication.*;
import java.nio.file.*;import java.security.*;import java.util.*;
/** Separate invocation provenance; JSON is the portable YAML 1.2 subset. */
public final class AIcBuildRecordPublicationProducer implements AIiPublicationProducer {
 public String producerId(){return "modustro-build-record";}
 public AIcPublishingPayload produce(AIcPublishingPayload parent,AIcPublishingEndpointResult result,Map<String,Object> context,Path directory)throws Exception{
  var files=new ArrayList<AIcPublishingPayloadFile>();var coords=new LinkedHashMap<>(parent.coordinates());
  String group=coords.get("groupId");if(group==null||group.isBlank())throw new IllegalArgumentException("Build record requires effective GroupId.");
  for(var file:parent.files()){
   if(file.logicalName().endsWith(".pom"))continue;
   Map<String,Object> record=new LinkedHashMap<>();record.put("BuildRecordVersion",1);
   record.put("Artifact",Map.of("RepositoryId",context.getOrDefault("RepositoryId","unknown"),"GroupId",group,"ArtifactCoordinateId",coords.getOrDefault("artifactId",parent.artifactIdentity()),"Version",coords.getOrDefault("logicalVersion",coords.getOrDefault("version",Objects.toString(parent.version(),"unknown")))));
   record.put("Output",Map.of("TechnologyKind",coords.getOrDefault("technologyKind","unknown"),"OutputType",parent.outputKind().descriptorName(),"PublishedVersion",Objects.toString(parent.version(),"unknown"),"Classifier",coords.getOrDefault("classifier",""),"Extension",coords.getOrDefault("technologyKind","").equals("java")?coords.getOrDefault("extension",extension(file.logicalName())):extension(file.logicalName()),"Files",List.of(Map.of("Filename",file.logicalName(),"Sha256",hash(file.path()),"Size",Files.size(file.path())))));
   for(String key:List.of("Invocation","Sources","Tools","OutputOrigin"))record.put(key,context.getOrDefault(key,key.equals("Sources")||key.equals("Tools")?List.of():Map.of("Kind","unknown")));
   record.put("ParentPublication",Map.of("Id",result.endpointId(),"Attempts",result.attempts()));
   String name=file.logicalName()+".modustro-build-record.yml";Path target=directory.resolve(name).normalize();
   if(!target.startsWith(directory.normalize()))throw new IllegalArgumentException("Invalid payload filename.");
   Files.createDirectories(target.getParent());Files.writeString(target,json(record)+"\n");files.add(new AIcPublishingPayloadFile(target,name));
  }
  if(files.isEmpty())throw new IllegalArgumentException("No file to record.");
  coords.put("extension",coords.getOrDefault("extension",extension(parent.files().get(0).logicalName()))+".modustro-build-record.yml");
  return new AIcPublishingPayload(parent.outputKind(),parent.stability(),parent.artifactIdentity(),parent.version(),files,coords);
 }
 public static String extension(String name){if(name.endsWith(".modustro-build-record.yml"))return extension(name.substring(0,name.length()-".modustro-build-record.yml".length()))+".modustro-build-record.yml";return name.endsWith(".tar.gz")?"tar.gz":name.substring(name.lastIndexOf('.')+1);}
 public static String hash(Path path)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))>=0)d.update(buffer,0,n);}return HexFormat.of().formatHex(d.digest());}
 public static String json(Object value){
  if(value==null)return "null";if(value instanceof Boolean||value instanceof Number)return value.toString();
  if(value instanceof Map<?,?> map)return "{"+String.join(",",map.entrySet().stream().map(e->json(e.getKey().toString())+":"+json(e.getValue())).toList())+"}";
  if(value instanceof Iterable<?> list){List<String> items=new ArrayList<>();for(Object item:list)items.add(json(item));return "["+String.join(",",items)+"]";}
  String text=value.toString();StringBuilder out=new StringBuilder("\"");for(char ch:text.toCharArray()){switch(ch){case '\\'->out.append("\\\\");case '"'->out.append("\\\"");case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");default->{if(ch<32)out.append(String.format("\\u%04x",(int)ch));else out.append(ch);}}}return out.append('"').toString();
 }
}
