package cc0.timecircles.naPHVO.ephem
import android.content.Context
import cc0.timecircles.naPHVO.SolarCrossing
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.math.*
import picante.math.vectorspace.UnwritableVectorIJK
import picante.mechanics.CelestialBodies
import picante.mechanics.CelestialFrames
import picante.mechanics.Coverage
import picante.mechanics.providers.aberrated.AberrationCorrection
import picante.spice.SpiceEnvironmentBuilder
import picante.time.TimeConversion
class AstronomyEngine private constructor(val kernelDir:File,val planetaryFile:File) {
data class SynodicCycleData(val fraction:Double,val startInstant:Instant,val endInstant:Instant)
data class SolsticeYearData(val fraction:Double,val startInstant:Instant,val endInstant:Instant)
data class NauticalEventData(val instant:Instant,val label:String)
companion object { @Volatile private var instance:AstronomyEngine?=null;fun get(context:Context):AstronomyEngine=instance?:synchronized(this) { instance?:AstronomyEngine(KernelInstaller.requireInstalled(context.applicationContext),KernelInstaller.activeKernel(context.applicationContext)).also { instance=it } };fun reset()=synchronized(this) { instance=null } }
private val environment by lazy { val builder=SpiceEnvironmentBuilder();builder.load("planetary.bsp",planetaryFile);builder.load("naif0012.tls",File(kernelDir,"naif0012.tls"));builder.load("pck00011.tpc",File(kernelDir,"pck00011.tpc"));builder.build() }
private val timeConversion by lazy { TimeConversion(environment.getLSK()) }
private val provider by lazy { environment.createSingleAberratedProvider() }
private val sunFromEarth by lazy { provider.createAberratedPositionVectorFunction(CelestialBodies.SUN,CelestialBodies.EARTH,CelestialFrames.J2000,Coverage.ALL_TIME,AberrationCorrection.NONE) }
private val moonFromEarth by lazy { provider.createAberratedPositionVectorFunction(CelestialBodies.MOON,CelestialBodies.EARTH,CelestialFrames.J2000,Coverage.ALL_TIME,AberrationCorrection.NONE) }
fun kernelSummary():String=kernelDir.listFiles()?.sortedBy { it.name }?.joinToString("\n") { "${it.name} (${it.length()} bytes)" }?.ifEmpty { "No kernels installed" }?:"No kernels installed"
fun hasAllCoreKernels():Boolean=planetaryFile.isFile && planetaryFile.length()>0L && listOf("naif0012.tls","pck00011.tpc").all { File(kernelDir,it).isFile && File(kernelDir,it).length()>0L }
fun statusLine():String=if(hasAllCoreKernels()) "Ephemeris kernels installed" else "Ephemeris kernels missing"
private fun sun(t:Instant):UnwritableVectorIJK=sunFromEarth.getPosition(timeConversion.instantToTDB(t))
private fun moon(t:Instant):UnwritableVectorIJK=moonFromEarth.getPosition(timeConversion.instantToTDB(t))
private fun declination(t:Instant):Double { val v=sun(t);return atan2(v.k,sqrt(v.i*v.i+v.j*v.j)) }
private fun elongation(t:Instant)=sun(t).getSeparation(moon(t))
private fun midpoint(a:Instant,b:Instant)=Instant.ofEpochMilli(a.toEpochMilli()+(b.toEpochMilli()-a.toEpochMilli())/2)
private fun refineMinimum(a0:Instant,b0:Instant,f:(Instant)->Double):Instant { var a=a0;var b=b0;repeat(50) { val delta=b.toEpochMilli()-a.toEpochMilli();if(delta<=1000L) return midpoint(a,b);val x=Instant.ofEpochMilli(a.toEpochMilli()+delta/3);val y=Instant.ofEpochMilli(b.toEpochMilli()-delta/3);if(f(x)<=f(y)) b=y else a=x };return midpoint(a,b) }
private fun findLocalMinimum(reference:Instant,direction:Int,step:Duration,window:Duration,f:(Instant)->Double):Instant? { val count=(window.seconds/step.seconds).toInt().coerceAtLeast(3);var before:Double?=null;var mid:Double?=null;var midInstant:Instant?=null;var beforeInstant:Instant?=null;var best:Instant?=null;var bestDistance=Long.MAX_VALUE;for(i in 0..count) { val current=if(direction>0) reference.plusSeconds(i*step.seconds) else reference.minusSeconds(i*step.seconds);val value=f(current);if(before!=null && mid!=null && midInstant!=null && beforeInstant!=null && mid<=before && mid<=value) { val dist=Duration.between(midInstant,reference).abs().seconds;if(dist<bestDistance) { bestDistance=dist;best=refineMinimum(minOf(beforeInstant,current),maxOf(beforeInstant,current),f) } };before=mid;mid=value;beforeInstant=midInstant;midInstant=current };return best }
fun getSynodicCycleDataOrNull(now:Instant):SynodicCycleData?=runCatching { val a=findLocalMinimum(now,-1,Duration.ofHours(3),Duration.ofDays(40),::elongation)?:return@runCatching null;val b=findLocalMinimum(now,1,Duration.ofHours(3),Duration.ofDays(40),::elongation)?:return@runCatching null;SynodicCycleData((Duration.between(a,now).seconds.toDouble()/Duration.between(a,b).seconds.coerceAtLeast(1)).coerceIn(0.0,1.0),a,b) }.getOrNull()
fun getSolsticeYearDataOrNull(now:Instant):SolsticeYearData?=runCatching { val a=findLocalMinimum(now,-1,Duration.ofHours(6),Duration.ofDays(380),::declination)?:return@runCatching null;val b=findLocalMinimum(now,1,Duration.ofHours(6),Duration.ofDays(380),::declination)?:return@runCatching null;SolsticeYearData((Duration.between(a,now).seconds.toDouble()/Duration.between(a,b).seconds.coerceAtLeast(1)).coerceIn(0.0,1.0),a,b) }.getOrNull()
private fun normal(a:Double):Double { val t=a%(2*PI);return if(t<0) t+2*PI else t }
private fun altitude(lat:Double,lon:Double,now:Instant):Double { val v=sun(now);val ra=atan2(v.j,v.i);val dec=atan2(v.k,sqrt(v.i*v.i+v.j*v.j));val phi=Math.toRadians(lat);val jd=now.epochSecond/86400.0+now.nano/86400.0/1e9+2440587.5;val t=(jd-2451545.0)/36525.0;val gmst=280.46061837+360.98564736629*(jd-2451545.0)+0.000387933*t*t-t*t*t/38710000.0;val ha=normal(normal(Math.toRadians(gmst)+Math.toRadians(lon))-ra);return asin(sin(phi)*sin(dec)+cos(phi)*cos(dec)*cos(ha)) }
private fun crosses(a:Double,b:Double)=a==0.0 || b==0.0 || (a<0&&b>0)||(a>0&&b<0)
private fun refineCrossing(a0:Instant,b0:Instant,lat:Double,lon:Double,target:Double):Instant { var a=a0;var b=b0;var fa=altitude(lat,lon,a)-target;repeat(40) { val m=midpoint(a,b);val fm=altitude(lat,lon,m)-target;if(crosses(fa,fm)) b=m else { a=m;fa=fm };if(Duration.between(a,b).abs().seconds<=1) return midpoint(a,b) };return midpoint(a,b) }
fun getSolarZeroCrossingsOrNull(lat:Double,lon:Double,start:Instant,end:Instant):List<SolarCrossing>?=runCatching {
val result=mutableListOf<SolarCrossing>()
var a=start
var av=altitude(lat,lon,start)
while(a.isBefore(end)) {
val b=minOf(a.plusSeconds(1200),end)
val bv=altitude(lat,lon,b)
if(crosses(av,bv) && (av<0.0&&bv>=0.0 || av>0.0&&bv<=0.0)) {
val crossing=refineCrossing(a,b,lat,lon,0.0)
if(result.none { abs(Duration.between(it.instant,crossing).seconds)<3 }) result.add(SolarCrossing(crossing,bv>av))
}
a=b
av=bv
}
result
}.getOrNull()
}
