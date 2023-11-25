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

    private fun apply(i : Instruction) = when(i) {
        is CallInstruction    -> stack.push(StackFrame(i.call)).ignore()
        is NewlineInstruction -> stack.last().sourceLine = i.newLine
        is ReturnInstruction  -> stack.pop().ignore()
        is StoreInstruction   -> data[i.location] = i.newValue

        is LoadInstruction, is AssertInstruction, is RevertInstruction -> Unit
    }

    private fun unapply(i : Instruction) = when(i) {
        is CallInstruction    -> stack.pop().ignore()
        is ReturnInstruction  -> stack.push(StackFrame(i.call)).ignore()
        is StoreInstruction   -> data.replaceOrDrop(i.location, i.oldValue).ignore()
        is NewlineInstruction -> stack.last().sourceLine = i.oldLine

        is LoadInstruction, is AssertInstruction, is RevertInstruction -> Unit
    }

    /**
     * Run forward until either:
     *  - the end of the trace is reached
     *  - an executed instruction satisfies [predicate]
     */
    fun runUntil(predicate : (Instruction) -> Boolean) {
        if (!next.hasNext()) { return }

        do {
            val instruction = next.next()
            apply(instruction)
        } while (next.hasNext() && !predicate(instruction))
    }

    /**
     * Run backward until either:
     *  - the beginning of the trace is reached
     *  - an undone instruction satisfies [predicate]
     */
    fun reverseUntil(predicate : (Instruction) -> Boolean) {
        if (!next.hasPrevious()) { return }

        do {
            val instruction = next.previous()
            unapply(instruction)
        } while(next.hasPrevious() && !predicate(instruction))
    }

    inner class StackFrame(
        val callId: CallId
    ) {
        var sourceLine : SourceLocation = trace.calls[callId]!!.startLocation
        val call : CallMetadata = trace.calls[callId]!!
    }
}


fun Any?.ignore() : Unit = Unit

fun <K,V> MutableMap<K,V>.replaceOrDrop(key : K, value : V?) : V?
        = value?.let { this.replace(key, it) } ?: this.remove(key)

fun <T> MutableList<T>.push(e : T) = add(e)
fun <T> MutableList<T>.pop() : T   = removeLast()
