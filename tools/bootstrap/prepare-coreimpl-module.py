#!/usr/bin/env python3
"""Add Gradle module metadata to a staged Maven snapshot containing a real JAR and POM."""
import hashlib, json, pathlib, sys, xml.etree.ElementTree as ET
if len(sys.argv)!=2:
    sys.exit('Usage: python3 prepare-coreimpl-module.py /path/to/maven/eu/algites/pltf/modustro/builder/pub.gov.Algites_devops.build.modustro.builder.coreimpl/1.0-SNAPSHOT')
d=pathlib.Path(sys.argv[1]).resolve()
poms=list(d.glob('*.pom')); jars=list(d.glob('*.jar'))
if len(poms)!=1 or len(jars)!=1: sys.exit('Expected exactly one .pom and one .jar in supplied Maven version directory')
pom,jar=poms[0],jars[0]
ns={'m':'http://maven.apache.org/POM/4.0.0'}
root=ET.parse(pom).getroot()
def val(parent,field):
    return parent.findtext('m:'+field, default='', namespaces=ns)
group=val(root,'groupId') or val(root.find('m:parent',ns),'groupId')
artifact=val(root,'artifactId'); version=val(root,'version') or val(root.find('m:parent',ns),'version')
if not all((group,artifact,version)) or not jar.name.startswith(artifact+'-'+version): sys.exit('POM/JAR coordinates do not match')
deps={'java-api':[],'java-runtime':[]}
for dep in root.findall('m:dependencies/m:dependency',ns):
    scope=val(dep,'scope') or 'compile'
    if scope in ('test','provided','system','import'): continue
    g,a,v=val(dep,'groupId'),val(dep,'artifactId'),val(dep,'version')
    if not all((g,a,v)) or '${' in v: sys.exit('Unresolved dependency in POM; refusing inaccurate module metadata')
    entry={'group':g,'module':a,'version':{'requires':v}}
    deps['java-runtime'].append(entry)
    if scope=='compile': deps['java-api'].append(entry)
data=jar.read_bytes(); checks={alg:getattr(hashlib,alg)(data).hexdigest() for alg in ('sha512','sha256','sha1','md5')}
file={'name':jar.name,'url':jar.name,'size':len(data),**checks}
variants=[]
for name,usage in [('apiElements','java-api'),('runtimeElements','java-runtime')]:
    variants.append({'name':name,'attributes':{'org.gradle.category':'library','org.gradle.dependency.bundling':'external','org.gradle.libraryelements':'jar','org.gradle.usage':usage},'dependencies':deps[usage],'files':[file]})
module={'formatVersion':'1.1','component':{'group':group,'module':artifact,'version':version,'attributes':{'org.gradle.status':'integration'}},'createdBy':{'bootstrap':{'version':'2026-10-08'}},'variants':variants}
out=d/(artifact+'-'+version+'.module'); out.write_text(json.dumps(module,indent=2)+'\n')
for f in (pom,jar,out):
    data=f.read_bytes()
    for alg in ('sha1','sha256'):(d/(f.name+'.'+alg)).write_text(getattr(hashlib,alg)(data).hexdigest()+'\n')
print('Created',out,'with',len(deps['java-api']),'API and',len(deps['java-runtime']),'runtime dependencies')
