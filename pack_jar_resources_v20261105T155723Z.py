import sys,zipfile,pathlib
SCRIPT_ID="pack_jar_resources_v20261105T155723Z"
apk=pathlib.Path(sys.argv[1]);jars=sorted(pathlib.Path(sys.argv[2]).glob("*.jar"));resources={}
for jar in jars:
    with zipfile.ZipFile(jar) as archive:
        for item in archive.infolist():
            name=item.filename
            if item.is_dir() or name.endswith((".class",".SF",".RSA",".DSA")) or name.upper()=="META-INF/MANIFEST.MF" or name.startswith("/") or ".." in pathlib.PurePosixPath(name).parts or item.file_size>4194304 or name in resources:continue
            resources[name]=(jar,name)
required="picante/time/parameters.properties"
if required not in resources:raise RuntimeError(f"Missing {required} in vendored JARs")
with zipfile.ZipFile(apk,"a") as result:
    existing=set(result.namelist())
    for name,(jar,entry) in resources.items():
        if name in existing:continue
        with zipfile.ZipFile(jar) as source:result.writestr(name,source.read(entry),compress_type=zipfile.ZIP_DEFLATED)
with zipfile.ZipFile(apk) as result:
    if required not in result.namelist():raise RuntimeError(f"APK missing {required}")
print(f"Packaged {len(resources)} JAR resources; verified {required}")
