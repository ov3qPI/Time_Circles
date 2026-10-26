import os,sys,re,urllib.request,xml.etree.ElementTree as ET
from pathlib import Path
RESOLVER_VERSION="resolve_deps_v20261026T061342Z"
BASE="https://repo.maven.apache.org/maven2/"
DEST=Path(sys.argv[1]);DEST.mkdir(parents=True,exist_ok=True)
MODELS={};SEEN=set();COUNT=0
EXCLUDED={("org.easymock","easymock"),("org.objenesis","objenesis"),("junit","junit"),("org.hamcrest","hamcrest-core")}
NS="{http://maven.apache.org/POM/4.0.0}"
def children(node,path):
    if node is None:return []
    for name in path.split("/"):
        node=node.find(NS+name)
        if node is None:return []
    return list(node)
def find(node,path):
    if node is None:return None
    for name in path.split("/"):
        node=node.find(NS+name)
        if node is None:return None
    return node
def val(node,path,default=""):
    v=find(node,path)
    return v.text.strip() if v is not None and v.text else default
def fetch(path):
    req=urllib.request.Request(BASE+path,headers={"User-Agent":"TimeCirclesDirectBuild/1"})
    with urllib.request.urlopen(req,timeout=40) as response:return response.read()
def coords_path(group,name,version,ext):return f"{group.replace('.','/')}/{name}/{version}/{name}-{version}.{ext}"
def model(group,name,version):
    key=(group,name,version)
    if key in MODELS:return MODELS[key]
    if len(MODELS)>100:raise RuntimeError("Dependency graph exceeded safety limit")
    root=ET.fromstring(fetch(coords_path(group,name,version,"pom")))
    parent=find(root,"parent")
    inherited={};managed={}
    if parent is not None:
        pg=val(parent,"groupId");pa=val(parent,"artifactId");pv=val(parent,"version")
        if pg and pa and pv:
            inherited,managed,_=model(pg,pa,pv)
            inherited=inherited.copy();managed=managed.copy()
    group=val(root,"groupId",group);version=val(root,"version",version)
    props={**inherited,"project.groupId":group,"project.artifactId":name,"project.version":version,"pom.groupId":group,"pom.version":version}
    properties=find(root,"properties")
    if properties is not None:
        for child in properties:
            if child.text:props[child.tag.split("}")[-1]]=child.text.strip()
    def expand(value):
        for _ in range(15):
            revised=re.sub(r"\$\{([^}]+)\}",lambda match:props.get(match.group(1),match.group(0)),value)
            if revised==value:break
            value=revised
        return value
    props={k:expand(v) for k,v in props.items()}
    management=find(root,"dependencyManagement/dependencies")
    if management is not None:
        for d in list(management):
            dg=expand(val(d,"groupId"));da=expand(val(d,"artifactId"));dv=expand(val(d,"version"))
            if val(d,"scope")=="import" and val(d,"type")=="pom" and dg and da and dv:
                _,bom,_=model(dg,da,dv);managed.update(bom)
            elif dg and da and dv:managed[(dg,da)]=dv
    deps=[];dep_node=find(root,"dependencies")
    if dep_node is not None:
        for d in list(dep_node):
            if val(d,"scope","compile") in ("test","provided","system") or val(d,"optional")=="true":continue
            if val(d,"type","jar") not in ("jar","bundle","pom"):continue
            if val(d,"classifier"):continue
            dg=expand(val(d,"groupId"));da=expand(val(d,"artifactId"));dv=expand(val(d,"version")) or managed.get((dg,da),"")
            if "${" in dv:dv=expand(dv)
            if not dg or not da or not dv or "${" in dv:raise RuntimeError(f"Unresolved Maven dependency in {key}: {dg}:{da}:{dv}")
            deps.append((dg,da,dv))
    MODELS[key]=(props,managed,deps)
    return MODELS[key]
def resolve(group,name,version):
    global COUNT
    key=(group,name)
    if key in EXCLUDED:
        print(f"Skipping test framework: {group}:{name}",flush=True)
        return
    if key==("com.google.guava","guava") and version.endswith("-jre"):version=version[:-4]+"-android"
    if key in SEEN:return
    SEEN.add(key)
    props,managed,deps=model(group,name,version)
    data=fetch(coords_path(group,name,version,"jar"))
    if not data.startswith(b"PK"):raise RuntimeError(f"Bad JAR {group}:{name}:{version}")
    filename=f"{len(SEEN):03d}_{name}-{version}.jar"
    (DEST/filename).write_bytes(data)
    COUNT+=len(data)
    print(f"Library {len(SEEN)}: {group}:{name}:{version} ({len(data)//1024} KiB)",flush=True)
    if len(SEEN)>48 or COUNT>160000000:raise RuntimeError("Dependency set exceeded safety limits")
    for dg,da,dv in deps:resolve(dg,da,dv)
resolve("edu.jhuapl.ses","picante","1.0.0")
print(f"Resolved {len(SEEN)} libraries, {COUNT//1048576} MiB; all stored in disposable build directory",flush=True)
