package cc0.timecircles.naPHVO
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale
object Store {
private const val NAME = "time_circles_prefs"
fun isCurrentSelected(context:Context):Boolean=context.getSharedPreferences(NAME,0).getBoolean("current_location_selected",true)
fun selectCurrent(context:Context) { context.getSharedPreferences(NAME,0).edit().putBoolean("current_location_selected",true).apply() }
fun locationKey(lat: Double,lon: Double) = String.format(Locale.US,"%.6f,%.6f",lat,lon)
fun selectedKey(context: Context) = context.getSharedPreferences(NAME,0).getString("selected_location_key",null)
fun select(context: Context,location: SavedLocation) { context.getSharedPreferences(NAME,0).edit().putString("selected_location_key",locationKey(location.lat,location.lon)).putBoolean("current_location_selected",false).apply() }
fun loadLocations(context: Context): List<SavedLocation> = try { val arr=JSONArray(context.getSharedPreferences(NAME,0).getString("locations_json",null) ?: "[]");(0 until arr.length()).map { val obj=arr.getJSONObject(it);SavedLocation(obj.optString("name","Location"),obj.optDouble("lat",0.0),obj.optDouble("lon",0.0)) } } catch (_: Exception) { emptyList() }
fun saveLocations(context: Context,locations: List<SavedLocation>) { val arr=JSONArray();locations.forEach { arr.put(JSONObject().put("name",it.name).put("lat",it.lat).put("lon",it.lon)) };context.getSharedPreferences(NAME,0).edit().putString("locations_json",arr.toString()).apply() }
fun saveAnchors(context: Context,key: String,a: AnchorData) { val prefs=context.getSharedPreferences(NAME,0);val root=try { JSONObject(prefs.getString("anchor_cache_json","{}") ?: "{}") } catch (_: Exception) { JSONObject() };val obj=JSONObject();fun putInstant(field:String,v:Instant?) { obj.put(field,v?.toEpochMilli() ?: JSONObject.NULL) };putInstant("synodicStart",a.synodicStart);putInstant("synodicEnd",a.synodicEnd);putInstant("solsticeStart",a.solsticeStart);putInstant("solsticeEnd",a.solsticeEnd);putInstant("nextNauticalInstant",a.nextNauticalInstant);obj.put("nextNauticalLabel",a.nextNauticalLabel ?: JSONObject.NULL);root.put(key,obj);prefs.edit().putString("anchor_cache_json",root.toString()).apply() }
fun loadAnchors(context: Context,key: String): AnchorData? {
return try {
val raw=context.getSharedPreferences(NAME,0).getString("anchor_cache_json","{}") ?: "{}"
val obj=JSONObject(raw).getJSONObject(key)
fun instant(field:String):Instant? = if(!obj.has(field) || obj.isNull(field)) null else Instant.ofEpochMilli(obj.getLong(field))
AnchorData(instant("synodicStart"),instant("synodicEnd"),instant("solsticeStart"),instant("solsticeEnd"),instant("nextNauticalInstant"),if(!obj.has("nextNauticalLabel") || obj.isNull("nextNauticalLabel")) null else obj.optString("nextNauticalLabel"))
} catch (_: Exception) { null }
}
fun clearAnchors(context:Context) { context.getSharedPreferences(NAME,0).edit().remove("anchor_cache_json").apply() }
fun usable(a: AnchorData?,now:Instant)=a!=null && a.synodicStart!=null && a.synodicEnd!=null && a.solsticeStart!=null && a.solsticeEnd!=null && !now.isBefore(a.synodicStart) && now.isBefore(a.synodicEnd) && !now.isBefore(a.solsticeStart) && now.isBefore(a.solsticeEnd)
}
