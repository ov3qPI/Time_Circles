import sys,urllib.request,zipfile
from pathlib import Path
FETCHER_VERSION="fetch_d8_v20261026T061342Z"
VERSION="9.5.26"
URL=f"https://dl.google.com/dl/android/maven2/com/android/tools/r8/{VERSION}/r8-{VERSION}.jar"
DEST=Path(sys.argv[1])
request=urllib.request.Request(URL,headers={"User-Agent":"TimeCirclesDirectBuild/1"})
with urllib.request.urlopen(request,timeout=90) as source,DEST.open("wb") as target:
    total=int(source.headers.get("Content-Length","0"));received=0;last=-1
    while True:
        data=source.read(262144)
        if not data:break
        target.write(data);received+=len(data)
        if total:
            mark=received*10//total
            if mark>last:last=mark;print(f"D8 download: {min(100,received*100//total)}%",flush=True)
if DEST.stat().st_size<1000000:raise RuntimeError("D8 download is unexpectedly small")
with zipfile.ZipFile(DEST) as bundle:
    if "com/android/tools/r8/D8.class" not in bundle.namelist():raise RuntimeError("Downloaded file is not the expected D8/R8 executable")
print(f"D8 {VERSION} verified ({DEST.stat().st_size//1048576} MiB); temporary workspace only",flush=True)
