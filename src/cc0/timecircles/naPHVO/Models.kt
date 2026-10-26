package cc0.timecircles.naPHVO
import java.time.Instant
const val SOURCE_VERSION = "TimeCirclesNative_v20261026T041334Z"
data class SavedLocation(val name: String,val lat: Double,val lon: Double)
data class AnchorData(val synodicStart: Instant?,val synodicEnd: Instant?,val solsticeStart: Instant?,val solsticeEnd: Instant?,val nextNauticalInstant: Instant?,val nextNauticalLabel: String?)
data class CycleData(val longitudinalTime: String,val dayDecimal: Double,val synodicDay: Int,val synodicFraction: Double,val yearDay: Int,val yearFraction: Double,val nextEventLabel: String,val nextEventCountdown: String)
