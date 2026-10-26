import hashlib,json,sys,zipfile
from pathlib import Path
VENDOR_VERSION="vendor_integrity_v20261026T061342Z"
mode=sys.argv[1];root=Path(sys.argv[2]);manifest=root/"manifest.json"
def files():return sorted([*root.glob("jars/*.jar"),*root.glob("tools/*.jar")])
def digest(path):
    h=hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b""):h.update(chunk)
    return h.hexdigest()
if mode=="record":
    jars=sorted(root.glob("jars/*.jar"));tool=root/"tools/r8-9.5.26.jar"
    if len(jars)!=11 or not tool.is_file():raise SystemExit("Incomplete vendored dependencies")
    for jar in [*jars,tool]:
        with zipfile.ZipFile(jar) as archive:
            if archive.testzip() is not None:raise SystemExit(f"Invalid JAR: {jar.name}")
    with zipfile.ZipFile(tool) as archive:
        if "com/android/tools/r8/D8.class" not in archive.namelist():raise SystemExit("D8 class missing")
    record={"version":VENDOR_VERSION,"r8_version":"9.5.26","files":{str(path.relative_to(root)):digest(path) for path in files()}}
    manifest.write_text(json.dumps(record,sort_keys=True,separators=(",",":"))+'\n')
    print(f"Bundled {len(jars)} Picante dependency JARs and D8 9.5.26; SHA-256 recorded",flush=True)
elif mode=="verify":
    if not manifest.is_file():raise SystemExit("Missing vendor manifest")
    record=json.loads(manifest.read_text())
    if record.get("version")!=VENDOR_VERSION or record.get("r8_version")!="9.5.26":raise SystemExit("Unsupported vendor version")
    found={str(path.relative_to(root)):digest(path) for path in files()}
    if found!=record.get("files") or len([p for p in found if p.startswith("jars/")])!=11 or "tools/r8-9.5.26.jar" not in found:raise SystemExit("Vendored JAR checksums do not match project ZIP")
    print(f"Verified {len(found)} bundled JARs; no downloads required",flush=True)
else:raise SystemExit("Expected record or verify")
