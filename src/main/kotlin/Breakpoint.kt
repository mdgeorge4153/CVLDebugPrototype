/** A reason to stop execution.  May represent a user-specified breakpoint or an internal step boundary */
interface Breakpoint {
    /** @return true if this breakpoint should be triggered by instruction [i] */
    fun triggeredBy(i: Instruction) : Boolean
}

class LoadBreakpoint(val variable : LocationId) : Breakpoint {
    override fun triggeredBy(i: Instruction)
        = i is LoadInstruction && i.location == variable
}

class StoreBreakpoint(val variable : LocationId) : Breakpoint {
    override fun triggeredBy(i: Instruction): Boolean
        = i is StoreInstruction && i.location == variable
}

class CallBreakpoint(val call : CallId) : Breakpoint {
    override fun triggeredBy(i: Instruction): Boolean
        = i is CallInstruction && i.call == call
}

class ReturnBreakpoint(val call : CallId) : Breakpoint {
    override fun triggeredBy(i: Instruction): Boolean
        = i is ReturnInstruction && i.call == call
}

class AssertBreakpoint : Breakpoint {
    override fun triggeredBy(i: Instruction): Boolean
        = i is AssertInstruction
}

class RevertBreakpoint : Breakpoint {
    override fun triggeredBy(i: Instruction): Boolean
        = i is RevertInstruction
}

class NewlineBreakpoint(val context : CallId) : Breakpoint {
    override fun triggeredBy(i: Instruction): Boolean
        = i is NewlineInstruction && i.context == context
}