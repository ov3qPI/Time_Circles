package cc0.timecircles.naPHVO
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.*
object CycleMath {
private fun positiveModulo(v:Double,mod:Double):Double { val x=v%mod;return if(x<0) x+mod else x }
fun longitudinalDate(t:Instant,lon:Double):LocalDate=t.plusSeconds((lon/15.0*3600.0).toLong()).atZone(ZoneOffset.UTC).toLocalDate()
fun longitudeDayDifference(start:Instant,end:Instant,lon:Double):Int=ChronoUnit.DAYS.between(longitudinalDate(start,lon),longitudinalDate(end,lon)).toInt().coerceAtLeast(0)
fun solsticeApprox(year:Int):Instant=ZonedDateTime.of(year,12,21,9,0,0,0,ZoneOffset.UTC).toInstant()
fun formatDuration(duration:Duration):String { val seconds=duration.seconds.coerceAtLeast(0);val days=seconds/86400;val hours=seconds%86400/3600;val minutes=seconds%3600/60;val secs=seconds%60;return if(days>0) String.format(Locale.US,"%dd %02d:%02d:%02d",days,hours,minutes,secs) else String.format(Locale.US,"%02d:%02d:%02d",hours,minutes,secs) }
fun compute(_lat:Double,lon:Double,now:Instant,a:AnchorData?,horizon:HorizonSchedule?=null):CycleData {
val zoned=now.atZone(ZoneOffset.UTC).plusSeconds((lon*240.0).roundToLong())
val seconds=zoned.hour*3600+zoned.minute*60+zoned.second
val dayFraction=seconds/86400.0
val time=String.format(Locale.US,"%02d:%02d:%02d",zoned.hour,zoned.minute,zoned.second)
val periodSeconds=2551442.8768992
val newMoonRef=Instant.parse("2024-01-11T11:57:00Z")
val anchoredStart=a?.synodicStart
val anchoredEnd=a?.synodicEnd
val anchoredValid=anchoredStart!=null && anchoredEnd!=null && !now.isBefore(anchoredStart) && now.isBefore(anchoredEnd) && Duration.between(anchoredStart,anchoredEnd).toDays() in 25L..35L
val synFraction=if(anchoredValid) (Duration.between(anchoredStart,now).toMillis().toDouble()/Duration.between(anchoredStart,anchoredEnd).toMillis()).coerceIn(0.0,1.0) else positiveModulo(Duration.between(newMoonRef,now).toMillis().toDouble()/(periodSeconds*1000.0),1.0)
val synStart=if(anchoredValid) anchoredStart!! else now.minusMillis((synFraction*periodSeconds*1000.0).roundToLong())
val synDay=longitudeDayDifference(synStart,now,lon).coerceAtMost(31)
val year=now.atZone(ZoneOffset.UTC).year
val dec=solsticeApprox(year)
val prev=if(now.isBefore(dec)) solsticeApprox(year-1) else dec
val next=if(now.isBefore(dec)) dec else solsticeApprox(year+1)
val yearStart=a?.solsticeStart
val yearEnd=a?.solsticeEnd
val yearValid=yearStart!=null&&yearEnd!=null&&!now.isBefore(yearStart)&&now.isBefore(yearEnd)&&Duration.between(yearStart,yearEnd).toDays() in 345L..385L
val yStart=if(yearValid) yearStart!! else prev
val yEnd=if(yearValid) yearEnd!! else next
val yearFraction=(Duration.between(yStart,now).toMillis().toDouble()/Duration.between(yStart,yEnd).toMillis()).coerceIn(0.0,1.0)
val yearDay=longitudeDayDifference(yStart,now,lon)
val event=horizon?.next(now)
val label=if(event==null) "Next 0° solar crossing unavailable" else if(event.rising) "Next 0° solar rise" else "Next 0° solar set"
return CycleData(time,dayFraction,synDay,synFraction,yearDay,yearFraction,label,if(event==null) "" else formatDuration(Duration.between(now,event.instant)))
}
fun parseCoordinates(s:String):Pair<Double,Double>? { val p=s.split(',').map { it.trim() }.filter { it.isNotEmpty() };if(p.size!=2) return null;val a=p[0].toDoubleOrNull()?:return null;val b=p[1].toDoubleOrNull()?:return null;return if(a.isFinite()&&b.isFinite()&&a in -90.0..90.0&&b in -180.0..180.0) a to b else null }
}
