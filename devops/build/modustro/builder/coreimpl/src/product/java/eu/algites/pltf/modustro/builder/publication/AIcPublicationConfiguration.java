package eu.algites.pltf.modustro.builder.publication;
import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputTypeGroup;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationOutputKind;
import java.util.*;
/** Core-owned sparse descriptor projection. Expand each hierarchy layer before merging layers. */
public final class AIcPublicationConfiguration {
 private AIcPublicationConfiguration(){}
 public static List<String> outputs(String selector){
  for(var g:AInBuildOutputTypeGroup.values())if(g.descriptorName().equals(selector))return g.outputs();
  for(var k:AInPublicationOutputKind.values())if(k.descriptorName().equals(selector))return List.of(selector);
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
    if(key.equals("Publications")||key.equals("PostPublicationActions")||key.equals("Configuration"))continue;
    if(!Set.of("Id","OutputSelector","TechnologyKind","Enabled","PublicationEnabled","Classifier","Extension","PostPublicationActionAdapter","TargetPublicationEndpointId","Order","FailurePolicy","RetryCount","WaitForNextAttemptMillis","AttemptTimeoutMillis","ShowProgressIfPossible","PublicationUri","PublicationAdapter","PublicationCredentialProfile","PublicationOrder","PublicationFailurePolicy","PublicationRetryCount","PublicationWaitForNextAttemptMillis","PublicationAttemptTimeoutMillis","ShowPublicationProgressIfPossible").contains(key))throw new IllegalArgumentException("Unknown publication property "+p+key);
    String value=entry.getValue();Object typed=value;
    if(Set.of("Enabled","PublicationEnabled","ShowPublicationProgressIfPossible","ShowProgressIfPossible").contains(key)){
     if(!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException(p+key+" must be boolean.");typed=Boolean.valueOf(value);
    } else if(Set.of("PublicationOrder","PublicationRetryCount","PublicationWaitForNextAttemptMillis","PublicationAttemptTimeoutMillis","Order","RetryCount","WaitForNextAttemptMillis","AttemptTimeoutMillis").contains(key)){
     long n=Long.parseLong(value);if(!Set.of("PublicationOrder","Order").contains(key)&&(n<0||Set.of("PublicationAttemptTimeoutMillis","AttemptTimeoutMillis").contains(key)&&n==0))throw new IllegalArgumentException(p+key+" out of range.");typed=n;
    }
    item.put(key,typed);
   }
   String configurationPrefix=p+"Configuration.";
   LinkedHashMap<String,Object> configuration=new LinkedHashMap<>();
   for(var entry:values.entrySet())if(entry.getKey().startsWith(configurationPrefix)){
    String configurationKey=entry.getKey().substring(configurationPrefix.length());
    if(!configurationKey.isBlank())configuration.put(configurationKey,entry.getValue());
   }
   if(!configuration.isEmpty())item.put("Configuration",Collections.unmodifiableMap(configuration));
   for(String child:List.of("Publications","PostPublicationActions"))if(values.containsKey(p+child)||values.keySet().stream().anyMatch(k->k.startsWith(p+child+".")))item.put(child,list(values,p+child));
   if(!prefix.equals("OutputPublications")){
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
    if(Set.of("Publications","PostPublicationActions").contains(e.getKey()))value.put(e.getKey(),merge((List<Map<String,Object>>)value.get(e.getKey()),(List<Map<String,Object>>)e.getValue()));else value.put(e.getKey(),e.getValue());
   }items.put(id,Collections.unmodifiableMap(value));
  }return List.copyOf(items.values());
 }

 public static Map<String,String> expand(Map<String,String> values){
  LinkedHashMap<String,Map<String,Object>> policies=new LinkedHashMap<>();
  TreeSet<Integer> indices=new TreeSet<>();for(String key:values.keySet())if(key.matches("OutputPublications\\.\\d+\\.OutputSelector"))indices.add(Integer.parseInt(key.split("\\.")[1]));
  List<Integer> ordered=new ArrayList<>(indices);ordered.sort(Comparator.comparingInt(n->rank(values.get("OutputPublications."+n+".OutputSelector"))));
  for(int n:ordered){String p="OutputPublications."+n;String technology=values.get(p+".TechnologyKind");
   if(technology==null||technology.isBlank())throw new IllegalArgumentException(p+" requires TechnologyKind.");
   if(!Set.of("java","python","mps","modustro").contains(technology))throw new IllegalArgumentException("Invalid TechnologyKind "+technology);
   for(String output:outputs(values.get(p+".OutputSelector")))
    apply(policies,technology+"."+output,branch(values,p+".Snapshot"),branch(values,p+".Release"));
  }
  
  Map<String,String> result=new LinkedHashMap<>(values);for(var e:policies.entrySet())flatten(result,e.getKey(),e.getValue());return result;
 }
 private static Map<String,Object> branch(Map<String,String> values,String prefix){
  Map<String,Object> b=new LinkedHashMap<>();String enabled=values.get(prefix+".PublicationEnabled");
  if(enabled!=null){if(!enabled.equals("true")&&!enabled.equals("false"))throw new IllegalArgumentException(prefix+" PublicationEnabled must be boolean.");b.put("PublicationEnabled",Boolean.valueOf(enabled));}
  String p=prefix+".PublicationEndpoints";
  if(values.containsKey(p)||values.keySet().stream().anyMatch(k->k.startsWith(p+".")))b.put("PublicationEndpoints",list(values,p));return b;
 }
 private static void apply(Map<String,Map<String,Object>> policies,String output,Map<String,Object> snapshot,Map<String,Object> release){
  if(snapshot.isEmpty()&&release.isEmpty())return;Map<String,Object> policy=policies.computeIfAbsent(output,k->new LinkedHashMap<>());
  mergeBranch(policy,"Snapshot",snapshot);mergeBranch(policy,"Release",release);
 }
 private static void mergeBranch(Map<String,Object> policy,String key,Map<String,Object> change){
  Map<String,Object> branch=new LinkedHashMap<>((Map<String,Object>)policy.getOrDefault(key,Map.of()));
  if(change.containsKey("PublicationEnabled"))branch.put("PublicationEnabled",change.get("PublicationEnabled"));
  if(change.containsKey("PublicationEndpoints"))branch.put("PublicationEndpoints",merge((List<Map<String,Object>>)branch.get("PublicationEndpoints"),(List<Map<String,Object>>)change.get("PublicationEndpoints")));
  policy.put(key,branch);
 }
 private static void flatten(Map<String,String> values,String prefix,Object value){
  if(value instanceof Map<?,?> m){for(var e:m.entrySet())flatten(values,prefix+"."+e.getKey(),e.getValue());}
  else if(value instanceof List<?> list){values.keySet().removeIf(k->k.equals(prefix)||k.startsWith(prefix+"."));if(list.isEmpty())values.put(prefix,"[]");else for(int n=0;n<list.size();n++)flatten(values,prefix+"."+n,list.get(n));}
  else values.put(prefix,Objects.toString(value,""));
 }
}
