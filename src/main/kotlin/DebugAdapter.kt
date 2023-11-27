import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

val log = File("logs/log.txt").printWriter()

class DebugAdapter(val state : State) : IDebugProtocolServer {

    constructor(trace : Trace) : this(State(trace))

    var userBreakpoints : List<Breakpoint> = emptyList()
    lateinit var client : IDebugProtocolClient

    object capabilities : Capabilities() {
        override fun getSupportsRestartRequest() = true
        // override fun getSupportsBreakpointLocationsRequest() = true
        override fun getSupportsFunctionBreakpoints() = true
        override fun getSupportsDataBreakpoints() = true
        override fun getSupportsStepBack() = true
        override fun getSupportsRestartFrame() = true
        override fun getSupportsLoadedSourcesRequest() = true
    }

    fun connect(client : IDebugProtocolClient) {
        this.client = client
        log.println("connected")
    }

    override fun terminate(args: TerminateArguments?): CompletableFuture<Void> {
        client.terminated(TerminatedEventArguments())
        exitProcess(0)
    }

    override fun disconnect(args: DisconnectArguments?): CompletableFuture<Void> {
        client.terminated(TerminatedEventArguments())
        exitProcess(0)
    }

    override fun initialize(args: InitializeRequestArguments?): CompletableFuture<Capabilities> {
        log.println("initializing")
        client.initialized()
        return capabilities.asFuture()
    }

    override fun launch(args: MutableMap<String, Any>?): CompletableFuture<Void> = done.also { log.println("launch") }

    override fun attach(args: MutableMap<String, Any>?): CompletableFuture<Void> = done.also { log.println("attach") }

    override fun restart(args: RestartArguments?): CompletableFuture<Void> {
        log.println("restart")

        state.reverseUntil { false }
        client.stopped(StoppedEventArguments().apply {
            reason = "TODO"
        })
        return done
    }

    // override fun breakpointLocations(args: BreakpointLocationsArguments?): CompletableFuture<BreakpointLocationsResponse> {
    //     TODO()
    // }

    override fun setBreakpoints(args: SetBreakpointsArguments?): CompletableFuture<SetBreakpointsResponse> {
        // TODO
        return SetBreakpointsResponse().asFuture()
    }

    // override fun setFunctionBreakpoints(args: SetFunctionBreakpointsArguments?): CompletableFuture<SetFunctionBreakpointsResponse> {
    //     TODO()
    // }

    override fun setExceptionBreakpoints(args: SetExceptionBreakpointsArguments?): CompletableFuture<SetExceptionBreakpointsResponse> {
        // TODO
        return SetExceptionBreakpointsResponse().asFuture()
    }

    override fun dataBreakpointInfo(args: DataBreakpointInfoArguments?): CompletableFuture<DataBreakpointInfoResponse> {
        // TODO
        return DataBreakpointInfoResponse().asFuture()
    }

    override fun setDataBreakpoints(args: SetDataBreakpointsArguments?): CompletableFuture<SetDataBreakpointsResponse> {
        // TODO
        return SetDataBreakpointsResponse().asFuture()
    }

    override fun continue_(args: ContinueArguments?): CompletableFuture<ContinueResponse> {
        // TODO
        return ContinueResponse().asFuture()
    }

    override fun next(args: NextArguments?): CompletableFuture<Void> {
        /*
        val context =
        runUntil { it is NewlineInstruction && it.context == }

         */
        // TODO
        return done
    }

    override fun stepIn(args: StepInArguments?): CompletableFuture<Void> {
        // TODO
        return done
    }

    override fun stepOut(args: StepOutArguments?): CompletableFuture<Void> {
        // TODO
        return done
    }

    override fun stepBack(args: StepBackArguments?): CompletableFuture<Void> {
        // TODO
        return done
    }

    override fun reverseContinue(args: ReverseContinueArguments?): CompletableFuture<Void> {
        // TODO
        return done
    }

    override fun restartFrame(args: RestartFrameArguments?): CompletableFuture<Void> {
        // TODO
        return done
    }

    override fun pause(args: PauseArguments?): CompletableFuture<Void> {
        // we are always paused, but this function is not optional so we just return
        return done
    }

    val frames = mutableListOf<State.StackFrame>()

    override fun stackTrace(args: StackTraceArguments?): CompletableFuture<StackTraceResponse> {
        log.println("stackTrace")
        val frames = state.stack.mapIndexed { index, frame ->
            StackFrame().apply {
                id = index
                name = frame.call.functionName
                source = Source().apply { path = frame.sourceLine.file }
                line = frame.sourceLine.line
            }
        }
        return StackTraceResponse().apply {
            stackFrames = frames.toTypedArray()
        }.asFuture()
    }

    override fun scopes(args: ScopesArguments?): CompletableFuture<ScopesResponse> {
        log.println("scopes")

        return ScopesResponse().apply {
            scopes = arrayOf(
                Scope().apply {
                    name = "Contracts"
                    variablesReference = -2
                },
                Scope().apply {
                    name = "Ghosts"
                    variablesReference = -1
                },
                Scope().apply {
                    name = "Locals"
                    variablesReference = args!!.frameId
                }
            )
        }.asFuture()
    }

    override fun variables(args: VariablesArguments?): CompletableFuture<VariablesResponse> {

        // TODO
        return VariablesResponse().asFuture()
    }

    override fun source(args: SourceArguments?): CompletableFuture<SourceResponse> {
        // TODO
        return SourceResponse().asFuture()
    }

    override fun threads(): CompletableFuture<ThreadsResponse> {
        log.println("threads")
        return ThreadsResponse().apply {
            threads = arrayOf(Thread().apply { id = 0; name = "rule" })
        }.asFuture()
    }

    override fun loadedSources(args: LoadedSourcesArguments?): CompletableFuture<LoadedSourcesResponse> {
        // TODO
        return LoadedSourcesResponse().asFuture()
    }

    override fun evaluate(args: EvaluateArguments?): CompletableFuture<EvaluateResponse> {
        // TODO
        return EvaluateResponse().asFuture()
    }



}

// stack frame
//   scope (Contracts)
//     variable (contract)
//       variable (field)
//   scope (Ghosts)
//   scope (Locals)

class DAPSnapshot(val state : State) {
    val variableSets : MutableList<Variable> = mutableListOf()
    val frames       : MutableList<StackFrame> = mutableListOf()
    val scopes       : MutableList<Scope> = mutableListOf()

    init {

    }
}

fun <T> MutableList<T>.allocate(t : T) : Int {
    val result = size
    add(t)
    return result
}

/** transform this into a completed future */
fun <T> T.asFuture() : CompletableFuture<T> = CompletableFuture.completedFuture(this)

/** Used to indicate a completed future with no result */
val done = CompletableFuture.allOf()

