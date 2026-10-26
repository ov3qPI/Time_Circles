package cc0.timecircles.naPHVO
import android.Manifest
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import cc0.timecircles.naPHVO.ephem.KernelInstaller
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
private const val DIAGNOSTICS_ID="Diagnostics_v20261026T032840Z"
object DiagnosticsLog {
private val events=java.util.ArrayDeque<String>()
@Synchronized fun add(message:String) { if(events.size>=80) events.removeFirst();events.addLast("${Instant.now()} | ${message.take(600)}") }
@Synchronized fun all():List<String> = events.toList()
}
data class DiagnosticSnapshot(val currentMode:Boolean,val locationName:String,val coordinates:String,val gpsStatus:String,val gpsAgeSeconds:Long?,val gpsFresh:Boolean?,val selectedLocationCount:Int,val kernelStatus:String,val kernelReady:Boolean?,val anchorStatus:String,val anchorPresent:Boolean,val horizonSource:String,val riseSetCount:Int,val nextEvent:String,val synodicDay:String,val synodicFraction:String,val solsticeDay:String,val solsticeFraction:String,val longitudinalTime:String,val currentLongitudeFraction:String,val cacheKey:String?,val kernelRevision:Int,val foreground:Boolean)
object DiagnosticsReport {
fun make(context:Context,s:DiagnosticSnapshot):String=buildString {
fun section(v:String) { append("\n== ").append(v).append(" ==\n") }
fun entry(k:String,v:Any?) { append(k).append(": ").append(v ?: "unavailable").append('\n') }
append("TIME CIRCLES — DIAGNOSTICS\nGenerated: ").append(Instant.now()).append(" UTC\nSensitive: includes coordinates and local file metadata; inspect before sharing.\n")
section("Application")
entry("Source revision",SOURCE_VERSION);entry("Diagnostics revision",DIAGNOSTICS_ID);entry("Package",context.packageName)
val info=runCatching { context.packageManager.getPackageInfo(context.packageName,0) }.getOrNull()
entry("Version name",info?.versionName);entry("Version code",if(Build.VERSION.SDK_INT>=28) info?.longVersionCode else info?.versionCode)
entry("Application target SDK",context.applicationInfo.targetSdkVersion);entry("Application process PID",Process.myPid());entry("Process uptime ms",SystemClock.uptimeMillis());entry("Thread count",Thread.getAllStackTraces().size)
entry("App foreground",s.foreground);entry("Kernel revision counter",s.kernelRevision)
section("Selected location and GPS")
entry("Selection mode",if(s.currentMode) "Current Location (GPS only)" else "Manual location")
entry("Location",s.locationName);entry("Coordinates (decimal degrees)",s.coordinates);entry("Manual saved locations",s.selectedLocationCount)
entry("GPS precise permission",context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
entry("GPS provider enabled",GpsLocationSource.enabled(context));entry("GPS state",s.gpsStatus);entry("Last GPS fix fresh",s.gpsFresh)
entry("GPS fix age seconds",s.gpsAgeSeconds);entry("Location cache key",s.cacheKey)
section("Astronomy and calculations")
entry("Ephemeris status",s.kernelStatus);entry("SPK ready",s.kernelReady);entry("Cycle anchor status",s.anchorStatus);entry("Valid anchors loaded",s.anchorPresent)
entry("Solar horizon data",s.horizonSource);entry("0° rise/set crossing count",s.riseSetCount);entry("Next 0° crossing",s.nextEvent)
entry("Longitudinal time",s.longitudinalTime);entry("Longitudinal fraction",s.currentLongitudeFraction)
entry("Synodic day",s.synodicDay);entry("Synodic fraction",s.synodicFraction);entry("Solstice-year day",s.solsticeDay);entry("Solstice-year fraction",s.solsticeFraction)
entry("Solar elevation definition","0° geometric; no atmospheric refraction")
section("Planetary SPK and supporting kernels")
val t=SystemClock.elapsedRealtime();val valid=runCatching { KernelInstaller.isInstalled(context) }.getOrDefault(false)
entry("Kernel integrity check",if(valid) "SHA-256 VERIFIED" else "NO VALID ACTIVE KERNEL")
entry("Kernel check wall duration ms",SystemClock.elapsedRealtime()-t)
val meta=KernelInstaller.installedInfo(context)
entry("Active model",meta?.name);entry("SPK source",meta?.source);entry("Installed at",meta?.installedAt);entry("SPK bytes",meta?.bytes);entry("SPK SHA-256",meta?.sha256);entry("SPK absolute path",meta?.file?.absolutePath)
entry("Default download",KernelInstaller.defaultChoice.filename);entry("Supported preset downloads",KernelInstaller.choices.joinToString(", ") { it.filename })
val dir=KernelInstaller.installedKernelDir(context)
entry("Kernel directory",dir.absolutePath)
for(f in dir.listFiles().orEmpty().sortedBy { it.name }) { if(!f.name.endsWith(".partial")) entry("File ${f.name}","${f.length()} bytes; modified=${Instant.ofEpochMilli(f.lastModified())}; readable=${f.canRead()}") }
section("App storage and cached state")
val prefs=context.getSharedPreferences("time_circles_prefs",0)
entry("Saved selection is GPS",prefs.getBoolean("current_location_selected",true));entry("Saved locations JSON bytes",prefs.getString("locations_json","")?.length)
entry("Anchors cache JSON bytes",prefs.getString("anchor_cache_json","")?.length)
entry("Location cache exists",context.getSharedPreferences("time_circles_gps",0).contains("timestamp"))
entry("App files directory",context.filesDir.absolutePath);entry("App cache directory",context.cacheDir.absolutePath)
val stat=StatFs(context.filesDir.absolutePath)
entry("Available filesystem bytes",stat.availableBytes);entry("Total filesystem bytes",stat.totalBytes)
section("Runtime and device (no unique identifiers)")
entry("Manufacturer",Build.MANUFACTURER);entry("Model",Build.MODEL);entry("Android release",Build.VERSION.RELEASE);entry("Android SDK",Build.VERSION.SDK_INT)
entry("ABIs",Build.SUPPORTED_ABIS.joinToString(", "));entry("OS",System.getProperty("os.name"));entry("Java",System.getProperty("java.version"))
entry("Time zone",TimeZone.getDefault().id);entry("Locale",Locale.getDefault().toLanguageTag());entry("UTC now",Instant.now())
val dm=context.resources.displayMetrics;entry("Display pixels","${dm.widthPixels} x ${dm.heightPixels}");entry("Density",dm.density);entry("Font scale",context.resources.configuration.fontScale)
val rt=Runtime.getRuntime();entry("CPU processors",rt.availableProcessors());entry("Heap allocated bytes",rt.totalMemory());entry("Heap free bytes",rt.freeMemory());entry("Heap limit bytes",rt.maxMemory());entry("Native heap allocated bytes",Debug.getNativeHeapAllocatedSize())
val am=context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager;val mem=ActivityManager.MemoryInfo();am.getMemoryInfo(mem);entry("System available RAM bytes",mem.availMem);entry("System total RAM bytes",mem.totalMem);entry("System low-memory flag",mem.lowMemory)
val pm=context.getSystemService(Context.POWER_SERVICE) as PowerManager;entry("Power saving enabled",pm.isPowerSaveMode)
val battery=context.registerReceiver(null,android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1;val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,-1)?:-1
entry("Battery percent",if(level>=0 && scale>0) "${level*100/scale}%" else "unknown");entry("Battery status code",battery?.getIntExtra(BatteryManager.EXTRA_STATUS,-1))
section("Privacy and limitations")
entry("App backup enabled",context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP !=0)
entry("GPS source","Android LocationManager.GPS_PROVIDER only");entry("GPS transmissions","No app location upload logic; Android OS / other apps may behave independently")
entry("App permissions","INTERNET, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION")
entry("Unsupported ephemeris","Non-SPK and SPK kernels without necessary Earth/Moon/Sun coverage")
section("Recent app activity (bounded)")
val log=DiagnosticsLog.all();if(log.isEmpty()) append("No recorded events\n") else for(e in log) append(e).append('\n')
}
}
