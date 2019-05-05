import kotlinx.cinterop.*
import mylaps.*
import platform.posix.NULL
import platform.posix.size_t
import platform.posix.uint32_t

fun initSdk(): X2Context {

    // connecting
    val sdkHandle: mdp_sdk_handle_t = mdp_sdk_alloc("x2Sample", NULL)
            ?: throw IllegalStateException("ERROR: SDK handle not allocated")
    println("SDK handle initialized")

    val mtaHandle = mta_handle_alloc(sdkHandle, NULL)
            ?: throw IllegalStateException("Unable to allocate MTA Handle")
    println("MTA Handle initialized")

    val eventHandle = mta_eventdata_handle_alloc_live_with_resend(mtaHandle, 0, NULL)
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
              passingTriggers?.let { passingTrigger ->
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
                        "TimeUTC $timeUtc Time: $time isResend: $isResend")
            }
        }
    }

    mta_notify_passingtrigger(x2.eventHandle, passingsTriggerHandler)
    mta_eventdata_subscribe(x2.eventHandle, MTAEVENTDATA_.mtaPassingTrigger, 0, false)
}

@ExperimentalUnsignedTypes
fun showPassings(x2: X2Context) {

    val passingHandler: pfNotifyPassing = staticCFunction { handle: mta_eventdata_handle_t?, type: MDP_NOTIFY_TYPE, passings: CPointer<CPointerVar<passing_t>>?, count: uint32_t, _ ->

        val appHandle = mta_eventdata_get_appliance_handle(handle)

        println("Passings found last $count")
        for (i in 0 until count.toInt()) {
            if (passings!=null) {
                val passingOptP = passings?.get(i)
                val passing = passingOptP.pointed
                val transponderLabel = mta_transponder_find(appHandle, passing.transponderid)?.pointed?.label?.toKString()
                println("[$type] Transponder ${passing.id} -> $transponderLabel")
            }
        }
    }
    mta_notify_passing(x2.eventHandle, passingHandler)
    mta_eventdata_subscribe(x2.eventHandle, MTAEVENTDATA_.mtaPassing, 100, false)
}

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
