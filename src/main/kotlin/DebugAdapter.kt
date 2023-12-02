import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

class DebugAdapter(private val state : State) : IDebugProtocolServer {

    constructor(trace : Trace) : this(State(trace))

    private var lineBreakpoints : List<DAPStopCondition> = emptyList()
    private var dataBreakpoints : List<DAPStopCondition> = emptyList()

    private lateinit var client : IDebugProtocolClient

    private val variables = mutableListOf<DataTree.Structure>().apply { allocate(DataTree.Structure(listOf(), "dummy"))}
    private val globalsId = variables.allocate(state.trace.contracts)

    private val capabilities = Capabilities().apply {
        supportsRestartRequest = true
        supportsDataBreakpoints = true
        supportsStepBack = true
        supportsRestartFrame = true
        exceptionBreakpointFilters = arrayOf(
            ExceptionBreakpointsFilter().apply {
                filter = "revert"
                label = "Contract function revert"
                description = "This breakpoint is triggered when a contract function reverts."
            },
            ExceptionBreakpointsFilter().apply {
                filter = "assert"
                label  = "CVL assertion failure"
                description = "This breakpoint is triggered when a CVL `assert` statement fails."
                default_ = true
            }
        )
    }

    fun connect(client : IDebugProtocolClient) {
        this.client = client
        client.initialized()
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
        return capabilities.asFuture()
    }

    override fun launch(args: MutableMap<String, Any>?): CompletableFuture<Void> {
        client.stopped(StoppedEventArguments().apply {
            reason = StoppedEventArgumentsReason.ENTRY
            description = "Call trace has been loaded"
            threadId = 0
            allThreadsStopped = true
        })
        return done
    }

    override fun attach(args: MutableMap<String, Any>?): CompletableFuture<Void> = done

