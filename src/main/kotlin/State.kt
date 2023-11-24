/**
 * The state of execution at a given point in time
 * @param contracts contains a DataTree for each top-level contract
 * @param calls     contains metadata for all function calls
 * @param stack     contains an entry for each stack frame
 * @param data      maps locations to values
 * @param next      a pointer to the next instruction to be executed
 */
class State(
    val contracts  : DataTree.Structure,
    val calls      : Map<CallId, CallMetadata>,
    val stack      : MutableList<StackFrame> = mutableListOf(),
    val data       : MutableMap<LocationId, DataValue>,
    val next       : ListIterator<Instruction>,
)

/** A currently-executing frame on the call stack */
data class StackFrame(
    val call : CallId,
    val locals : DataTree.Structure,
    var sourceLine : SourceLocation
)

