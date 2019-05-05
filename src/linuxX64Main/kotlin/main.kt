import kotlinx.cinterop.*
import mylaps.*
import platform.posix.*
import kotlin.system.exitProcess

data class X2Context(val sdkHandle: mdp_sdk_handle_t, val mtaHandle: mta_handle_t, val eventHandle: mta_eventdata_handle_t)

@ExperimentalUnsignedTypes
fun main(args: Array<String>) {

    signal(SIGKILL, staticCFunction(::localExit))
    signal(SIGINT, staticCFunction(::localExit))
    signal(SIGQUIT, staticCFunction(::localExit))


    val params = parseArgs(args) ?: exitProcess(1)

    val x2Context = initSdk()

    verifyAppliance(x2Context, params.hostname)

    connectAppliance(x2Context, params)

    showLoops(x2Context)

    showPassings(x2Context)

    passingsTrigger(x2Context)

    var count = 0
    while (count < 100) {
        try {

            mdp_sdk_messagequeue_process(x2Context.sdkHandle, true, _MDP_SECOND / 10)
            count++
        } catch (e: Exception) {
            e.message?.let { fail(it) }
            exitProcess(2)
        }
    }
    disconnectAppliance(x2Context)
}

fun localExit(signal: Int) {
    println("Exit signal $signal")
    exit(0)
}
