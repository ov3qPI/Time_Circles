package cc0.timecircles.naPHVO
import java.time.Duration
import java.time.Instant
import kotlin.math.*
data class SolarCrossing(val instant:Instant,val rising:Boolean)
data class HorizonSchedule(val dayStart:Instant,val dayEnd:Instant,val dayEvents:List<SolarCrossing>,val upcoming:List<SolarCrossing>) {
fun next(now:Instant):SolarCrossing?=upcoming.firstOrNull { it.instant.isAfter(now) }
fun tickFractions():List<Double> = dayEvents.map { Duration.between(dayStart,it.instant).toMillis().toDouble()/Duration.between(dayStart,dayEnd).toMillis() }.filter { it>=0.0 && it<1.0 }
}
object SolarHorizon {
private fun radians(v:Double)=Math.toRadians(v)
private fun degrees(v:Double)=Math.toDegrees(v)
private fun mod(v:Double,n:Double):Double=(v%n+n)%n
fun dayStart(now:Instant,lon:Double):Instant { val offset=(lon*240000.0).roundToLong();val shifted=now.toEpochMilli()+offset;return Instant.ofEpochMilli(Math.floorDiv(shifted,86400000L)*86400000L-offset) }
fun altitude(lat:Double,lon:Double,at:Instant):Double {
val jd=at.epochSecond/86400.0+at.nano/86400000000000.0+2440587.5
val t=(jd-2451545.0)/36525.0
val l=mod(280.46646+t*(36000.76983+t*0.0003032),360.0)
val m=357.52911+t*(35999.05029-0.0001537*t)
val e=0.016708634-t*(0.000042037+0.0000001267*t)
val c=sin(radians(m))*(1.914602-t*(0.004817+0.000014*t))+sin(radians(2*m))*(0.019993-0.000101*t)+sin(radians(3*m))*0.000289
val omega=125.04-1934.136*t
val lambda=l+c-0.00569-0.00478*sin(radians(omega))
val meanObliq=23.0+(26.0+(21.448-t*(46.815+t*(0.00059-t*0.001813)))/60.0)/60.0
val eps=radians(meanObliq+0.00256*cos(radians(omega)))
val decl=asin(sin(eps)*sin(radians(lambda)))
val y=tan(eps/2).pow(2)
val eqTime=4*degrees(y*sin(2*radians(l))-2*e*sin(radians(m))+4*e*y*sin(radians(m))*cos(2*radians(l))-0.5*y*y*sin(4*radians(l))-1.25*e*e*sin(2*radians(m)))
val utcMinutes=mod(at.epochSecond.toDouble()/60.0+at.nano/60000000000.0,1440.0)
val angle=radians(mod(utcMinutes+eqTime+4*lon,1440.0)/4.0-180.0)
val phi=radians(lat)
return degrees(asin((sin(phi)*sin(decl)+cos(phi)*cos(decl)*cos(angle)).coerceIn(-1.0,1.0)))
}
private fun refine(lat:Double,lon:Double,a0:Instant,b0:Instant):Instant { var a=a0;var b=b0;val start=altitude(lat,lon,a);repeat(32) { if(Duration.between(a,b).seconds<=1) return a.plusMillis(Duration.between(a,b).toMillis()/2);val mid=a.plusMillis(Duration.between(a,b).toMillis()/2);val h=altitude(lat,lon,mid);if((start<=0)==(h<=0)) a=mid else b=mid };return a.plusMillis(Duration.between(a,b).toMillis()/2) }
fun crossings(lat:Double,lon:Double,start:Instant,end:Instant,stepSeconds:Long=1200):List<SolarCrossing> { val hits=mutableListOf<SolarCrossing>();var a=start;var av=altitude(lat,lon,a);while(a.isBefore(end)) { val b=minOf(a.plusSeconds(stepSeconds),end);val bv=altitude(lat,lon,b);if((av<0 && bv>=0)||(av>0 && bv<=0)) { val instant=refine(lat,lon,a,b);if(hits.none { abs(Duration.between(it.instant,instant).seconds)<3 }) hits.add(SolarCrossing(instant,bv>av)) };a=b;av=bv };return hits }
fun approximate(lat:Double,lon:Double,now:Instant):HorizonSchedule {
val start=dayStart(now,lon);val end=start.plusSeconds(86400)
val nearby=crossings(lat,lon,start,start.plusSeconds(3*86400))
if(nearby.any { it.instant.isAfter(now) }) return HorizonSchedule(start,end,nearby.filter { !it.instant.isBefore(start)&&it.instant.isBefore(end) },nearby)
val later=crossings(lat,lon,start.plusSeconds(3*86400),start.plusSeconds(370*86400),1800)
return HorizonSchedule(start,end,nearby.filter { !it.instant.isBefore(start)&&it.instant.isBefore(end) },nearby+later.take(2))
}
fun withPrecise(base:HorizonSchedule,events:List<SolarCrossing>):HorizonSchedule {
val inDay=events.filter { !it.instant.isBefore(base.dayStart)&&it.instant.isBefore(base.dayEnd) }
val upcoming=if(events.isEmpty()) base.upcoming else { val boundary=base.dayStart.plusSeconds(3*86400);events+base.upcoming.filter { !it.instant.isBefore(boundary) } }
return HorizonSchedule(base.dayStart,base.dayEnd,if(events.isEmpty()) base.dayEvents else inDay,upcoming.sortedBy { it.instant })
}
}
