package cc0.timecircles.naPHVO.ephem
import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.os.SystemClock
import android.provider.OpenableColumns
import cc0.timecircles.naPHVO.DiagnosticsLog
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import picante.mechanics.CelestialBodies
import picante.mechanics.CelestialFrames
import picante.mechanics.Coverage
import picante.mechanics.providers.aberrated.AberrationCorrection
import picante.spice.SpiceEnvironmentBuilder
import picante.time.TimeConversion
private const val KERNEL_INSTALLER_VERSION="KernelInstaller_v20261026T041334Z"
data class KernelChoice(val filename:String,val bytes:Long,val md5:String,val label:String,val span:String)
data class KernelInfo(val name:String,val bytes:Long,val sha256:String,val source:String,val installedAt:String,val file:File)
object KernelInstaller {
const val BASE_URL="https://naif.jpl.nasa.gov/pub/naif/generic_kernels/spk/planets/"
val choices=listOf(KernelChoice("de442s.bsp",32701440,"cc49327e06088124c0e39d8dde9f0b58","DE442s · recommended","Modern-era subset"),KernelChoice("de442.bsp",119771136,"446656322267e7b819a26cb08a0d8718","DE442 · full","1550–2650"),KernelChoice("de440s.bsp",32726016,"3917ee56769db332790c751e2168843d","DE440s · compact","1849–2150"),KernelChoice("de440.bsp",119799808,"c9d581bfd84209dbeee8b1583939b148","DE440 · full","1550–2650"))
val defaultChoice=choices.first()
private val lock=Any()
private val transferLock=Any()
private val supportFiles=listOf("naif0012.tls","pck00011.tpc")
@Volatile private var verifiedStamp:String?=null
fun installedKernelDir(context:Context)=File(context.filesDir,"kernels")
private fun activeFile(context:Context)=File(installedKernelDir(context),"active.bsp")
private fun recordFile(context:Context)=File(installedKernelDir(context),"active.properties")
private fun legacyFile(context:Context)=File(installedKernelDir(context),"de440.bsp")
private fun stamp(file:File)="${file.absolutePath}:${file.length()}:${file.lastModified()}"
private fun hex(bytes:ByteArray)=bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
private fun digest(file:File,type:String):String { val md=MessageDigest.getInstance(type);file.inputStream().buffered(1048576).use { input -> val buf=ByteArray(1048576);while(true) { val n=input.read(buf);if(n<0) break;md.update(buf,0,n) } };return hex(md.digest()) }
private fun readRecord(context:Context):KernelInfo? { val f=activeFile(context);val r=recordFile(context);if(!f.isFile||!r.isFile) return null;return runCatching { val p=Properties().also { props -> r.inputStream().use { props.load(it) } };val n=p.getProperty("bytes")?.toLongOrNull()?:return null;val h=p.getProperty("sha256")?:return null;if(n!=f.length()||!h.matches(Regex("[a-f0-9]{64}"))) return null;KernelInfo(p.getProperty("name","Unknown SPK"),n,h,p.getProperty("source","import"),p.getProperty("installedAt","unknown"),f) }.getOrNull() }
fun installedInfo(context:Context):KernelInfo?=synchronized(lock) { readRecord(context)?:run { val old=legacyFile(context);if(old.isFile&&old.length()==119799808L) KernelInfo("de440.bsp",old.length(),"a4ce9bf9b3282becc9f4b2ac3cebe03a2ae7599981aabd7265fd8482fff7c4b5","legacy","original installation",old) else null } }
fun activeKernel(context:Context)=installedInfo(context)?.file?:activeFile(context)
fun isInstalled(context:Context):Boolean=synchronized(lock) { val info=installedInfo(context)?:return@synchronized false;val key=stamp(info.file)+":"+info.sha256;if(verifiedStamp==key) true else { val ok=runCatching { digest(info.file,"SHA-256").equals(info.sha256,true) }.getOrDefault(false);verifiedStamp=if(ok) key else null;ok } }
fun activeName(context:Context)=installedInfo(context)?.name?:"No SPK installed"
private fun move(a:File,b:File) { Files.move(a.toPath(),b.toPath(),StandardCopyOption.REPLACE_EXISTING) }
private fun support(context:Context,dir:File) { for(name in supportFiles) { val target=File(dir,name);if(target.isFile&&target.length()>0) continue;val temp=File.createTempFile(".$name-",".partial",dir);try { context.assets.open("kernels/$name").use { source -> FileOutputStream(temp).use { out -> source.copyTo(out);out.fd.sync() } };if(temp.length()<100) error("Invalid supporting kernel: $name");move(temp,target) } finally { temp.delete() } } }
fun requireInstalled(context:Context):File=synchronized(lock) { if(!isInstalled(context)) error("No valid planetary SPK installed");val dir=installedKernelDir(context);dir.mkdirs();support(context,dir);dir }
private fun validateKernel(context:Context,file:File) { file.inputStream().use { val b=ByteArray(8);if(it.read(b)!=8||!String(b,Charsets.US_ASCII).startsWith("DAF/SPK")) error("Not an SPK file") };val dir=installedKernelDir(context);support(context,dir);val env=SpiceEnvironmentBuilder().apply { load("candidate.bsp",file);load("naif0012.tls",File(dir,"naif0012.tls"));load("pck00011.tpc",File(dir,"pck00011.tpc")) }.build();val t=TimeConversion(env.getLSK()).instantToTDB(Instant.now());val provider=env.createSingleAberratedProvider();val sun=provider.createAberratedPositionVectorFunction(CelestialBodies.SUN,CelestialBodies.EARTH,CelestialFrames.J2000,Coverage.ALL_TIME,AberrationCorrection.NONE).getPosition(t);val moon=provider.createAberratedPositionVectorFunction(CelestialBodies.MOON,CelestialBodies.EARTH,CelestialFrames.J2000,Coverage.ALL_TIME,AberrationCorrection.NONE).getPosition(t);if(!listOf(sun.i,sun.j,sun.k,moon.i,moon.j,moon.k).all { it.isFinite() }) error("Invalid Sun or Moon vectors") }
private fun ensureSpace(dir:File,bytes:Long) { if(StatFs(dir.absolutePath).availableBytes<bytes+16777216L) error("Insufficient storage for ephemeris") }
private fun transfer(context:Context,known:KernelChoice?,name:String,origin:String,length:Long?,cancel:AtomicBoolean,onProgress:(Long,Long?)->Unit,onPhase:(String)->Unit,source:()->InputStream) { synchronized(transferLock) { val dir=installedKernelDir(context);if(!dir.exists()&&!dir.mkdirs()) error("Cannot create kernel directory");ensureSpace(dir,length?:known?.bytes?:119799808L);val temp=File.createTempFile(".download-",".bsp",dir);val record=File.createTempFile(".record-",".properties",dir);try { val sha=MessageDigest.getInstance("SHA-256");val md5=MessageDigest.getInstance("MD5");var done=0L;var last=0L;source().use { input -> FileOutputStream(temp).use { output -> val b=ByteArray(262144);while(true) { if(cancel.get()||Thread.currentThread().isInterrupted) throw InterruptedException("Download stopped");val n=input.read(b);if(n<0) break;done+=n;if(done>2000000000L||(known!=null&&done>known.bytes)) error("Kernel exceeds expected size");output.write(b,0,n);sha.update(b,0,n);md5.update(b,0,n);val time=SystemClock.elapsedRealtime();if(time-last>=110L) { onProgress(done,length?:known?.bytes);last=time } };output.fd.sync() } };onProgress(done,length?:known?.bytes);if(cancel.get()) throw InterruptedException("Download stopped");onPhase("Transfer complete · validating kernel");if(known!=null&&(done!=known.bytes||hex(md5.digest())!=known.md5)) error("Kernel failed size or NASA/JPL checksum verification");if(done<1024L) error("Kernel file too small");validateKernel(context,temp);if(cancel.get()) throw InterruptedException("Download stopped");onPhase("Kernel verified · installing");val hash=hex(sha.digest());val props=Properties().apply { setProperty("name",name);setProperty("bytes",done.toString());setProperty("sha256",hash);setProperty("source",origin);setProperty("installedAt",Instant.now().toString()) };FileOutputStream(record).use { props.store(it,null);it.fd.sync() };synchronized(lock) { val current=activeFile(context);val backup=File(dir,".previous-active.bsp");backup.delete();if(current.exists()) move(current,backup);try { move(temp,current);move(record,recordFile(context)) } catch(e:Exception) { current.delete();if(backup.exists()) move(backup,current);throw e };backup.delete();verifiedStamp=stamp(current)+":"+hash };DiagnosticsLog.add("SPK installed: $name, $done bytes");onProgress(done,done) } finally { temp.delete();record.delete() } } }
fun download(context:Context,choice:KernelChoice,cancel:AtomicBoolean,onProgress:(Long,Long?)->Unit,onPhase:(String)->Unit) { require(choices.any { it.filename==choice.filename&&it.md5==choice.md5 });val conn=(URL(BASE_URL+choice.filename).openConnection() as HttpsURLConnection).apply { connectTimeout=20000;readTimeout=10000;instanceFollowRedirects=false;setRequestProperty("Accept-Encoding","identity");setRequestProperty("User-Agent","TimeCircles/3") };try { conn.connect();if(conn.responseCode!=200) error("JPL download HTTP ${conn.responseCode}");val sz=conn.contentLengthLong.takeIf { it>0 };if(sz!=null&&sz!=choice.bytes) error("Unexpected kernel size $sz");transfer(context,choice,choice.filename,"JPL download",choice.bytes,cancel,onProgress,onPhase) { conn.inputStream } } finally { conn.disconnect() } }
fun importFile(context:Context,uri:Uri,cancel:AtomicBoolean,onProgress:(Long,Long?)->Unit,onPhase:(String)->Unit) { val resolver=context.contentResolver;val name=runCatching { resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null } }.getOrNull()?.substringAfterLast('/')?:"imported.bsp";if(!name.lowercase().endsWith(".bsp")) error("Select a SPICE .bsp kernel");val known=choices.firstOrNull { it.filename.equals(name,true) };val size=runCatching { resolver.query(uri,arrayOf(OpenableColumns.SIZE),null,null,null)?.use { if(it.moveToFirst()) it.getLong(0) else null } }.getOrNull()?.takeIf { it>0 };transfer(context,known,name,"Android file picker",size,cancel,onProgress,onPhase) { resolver.openInputStream(uri)?:error("Cannot open selected SPK") } }
}