    override fun restart(args: RestartArguments?): CompletableFuture<Void> {
        state.reverseUntil(stopConditions = emptyList())
        client.stopped(StoppedEventArguments().apply {
            reason = StoppedEventArgumentsReason.ENTRY
            description = "Execution restarted"
            threadId = 0
            allThreadsStopped = true
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
        val location = variables[args!!.variablesReference].get(args.name)
        return DataBreakpointInfoResponse().apply {
            dataId = location
            description = state.trace.locations[location]?.name ?: args.name
            accessTypes = DataBreakpointAccessType.entries.toTypedArray()
        }.asFuture()
    }

    override fun setDataBreakpoints(args: SetDataBreakpointsArguments?): CompletableFuture<SetDataBreakpointsResponse> {
        // set up internal breakpoints
        dataBreakpoints = args!!.breakpoints.flatMap { breakpoint ->
            val location = breakpoint!!.dataId
            val name     = state.trace.locations[location]?.name ?: "data"
            listOfNotNull(
                ReadAccess  (location, name).takeIf { breakpoint.accessType != DataBreakpointAccessType.WRITE },
                WriteAccess (location, name).takeIf { breakpoint.accessType != DataBreakpointAccessType.READ },
            )
        }

        // construct response
        return SetDataBreakpointsResponse().apply {
            breakpoints = dataBreakpoints.map { Breakpoint().apply { isVerified = true } }.toTypedArray()
        }.asFuture()
    }

    override fun continue_(args: ContinueArguments?): CompletableFuture<ContinueResponse> {
        runUntilBreakOr { false }
        return ContinueResponse().asFuture()
    }

    override fun next(args: NextArguments?): CompletableFuture<Void> {
        val currentCall = state.stack.last().callId
        runUntilBreakOr {
            it is NewlineInstruction && it.context == currentCall
            || it is ReturnInstruction && it.call == currentCall
        }
        return done
    }

    override fun stepIn(args: StepInArguments?): CompletableFuture<Void> {
        runUntilBreakOr { it is NewlineInstruction || it is CallInstruction }
        return done
    }

    override fun stepOut(args: StepOutArguments?): CompletableFuture<Void> {
        val callId = state.stack.last().callId
        runUntilBreakOr { it is ReturnInstruction && it.call == callId }
        return done
    }

    override fun stepBack(args: StepBackArguments?): CompletableFuture<Void> {
        val callId = state.stack.last().callId
        reverseUntilBreakOr {
            it is NewlineInstruction && it.context == callId
            || it is CallInstruction && it.call == callId
        }
        return done
    }

    override fun reverseContinue(args: ReverseContinueArguments?): CompletableFuture<Void> {
        reverseUntilBreakOr { false }
        return done
    }

    override fun restartFrame(args: RestartFrameArguments?): CompletableFuture<Void> {
        val callId = state.stack.last().callId
        reverseUntilBreakOr { it is CallInstruction && it.call == callId }
        return done
    }

    override fun pause(args: PauseArguments?): CompletableFuture<Void> {
        // we are always paused, but this function is not optional, so we just return
        return done
    }

    override fun stackTrace(args: StackTraceArguments?): CompletableFuture<StackTraceResponse> {
        val frames = state.stack.map { frame ->
            StackFrame().apply {
                id = variables.allocate(state.trace.calls[frame.callId]!!.locals)
                name = frame.call.functionName
                source = Source().apply { path = frame.sourceLine.file }
                line = frame.sourceLine.line
            }
        }.reversed()

        return StackTraceResponse().apply {
            stackFrames = frames.toTypedArray()
        }.asFuture()
    }

    override fun scopes(args: ScopesArguments?): CompletableFuture<ScopesResponse> {
        return ScopesResponse().apply {
            scopes = arrayOf(
                Scope().apply {
                    name = "Globals"
                    variablesReference = globalsId
                },
                Scope().apply {
                    name = "Locals"
                    variablesReference = args!!.frameId
                }
            )
        }.asFuture()
    }

    override fun variables(args: VariablesArguments?): CompletableFuture<VariablesResponse> {
        val tree = variables[args!!.variablesReference]
        val vars = tree.children.mapNotNull { (varName, varValue) -> Variable().apply {
            name = varName
            type = varValue.cvlType
            value = (varValue as? DataTree.Leaf)?.let { state.data[it.location]?.value ?: return@mapNotNull null } ?: ""
            variablesReference = (varValue as? DataTree.Structure)?.let { variables.allocate(it) } ?: 0
        }}

        return VariablesResponse().apply { variables = vars.toTypedArray() }.asFuture()
    }

    override fun threads(): CompletableFuture<ThreadsResponse> {
        return ThreadsResponse().apply {
            threads = arrayOf(Thread().apply { id = 0; name = "rule" })
        }.asFuture()
    }

    private fun runUntilBreakOr(predicate : (Instruction) -> Boolean) {
        val reasons = state.runUntil(lineBreakpoints + dataBreakpoints + StepFinished(predicate))
        notifyStopped(reasons.firstOrNull() ?: OtherReason("End of trace"))
    }

    private fun reverseUntilBreakOr(predicate: (Instruction) -> Boolean) {
        val reasons = state.reverseUntil(lineBreakpoints + dataBreakpoints + StepFinished(predicate))
        notifyStopped(reasons.firstOrNull() ?: OtherReason("Beginning of trace"))
    }

    private fun notifyStopped(stopReason : DAPStopCondition) {
        client.stopped(StoppedEventArguments().apply {
            reason      = stopReason.reason
            description = stopReason.description
            threadId    = 0
            allThreadsStopped = true
        })
    }
}

interface DAPStopCondition : StopCondition {
    val reason      : String
    val description : String
}

class ReadAccess(val locationId: LocationId, locationDescription: String) : DAPStopCondition {
    override val reason      = StoppedEventArgumentsReason.DATA_BREAKPOINT
    override val description = "$locationDescription was read"
    override fun triggeredBy(i: Instruction) = i is LoadInstruction && i.location == locationId
}

class WriteAccess(val locationId: LocationId, locationDescription: String) : DAPStopCondition {
    override val reason      = StoppedEventArgumentsReason.DATA_BREAKPOINT
    override val description = "$locationDescription was written"
    override fun triggeredBy(i: Instruction) = i is StoreInstruction && i.location == locationId
}

class StepFinished(val predicate: (Instruction) -> Boolean) : DAPStopCondition {
    override val reason      = StoppedEventArgumentsReason.STEP
    override val description = "Step completed"
    override fun triggeredBy(i: Instruction) = predicate(i)
}

class OtherReason(override val description : String) : DAPStopCondition {
    override val reason      = StoppedEventArgumentsReason.STEP
    override fun triggeredBy(i: Instruction) = false
}

fun <T> MutableList<T>.allocate(t : T) : Int {
    val result = size
    add(t)
    return result
}

/** transform this into a completed future */
fun <T> T.asFuture() : CompletableFuture<T> = CompletableFuture.completedFuture(this)

/** Used to indicate a completed future with no result */
val done = CompletableFuture.allOf()!!

