
@kotlinx.serialization.UnstableDefault
fun main(args: Array<String>) {
    parseArgs(args)?.let { params ->
        init(params)
    }
}

expect fun init(params: Params)