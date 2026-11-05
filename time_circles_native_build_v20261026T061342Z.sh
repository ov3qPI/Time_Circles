#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
BUILDER_VERSION=time_circles_native_build_v20261026T061342Z
BASE=/storage/emulated/0/Documents
ZIP="${1:-$BASE/zip/time_circles_native_kotlin_v20261026T061342Z.zip}"
MODE=build
if [[ "${1:-}" == --bundle-only ]];then MODE=bundle;shift;ZIP="${1:-$BASE/zip/time_circles_native_kotlin_v20261026T061342Z.zip}";fi
[[ -f "$ZIP" ]] || { printf 'Missing project ZIP: %s\n' "$ZIP" >&2;exit 1; }
for exe in python kotlinc javac aapt2 java apksigner unzip zip keytool;do command -v "$exe" >/dev/null || { printf 'Missing installed Termux tool: %s\n' "$exe" >&2;exit 1; };done
WORK="$(mktemp -d "$HOME/.time_circles_build_XXXXXX")"
STAGED=""
cleanup() { rc=$?;trap - EXIT INT TERM;if [[ -n "$STAGED" ]];then rm -f -- "$STAGED";fi;rm -rf -- "$WORK";printf 'Temporary workspace deleted; exit code %s\n' "$rc";exit "$rc"; }
trap cleanup EXIT INT TERM
printf 'Temporary workspace: %s\n' "$WORK"
unzip -q "$ZIP" -d "$WORK"
mapfile -t MANIFESTS < <(find "$WORK" -mindepth 2 -maxdepth 2 -type f -name AndroidManifest.xml)
[[ "${#MANIFESTS[@]}" -eq 1 ]] || { printf 'Expected one Android project at ZIP root\n' >&2;exit 1; }
PROJECT="$(dirname "${MANIFESTS[0]}")"
ROOT="$(basename "$PROJECT")"
VENDOR="$PROJECT/vendor"
if [[ -f "$VENDOR/manifest.json" ]];then
python "$PROJECT/vendor_integrity_v20261026T061342Z.py" verify "$VENDOR"
else
printf 'One-time dependency bundling: updating the project ZIP itself\n'
mkdir -p "$VENDOR/jars" "$VENDOR/tools"
python "$PROJECT/resolve_deps_v20261026T061342Z.py" "$VENDOR/jars"
python "$PROJECT/fetch_d8_v20261026T061342Z.py" "$VENDOR/tools/r8-9.5.26.jar"
python "$PROJECT/vendor_integrity_v20261026T061342Z.py" record "$VENDOR"
STAGED="${ZIP%.zip}.staging_$$_$(date -u +%Y%m%dT%H%M%SZ).zip"
(cd "$WORK";zip -q -r "$STAGED" "$ROOT")
unzip -tqq "$STAGED"
mv -f -- "$STAGED" "$ZIP"
STAGED=""
printf 'Project ZIP updated with build dependencies: %s\n' "$ZIP"
fi
if [[ "$MODE" == bundle ]];then printf 'Bundle-only complete; future builds use this ZIP offline\n';exit 0;fi
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
ANDROID_JAR="$(find "$SDK/platforms" -maxdepth 2 -name android.jar 2>/dev/null | sort -V | tail -1)"
[[ -f "$ANDROID_JAR" ]] || { printf 'Missing Android SDK platform android.jar under %s/platforms\n' "$SDK" >&2;exit 1; }
mkdir -p "$WORK/generated" "$WORK/classes" "$WORK/dex" "$WORK/tmp" "$BASE/apk"
export TMPDIR="$WORK/tmp"
printf 'Compiling Android resources\n'
aapt2 compile --dir "$PROJECT/res" -o "$WORK/resources.zip"
aapt2 link -o "$WORK/resources.apk" -I "$ANDROID_JAR" --manifest "$PROJECT/AndroidManifest.xml" -A "$PROJECT/assets" --java "$WORK/generated" --min-sdk-version 26 --target-sdk-version 34 "$WORK/resources.zip"
printf 'Compiling R.java\n'
find "$WORK/generated" -name '*.java' -exec javac --release 8 -cp "$ANDROID_JAR" -d "$WORK/classes" {} +
CLASSPATH="$ANDROID_JAR:$WORK/classes"
for jar in "$VENDOR/jars"/*.jar;do CLASSPATH="$CLASSPATH:$jar";done
printf 'Compiling Kotlin directly\n'
kotlinc -J-Djava.io.tmpdir="$WORK/tmp" "$PROJECT/src" -jvm-target 1.8 -classpath "$CLASSPATH" -no-reflect -include-runtime -d "$WORK/time_circles.jar"
printf 'Generating DEX with bundled D8 9.5.26\n'
(cd "$WORK/classes";zip -q -r "$WORK/generated-r.jar" .)
java -Xmx1024m -cp "$VENDOR/tools/r8-9.5.26.jar" com.android.tools.r8.D8 --release --min-api 26 --lib "$ANDROID_JAR" --output "$WORK/dex" "$WORK/time_circles.jar" "$WORK/generated-r.jar" "$VENDOR/jars"/*.jar
printf 'Packaging APK\n'
cp "$WORK/resources.apk" "$WORK/unaligned.apk"
(cd "$WORK/dex";zip -q -0 "$WORK/unaligned.apk" classes*.dex)
python "$PROJECT/pack_jar_resources_v20261105T155723Z.py" "$WORK/unaligned.apk" "$VENDOR/jars"
if command -v zipalign >/dev/null 2>&1;then zipalign -f 4 "$WORK/unaligned.apk" "$WORK/aligned.apk";else python "$PROJECT/align_apk_v20261026T061342Z.py" "$WORK/unaligned.apk" "$WORK/aligned.apk";fi
KEYSTORE="${TIME_CIRCLES_KEYSTORE:-$HOME/.android/time_circles_signing.jks}"
if [[ ! -f "$KEYSTORE" ]];then
[[ -n "${TIME_CIRCLES_KEYSTORE:-}" ]] && { printf 'Specified signing key is missing: %s\n' "$KEYSTORE" >&2;exit 1; }
mkdir -p "$HOME/.android"
umask 077
keytool -genkeypair -noprompt -storetype PKCS12 -keystore "$KEYSTORE" -alias androiddebugkey -keyalg RSA -keysize 3072 -validity 10000 -storepass android -keypass android -dname "CN=Time Circles, O=Personal Open Source, C=US"
fi
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
TARGET="$BASE/apk/time_circles_naPHVO_v${STAMP}.apk"
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out "$WORK/signed.apk" "$WORK/aligned.apk"
apksigner verify --verbose "$WORK/signed.apk"
cp "$WORK/signed.apk" "$TARGET"
printf 'APK built: %s\n' "$TARGET"
if command -v termux-open >/dev/null 2>&1;then termux-open --view --content-type application/vnd.android.package-archive "$TARGET" || true;fi
