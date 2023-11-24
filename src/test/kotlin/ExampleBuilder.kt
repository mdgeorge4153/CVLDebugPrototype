interface TreeBuilder {
    fun newStruct(name : String, f : TreeBuilder.() -> Unit) : DataTree.Structure
    fun newVariable(type : String, name : String, value : String? = null): LocationId
}

interface TraceBuilder : TreeBuilder {
    fun load(loc : LocationId) : Unit
    fun store(loc : LocationId, value : String) : Unit
    fun call(functionName : String, sourceFile: String, line : Int, body : TraceBuilder.() -> Unit)
    fun newline(file : String, line : Int)
    val storage : DataTree
}

interface Example {
    fun TreeBuilder.initStorage()

    val ruleName : String
    val ruleFile : String
    val ruleLine : Int

    fun TraceBuilder.trace()

    fun makeTrace() : Trace {
        val builder = ExampleBuilder()
        builder.storageBuilder.initStorage()
        builder.traceBuilder(ruleName, SourceLocation(ruleFile, ruleLine)).trace()
        return builder.getTrace()
    }
}

// private implementation //////////////////////////////////////////////////////////////////////////////////////////////

private class ExampleBuilder {
    val locations = Allocator<LocationId, LocationMetadata>({ it }, "location")
    val calls     = Allocator<CallId, CallMetadata>({ it }, "call")

    val instructions : MutableList<Instruction> = mutableListOf()
    val currentState : MutableMap<LocationId, DataValue> = mutableMapOf()

    val storageBuilder : TreeBuilderImpl  = TreeBuilderImpl()

    fun traceBuilder(functionName : String, source : SourceLocation) : TraceBuilderImpl {
        currentState.putAll(storageBuilder.state)
        return TraceBuilderImpl(functionName, source)
    }

    inner class TraceBuilderImpl(functionName : String, source : SourceLocation) : TraceBuilder {
        val locals : MutableList<Pair<String, DataTree>> = mutableListOf()
        val callId = calls.allocate(CallMetadata(functionName, DataTree.Structure(locals), source), functionName)
        var sourceLocation = source

        override fun load(loc: LocationId) {
            instructions.add(LoadInstruction(loc))
        }

        override fun store(loc: LocationId, value: String) {
            instructions.add(StoreInstruction(loc, currentState[loc], DataValue(value)))
            currentState[loc] = DataValue(value)
        }

        override fun call(functionName: String, sourceFile: String, line: Int, body: TraceBuilder.() -> Unit) {
            val childBuilder = TraceBuilderImpl(functionName, SourceLocation(sourceFile, line))
            instructions.add(CallInstruction(childBuilder.callId))
            childBuilder.body()
            instructions.add(ReturnInstruction(childBuilder.callId))
        }

        override fun newline(file: String, line: Int) {
            val newLocation = SourceLocation(file, line)
            instructions.add(NewlineInstruction(callId, sourceLocation, newLocation))
            sourceLocation = newLocation
        }

        override val storage: DataTree
            get() = getStorage()

        override fun newStruct(name: String, f: TreeBuilder.() -> Unit): DataTree.Structure {
            val builder = TreeBuilderImpl()
            builder.f()
            val structure = DataTree.Structure(builder.structure)
            locals.add(name to structure)
            builder.state.forEach { (loc, value) -> instructions.add(StoreInstruction(loc, null, value)) }
            return structure
        }

        override fun newVariable(type: String, name: String, value: String?): LocationId {
            val location = locations.allocate(LocationMetadata(type), name)
            locals.add(name to DataTree.Leaf(location))
            value?.let { store(location, it) }
            return location
        }
    }

    fun getStorage() : DataTree.Structure = storageBuilder.getStructure()

    fun getTrace() = Trace(
        contracts = storageBuilder.getStructure(),
        locations = locations.objects,
        initState = storageBuilder.state,
        instructions = instructions,
        calls = calls.objects,
    )

    inner class TreeBuilderImpl : TreeBuilder {
        val structure : MutableList<Pair<String, DataTree>> = mutableListOf()
        val state     : MutableMap<LocationId, DataValue>   = mutableMapOf()

        override fun newStruct(name: String, f: TreeBuilder.() -> Unit): DataTree.Structure {
            val child = TreeBuilderImpl()
            child.f()
            val childTree = DataTree.Structure(child.structure)
            structure.add(name to childTree)
            state.putAll(child.state)
            return childTree
        }

        override fun newVariable(type: String, name: String, value: String?): LocationId {
            val location = locations.allocate(LocationMetadata(type), name)
            val leaf = DataTree.Leaf(location)
            structure.add(name to leaf)
            value?.let { state[location] = DataValue(it) }
            return location
        }

        fun getStructure() : DataTree.Structure = DataTree.Structure(structure)
    }

}

/** Convenience class for storing a mapping from fresh ids to values */
private class Allocator<K,V>(val wrapper : (String) -> K, val prefix : String) {
    var count = 0
    val objects : MutableMap<K,V> = mutableMapOf()
    fun allocate(value : V, postfix : String) : K {
        val id = wrapper("$prefix ${count++} ($postfix)")
        objects[id] = value
        return id
    }
}

