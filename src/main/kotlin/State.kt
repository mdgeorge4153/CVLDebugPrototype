/**
 * The state of execution at a given point in time
 */
class State(
    val trace : Trace
) {
    val stack : MutableList<StackFrame>           = mutableListOf()
    val data  : MutableMap<LocationId, DataValue> = trace.initState.toMutableMap()
    val next  : ListIterator<Instruction>         = trace.instructions.subList(1, trace.instructions.size - 2).listIterator()

    init {
        apply(trace.instructions.first())
    }

    /**
     * Run forward until either:
     *  - the end of the trace is reached
     *  - an executed instruction triggers one or more [stopConditions]
     * @return the list of triggered breakpoints (which is empty if the end of the trace is reached)
     */
    fun <C : StopCondition> runUntil(stopConditions : List<C>) : List<C> {
        while (next.hasNext()) {
            val instruction = next.next()
            apply(instruction)
            val triggered = stopConditions.filter { it.triggeredBy(instruction) }
            if (triggered.isNotEmpty()) { return triggered }
        }

        return emptyList()
    }

    /**
     * Run backward until either:
     *  - the beginning of the trace is reached
     *  - an undone instruction triggers one or more [stopConditions]
     * @return the list of triggered breakpoints (which is empty if the beginning of the trace is reached)
     */
    fun <C : StopCondition> reverseUntil(stopConditions : List<C>) : List<C> {
        while(next.hasPrevious()) {
            val instruction = next.previous()
            unapply(instruction)
            val triggered = stopConditions.filter { it.triggeredBy(instruction) }
            if (triggered.isNotEmpty()) { return triggered }
        }

        return emptyList()
    }

    inner class StackFrame(
        val callId: CallId
    ) {
        var sourceLine : SourceLocation = trace.calls[callId]!!.startLocation
        val call : CallMetadata = trace.calls[callId]!!
    }

    private fun apply(i : Instruction) = when(i) {
        is CallInstruction    -> ignore()
        is NewlineInstruction -> stack.last().sourceLine = i.newLine
        is ReturnInstruction  -> ignore()
        is StoreInstruction   -> data[i.location] = i.newValue

        is LoadInstruction, is AssertInstruction, is RevertInstruction -> Unit
    }

    private fun unapply(i : Instruction) = when(i) {
        is CallInstruction    -> ignore()
        is ReturnInstruction  -> ignore()
        is StoreInstruction   -> ignore()
        is NewlineInstruction -> stack.last().sourceLine = i.oldLine

        is LoadInstruction, is AssertInstruction, is RevertInstruction -> Unit
    }
}

fun ignore(): Unit = Unit
