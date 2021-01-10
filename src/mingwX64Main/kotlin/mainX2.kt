import kotlinx.cinterop.*
import kotlinx.serialization.json.Json
import mylaps.*
import platform.posix.size_t
import platform.posix.uint32_t

fun initSdk(publisher: COpaquePointer): X2Context {

    // connecting
    val sdkHandle: mdp_sdk_handle_t = mdp_sdk_alloc("x2Sample", publisher)
            ?: throw IllegalStateException("ERROR: SDK handle not allocated")
    println("SDK handle initialized")

    val mtaHandle = mta_handle_alloc(sdkHandle, publisher)
            ?: throw IllegalStateException("Unable to allocate MTA Handle")
    println("MTA Handle initialized")

    val eventHandle = mta_eventdata_handle_alloc_live_with_resend(mtaHandle, 0, publisher)
            ?: throw IllegalStateException("Failed to get the necessary handles to the event")
    println("Event handle initialized")

    mta_objectdata_subscribe(mtaHandle, mtaLoop)
    mta_objectdata_subscribe(mtaHandle, mtaTransponder)

    mta_eventdata_subscribe(eventHandle, MTAEVENTDATA_.mtaLoopTrigger, 0, false)
    mta_eventdata_subscribe(eventHandle, MTAEVENTDATA_.mtaPassingTrigger, 0, false)
    mta_eventdata_subscribe(eventHandle, MTAEVENTDATA_.mtaPassing, 100, false)

    return X2Context(sdkHandle, mtaHandle, eventHandle)
}

fun initConnectionListener(x2: X2Context) {

    val connectionStateHandler: pfNotifyConnectionState = staticCFunction { _, state: uint32_t, _ ->
        when (CONNECTIONSTATE.byValue(state)) {
            CONNECTIONSTATE_.csTryingConnect -> println("Trying to connect to the server")
            CONNECTIONSTATE_.csConnectFailed -> println("The connection failed to establish (end-point)")
            CONNECTIONSTATE_.csTryingAuthenticate -> println("The connection is opened, trying to authenticate the user")
            CONNECTIONSTATE_.csAuthenticationFailed -> println("Failed to authenticate the user")
            CONNECTIONSTATE_.csStreaming -> println("Connected. The client receives streaming data")
            CONNECTIONSTATE_.csStoppedStreaming -> println("The client stopped receiving streaming data")
            CONNECTIONSTATE_.csAutoReconnect -> println("Trying to reconnect to the server")
            CONNECTIONSTATE_.csForceDisconnect -> println("The user explicitly killed the connection")
            CONNECTIONSTATE_.csServerForceDisconnect -> println("Server forced a connection close")
            else -> println("Unknown connect state $state")
        }
    }
    mta_notify_connectionstate(x2.mtaHandle, connectionStateHandler)
}


@ExperimentalUnsignedTypes
fun passingsTrigger(x2: X2Context) {

    val passingsTriggerHandler: pfNotifyPassingTrigger = staticCFunction { handle: mta_eventdata_handle_t?, type: MDP_NOTIFY_TYPE, passingTriggers: CPointer<CPointerVar<passingtrigger_t>>?, count: uint32_t, _ ->

        val appHandle = mta_eventdata_get_appliance_handle(handle)
        val bufferSize = 1024.convert<size_t>()
        val buffer = nativeHeap.allocArray<ByteVar>(bufferSize.toInt())

        println("[$type] Passing trigger $count")
        for (i in 0 until count.toInt()) {
            val passingTriggerOpt = passingTriggers?.get(i)
            passingTriggerOpt?.let { passingTrigger ->

                val trigger: passingtrigger_t = passingTrigger.pointed
                val transponderLabel = mta_transponder_find(appHandle, trigger.transponderid)?.pointed?.label?.toKString()

                val triggerType = when (trigger.type.toUInt()) {
                    pttFirstContact -> "FirstContact"
                    pttRealTime -> "RealTime"
                    else -> "Unknown"
                }
                val timeUtc = mdp_get_time_as_string(buffer, bufferSize, trigger.utctime, false, 4)
                val time = mdp_get_time_as_string(buffer, bufferSize, trigger.timeofday, false, 4)
                val isResend = passingtrigger_is_resend(passingTriggerOpt)

                println("[$type] Passing trigger type $triggerType id: ${trigger.id} LoopId: ${trigger.loopid} Transponder: $transponderLabel " +
                        "TimeUTC ${timeUtc?.toKString()} Time: ${time?.toKString()} isResend: $isResend")
            }
        }
    }

    mta_notify_passingtrigger(x2.eventHandle, passingsTriggerHandler)
    mta_eventdata_subscribe(x2.eventHandle, MTAEVENTDATA_.mtaPassingTrigger, 0, false)
}

