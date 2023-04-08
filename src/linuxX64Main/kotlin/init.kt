import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.staticCFunction
import mylaps.*
import platform.posix.*
import zmq.ZMQ_PUB
import zmq.zmq_bind
import zmq.zmq_socket
import kotlin.system.exitProcess

data class X2Context(val sdkHandle: mdp_sdk_handle_t, val mtaHandle: mta_handle_t, val eventHandle: mta_eventdata_handle_t)
data class ZMQContext(val zmqContext: COpaquePointer, val publisher: COpaquePointer)

const val ZMQ_PUBLISHER_PORT = 5556

@ExperimentalUnsignedTypes
actual fun init(params: Params) {

    signal(SIGKILL, staticCFunction(::localExit))
    signal(SIGINT, staticCFunction(::localExit))
    signal(SIGQUIT, staticCFunction(::localExit))

    val zmqContext = initZmq(ZMQ_PUBLISHER_PORT)

    val x2Context = initSdk(zmqContext.publisher)

    verifyAppliance(x2Context, params.hostname)

    connectAppliance(x2Context, params)

    showLoops(x2Context)

    showPassings(x2Context)

    passingsTrigger(x2Context)

    var count = 0
    while (count < 100) {
        try {

            mdp_sdk_messagequeue_process(x2Context.sdkHandle, true, 10)
            count++
        } catch (e: Exception) {
            e.message?.let { fail(it) }
            exitProcess(2)
        }
    }
    disconnectAppliance(x2Context)
}

fun initZmq(publisherPort: Int): ZMQContext {
    val zmqContext = zmq.zmq_ctx_new() ?: throw IllegalStateException("Unable to connect to ZMQ")

    val publisher = zmq_socket(zmqContext, ZMQ_PUB) ?: throw IllegalStateException("Unable to open publisher")
    val rc = zmq_bind(publisher, "tcp://*:$publisherPort")
    check(rc == 0) { "Unable to listen on port $publisherPort" }

    println("ZMQ bound to publisher: $publisherPort")
    return ZMQContext(zmqContext, publisher)
}

fun localExit(signal: Int) {
    println("Exit signal $signal")
    exit(0)
}
