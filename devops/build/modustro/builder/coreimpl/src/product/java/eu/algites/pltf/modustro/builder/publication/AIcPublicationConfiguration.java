package eu.algites.pltf.modustro.builder.publication;
import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputTypeGroup;
import eu.algites.pltf.modustro.builder.model.publication.AInPublishingOutputKind;
import java.util.*;
/** Core-owned sparse descriptor projection. Expand each hierarchy layer before merging layers. */
public final class AIcPublicationConfiguration {
 private AIcPublicationConfiguration(){}
 public static List<String> outputs(String selector){
  for(var g:AInBuildOutputTypeGroup.values())if(g.descriptorName().equals(selector))return g.outputs();
  for(var k:AInPublishingOutputKind.values())if(k.descriptorName().equals(selector))return List.of(selector);
  throw new IllegalArgumentException("Unknown OutputSelector '"+selector+"'.");
 }
 public static int rank(String selector){outputs(selector);return selector.equals("native_outputs")?0:outputs(selector).size()>1?1:2;}
 public static List<Map<String,Object>> list(Map<String,String> values,String prefix){
  if(values.containsKey(prefix)){
   if(values.get(prefix).equals("[]"))return List.of();
   throw new IllegalArgumentException(prefix+" must be a list.");
  }
  TreeSet<Integer> indices=new TreeSet<>();
  for(String key:values.keySet())if(key.startsWith(prefix+".")){
   String first=key.substring(prefix.length()+1).split("\\.")[0];
   try{indices.add(Integer.parseInt(first));}catch(NumberFormatException ignored){}
  }
  List<Map<String,Object>> result=new ArrayList<>();Set<String> ids=new HashSet<>();
  for(int index:indices){Map<String,Object> item=new LinkedHashMap<>();String p=prefix+"."+index+".";
   for(var entry:values.entrySet())if(entry.getKey().startsWith(p)){
    String key=entry.getKey().substring(p.length());if(key.contains("."))continue;
    if(key.equals("Publications")||key.equals("ExtendedPublications"))continue;
    if(!Set.of("Id","OutputSelector","TechnologyKind","Enabled","PublishingEnabled","Url","Stability","CredentialProfile","ResourceEndpointProviderAdapter","Classifier","Extension","PublicationProducer","PublishingUrl","PublishingAdapter","PublishingCredentialProfile","PublishingOrder","PublishingFailurePolicy","PublishingRetryCount","PublishingRetryDelayMillis","PublishingAttemptTimeoutMillis","ShowPublishingProgressIfPossible").contains(key))throw new IllegalArgumentException("Unknown publication/resource property "+p+key);
    String value=entry.getValue();Object typed=value;
    if(Set.of("Enabled","PublishingEnabled","ShowPublishingProgressIfPossible").contains(key)){
     if(!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException(p+key+" must be boolean.");typed=Boolean.valueOf(value);
    } else if(Set.of("PublishingOrder","PublishingRetryCount","PublishingRetryDelayMillis","PublishingAttemptTimeoutMillis").contains(key)){
     long n=Long.parseLong(value);if(!key.equals("PublishingOrder")&&(n<0||key.equals("PublishingAttemptTimeoutMillis")&&n==0))throw new IllegalArgumentException(p+key+" out of range.");typed=n;
    }
    item.put(key,typed);
   }
   for(String child:List.of("Publications","ExtendedPublications"))if(values.containsKey(p+child)||values.keySet().stream().anyMatch(k->k.startsWith(p+child+".")))item.put(child,list(values,p+child));
   if(!prefix.equals("OutputPublishing")){
    Object id=item.get("Id");if(id==null||!id.toString().matches("[a-z0-9]+(?:-[a-z0-9]+)*")||!ids.add(id.toString()))throw new IllegalArgumentException(prefix+" requires unique lowercase dash-separated Id values.");
   }
   result.add(Collections.unmodifiableMap(item));
  }return List.copyOf(result);
 }
 public static List<Map<String,Object>> merge(List<Map<String,Object>> base,List<Map<String,Object>> override){
  if(override==null)return base;if(override.isEmpty())return List.of();
  LinkedHashMap<String,Map<String,Object>> items=new LinkedHashMap<>();if(base!=null)for(var i:base)items.put(i.get("Id").toString(),i);
  for(var i:override){String id=i.get("Id").toString();Map<String,Object> value=new LinkedHashMap<>(items.getOrDefault(id,Map.of()));
   for(var e:i.entrySet()){
    if(Set.of("Publications","ExtendedPublications").contains(e.getKey()))value.put(e.getKey(),merge((List<Map<String,Object>>)value.get(e.getKey()),(List<Map<String,Object>>)e.getValue()));else value.put(e.getKey(),e.getValue());
   }items.put(id,Collections.unmodifiableMap(value));
  }return List.copyOf(items.values());
 }

 public static Map<String,String> expand(Map<String,String> values){
  LinkedHashMap<String,Map<String,Object>> policies=new LinkedHashMap<>();
  TreeSet<Integer> indices=new TreeSet<>();for(String key:values.keySet())if(key.matches("OutputPublishing\\.\\d+\\.OutputSelector"))indices.add(Integer.parseInt(key.split("\\.")[1]));
  List<Integer> ordered=new ArrayList<>(indices);ordered.sort(Comparator.comparingInt(n->rank(values.get("OutputPublishing."+n+".OutputSelector"))));
  for(int n:ordered){String p="OutputPublishing."+n;String technology=values.get(p+".TechnologyKind");
   if(technology!=null&&!Set.of("java","python","mps","modustro").contains(technology))throw new IllegalArgumentException("Invalid TechnologyKind "+technology);
   for(String output:outputs(values.get(p+".OutputSelector")))for(String tech:technology==null?List.of("","java.","python.","mps.","modustro."):List.of(technology+"."))
    apply(policies,tech+output,branch(values,p+".Snapshot"),branch(values,p+".Release"));
  }
  for(var kind:AInPublishingOutputKind.values())for(String tech:List.of("","java.","python.","mps.","modustro."))
   apply(policies,tech+kind.descriptorName(),branch(values,kind.descriptorName()+".Snapshot"),branch(values,kind.descriptorName()+".Release"));
  Map<String,String> result=new LinkedHashMap<>(values);for(var e:policies.entrySet())flatten(result,e.getKey(),e.getValue());return result;
 }
 private static Map<String,Object> branch(Map<String,String> values,String prefix){
  Map<String,Object> b=new LinkedHashMap<>();String enabled=values.get(prefix+".PublishingEnabled");
  if(enabled!=null){if(!enabled.equals("true")&&!enabled.equals("false"))throw new IllegalArgumentException(prefix+" PublishingEnabled must be boolean.");b.put("PublishingEnabled",Boolean.valueOf(enabled));}
  String p=prefix+".EndpointPublications";
  if(values.containsKey(p)||values.keySet().stream().anyMatch(k->k.startsWith(p+".")))b.put("EndpointPublications",list(values,p));return b;
 }
 private static void apply(Map<String,Map<String,Object>> policies,String output,Map<String,Object> snapshot,Map<String,Object> release){
  if(snapshot.isEmpty()&&release.isEmpty())return;Map<String,Object> policy=policies.computeIfAbsent(output,k->new LinkedHashMap<>());
  mergeBranch(policy,"Snapshot",snapshot);mergeBranch(policy,"Release",release);
 }
 private static void mergeBranch(Map<String,Object> policy,String key,Map<String,Object> change){
  Map<String,Object> branch=new LinkedHashMap<>((Map<String,Object>)policy.getOrDefault(key,Map.of()));
  if(change.containsKey("PublishingEnabled"))branch.put("PublishingEnabled",change.get("PublishingEnabled"));
  if(change.containsKey("EndpointPublications"))branch.put("EndpointPublications",merge((List<Map<String,Object>>)branch.get("EndpointPublications"),(List<Map<String,Object>>)change.get("EndpointPublications")));
  policy.put(key,branch);
 }
 private static void flatten(Map<String,String> values,String prefix,Object value){
  if(value instanceof Map<?,?> m){for(var e:m.entrySet())flatten(values,prefix+"."+e.getKey(),e.getValue());}
  else if(value instanceof List<?> list){values.keySet().removeIf(k->k.equals(prefix)||k.startsWith(prefix+"."));if(list.isEmpty())values.put(prefix,"[]");else for(int n=0;n<list.size();n++)flatten(values,prefix+"."+n,list.get(n));}
  else values.put(prefix,Objects.toString(value,""));
 }
}
