import kotlinx.serialization.Serializable

/**
 * An event of interest that occurs during execution.  Instructions contain enough data that
 * they can be both applied and unapplied without additional information about the trace.  For example,
 * [StoreInstruction]s contain the old value as well as the new value so that they can be easily rolled back
 */
@Serializable
sealed interface Instruction

/** Read a variable */
@Serializable
class LoadInstruction(val location: LocationId) : Instruction

/** Store [newValue] into [location], overwriting [oldValue].  [oldValue] is null if the location was previously undefined */
@Serializable
class StoreInstruction(
    val location: LocationId,
    val oldValue : DataValue?,
    val newValue : DataValue,
) : Instruction

/** Begin [call] with [args] */
@Serializable
class CallInstruction(
    val call : CallId,
) : Instruction

/** Return from [call], popping local variables [popped] */
@Serializable
class ReturnInstruction(
    val call   : CallId,
) : Instruction

/** An assert statement in spec that fails */
@Serializable
class AssertInstruction : Instruction

/** A contract call reverting */
@Serializable
class RevertInstruction : Instruction

/** Indicates the start of a new source line (for stepping) */
@Serializable
class NewlineInstruction(val context : CallId, val oldLine: SourceLocation, val newLine : SourceLocation) : Instruction