@ExperimentalUnsignedTypes
fun showPassings(x2: X2Context) {

    val passingHandler: pfNotifyPassing = staticCFunction { handle: mta_eventdata_handle_t?, type: MDP_NOTIFY_TYPE, passings: CPointer<CPointerVar<passing_t>>?, count: uint32_t, context: COpaquePointer? ->

        val bs = 1024.convert<size_t>()
        val b = nativeHeap.allocArray<ByteVar>(bs.toInt())

        val appHandle = mta_eventdata_get_appliance_handle(handle)

        println("Passings found last $count")
        for (i in 0 until count.toInt()) {
            val passingOptP = passings?.get(i)
            passingOptP?.let { passingP ->
                val passing = passingP.pointed
                val transponderLabel = mta_transponder_find(appHandle, passing.transponderid)?.pointed?.label?.toKString()
                        ?: ""
                println("[$type] Transponder ${passing.id} -> $transponderLabel")

                val p = PassingMsg(
                        passingId = passing.id.toInt(),
                        transponderId = passing.transponderid.toString(),
                        transponder = transponderLabel,
                        utcTime = mdp_get_time_as_string(b, bs, passing.utctime, false, 4)?.toKString() ?: "",
                        localTime = mdp_get_time_as_string(b, bs, passing.timeofday, false, 4)?.toKString() ?: "")
                val str = Json.encodeToString(PassingMsg.serializer(), p)
                sendJsonToZMQ(context, str)
            }
        }
    }
    mta_notify_passing(x2.eventHandle, passingHandler)
    mta_eventdata_subscribe(x2.eventHandle, MTAEVENTDATA_.mtaPassing, 100, false)
}

@ExperimentalUnsignedTypes
fun showLoops(x2: X2Context) {

    val loopCallback: pfNotifyLoop = staticCFunction { _, type: MDP_NOTIFY_TYPE, loops: CPointer<CPointerVar<loop_t>>?, count: uint32_t, _ ->

        println("Loops found: $count")
        for (i in 0 until count.toInt()) {
            loops?.let {
                it[i]?.let { l ->
                    val loop = l.pointed
                    println("[$type] Loop info [${loop.id}, '${loop.name.toKString()}', '${loop.description.toKString()}']")
                }
            }
        }
    }
    mta_notify_loop(x2.mtaHandle, loopCallback)
}

fun disconnectAppliance(x2: X2Context) {
    println("Disconnecting appliance")
    mta_disconnect(x2.mtaHandle)
}

fun connectAppliance(x2: X2Context, data: Params) {

    initConnectionListener(x2)

    val connectHandler: pfNotifyConnect = staticCFunction { _, connected: Boolean, _ ->
        println("Appliance connect $connected")
    }
    mta_notify_connect(x2.mtaHandle, connectHandler)
    mta_connect(x2.mtaHandle, data.hostname, data.username, data.password, false)
}

@ExperimentalUnsignedTypes
fun verifyAppliance(x2: X2Context, hostName: String) {

    val notifyHandler: pfNotifyVerifyAppliance? = staticCFunction { _, _, verified, appliance: CPointer<availableappliance_t>?, _ ->
        println("Verify callback")
        val bufferSize = 1024.convert<size_t>()
        val buffer = nativeHeap.allocArray<ByteVar>(bufferSize.toInt())

        val modes = arrayOf("Main", "Backup", "Slave", "Mirror", "Replayer")

        if (verified) {
            appliance?.let {
                val a = it.pointed
                val macaddress = mdp_mac_to_string(buffer, bufferSize, a.macaddress, true)?.toKString()
                val ipaddress = mdp_ipaddress_to_string(buffer, bufferSize, a.ipaddress)?.toKString()
                val buildnumber = mdp_version_to_string(buffer, bufferSize, a.buildnumber, true)?.toKString()
                val releasename = a.releasename.toKString()
                val systemsetup = a.systemsetup.toKString()
                val timezone = a.timezoneid.toKString()
                val mode = modes[a.mode.toInt()]
                val isCompatible = availableappliance_is_compatible(appliance)
                println("Appliance verified. Mac address: $macaddress IP address: $ipaddress Build: $buildnumber. Release name: $releasename" +
                        "System setup $systemsetup Timezone: $timezone Mode: $mode IsCompatible: $isCompatible")
            }
        } else {
            throw IllegalArgumentException("Appliance not verified")
        }
    }
    mdp_sdk_notify_verify_appliance(x2.sdkHandle, notifyHandler)
    mdp_sdk_appliance_verify(x2.sdkHandle, hostName)
}

@ExperimentalUnsignedTypes
fun sendJsonToZMQ(zmqContext: COpaquePointer?, jsonString: String) {
    zmqContext?.let { context ->
        val sent = zmq.zmq_send(context, jsonString.cstr, jsonString.length.toULong(), 0)
        if (sent <= 0) println("ERROR: Unable to sent to ZMQ ${zmq.zmq_errno()}")
    }
}
