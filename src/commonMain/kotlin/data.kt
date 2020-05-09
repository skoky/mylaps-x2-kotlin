import kotlinx.serialization.Serializable

@Serializable
data class PassingMsg(val passingId: Int, val transponderId: String, val transponder: String, val utcTime: String, val localTime: String)
