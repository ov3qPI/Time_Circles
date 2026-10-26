package cc0.timecircles.naPHVO
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import java.util.Locale
private const val GPS_SOURCE_ID="GpsLocationSource_v20261026T011942Z"
data class GpsFix(val lat:Double,val lon:Double,val timeMillis:Long,val fresh:Boolean)
object GpsLocationSource {
private const val PREFS="time_circles_gps"
fun permitted(context:Context):Boolean=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
fun enabled(context:Context):Boolean=runCatching { (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
private fun valid(lat:Double,lon:Double):Boolean=lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
private fun fromLocation(location:Location,fresh:Boolean):GpsFix?=if(valid(location.latitude,location.longitude) && location.time>0) GpsFix(location.latitude,location.longitude,location.time,fresh) else null
fun remember(context:Context,fix:GpsFix) { if(!valid(fix.lat,fix.lon) || fix.timeMillis<=0) return;context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("latitude",String.format(Locale.US,"%.9f",fix.lat)).putString("longitude",String.format(Locale.US,"%.9f",fix.lon)).putLong("timestamp",fix.timeMillis).apply() }
fun cached(context:Context):GpsFix? { val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);val lat=p.getString("latitude",null)?.toDoubleOrNull() ?: return null;val lon=p.getString("longitude",null)?.toDoubleOrNull() ?: return null;val time=p.getLong("timestamp",0L);return if(valid(lat,lon) && time>0) GpsFix(lat,lon,time,false) else null }
fun lastKnown(context:Context):GpsFix? { if(!permitted(context)) return null;val system=runCatching { val lm=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager;lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { fromLocation(it,false) } }.getOrNull();return listOfNotNull(system,cached(context)).maxByOrNull { it.timeMillis } }
fun observe(context:Context,onFix:(GpsFix)->Unit,onStatus:(String)->Unit):()->Unit { DiagnosticsLog.add("GPS observation requested")
if(!permitted(context)) { onStatus("Precise GPS permission required");return {} }
val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
val listener=object:LocationListener {
override fun onLocationChanged(location:Location) { val fix=fromLocation(location,true) ?: return;remember(context,fix);onFix(fix);onStatus("GPS satellites · live fix");DiagnosticsLog.add("Fresh GPS fix accepted") }
override fun onProviderEnabled(provider:String) { if(provider==LocationManager.GPS_PROVIDER) onStatus("Waiting for GPS satellites") }
override fun onProviderDisabled(provider:String) { if(provider==LocationManager.GPS_PROVIDER) onStatus("GPS disabled · enable Location") }
}
try { if(!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { onStatus("GPS disabled · enable Location");return {} };manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,20000L,25f,listener,Looper.getMainLooper());onStatus("Waiting for GPS satellites") } catch (_:SecurityException) { onStatus("Precise GPS permission required");return {} } catch (_:Exception) { onStatus("GPS provider unavailable");return {} }
return { runCatching { manager.removeUpdates(listener) } }
}
}
