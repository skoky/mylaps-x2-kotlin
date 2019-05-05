
data class Params(val hostname: String, val username: String, val password: String)

fun parseArgs(args: Array<String>): Params? {
    if (args.size != 3) {
        println("Usage: x2 <x2 server hostname> <x2 username> <x2 password>")
        return null
    }
    return Params(args[0], args[1], args[2])
}

fun fail(msg: String) {
    println("ERROR: $msg")
}