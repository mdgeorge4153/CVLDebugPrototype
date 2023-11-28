interface TreeBuilder {
    fun newStruct(type : String, name : String, f : TreeBuilder.() -> Unit) : DataTree.Structure
    fun newMapping(type : String, name : String, f : TreeBuilder.() -> Unit) : DataTree.Structure
    fun newVariable(type : String, name : String, value : String? = null): LocationId
}

interface TraceBuilder : TreeBuilder {
    fun load(loc : LocationId)
    fun store(loc : LocationId, value : String)
    fun call(functionName : String, sourceFile: String, startLine: Int, endLine: Int, body : TraceBuilder.() -> Unit)
    fun newline(file : String, line : Int)
    val storage : DataTree
}

interface Example {
    fun TreeBuilder.initStorage()

    val ruleName : String
    val ruleFile : String
    val ruleLine : Int
    val ruleEndLine : Int

    fun TraceBuilder.trace()

    fun makeTrace() : Trace {
        val builder = ExampleBuilder { initStorage() }
        builder.call(ruleName, ruleFile, ruleLine) { trace() }
        return builder.getTrace()
    }
}

// private implementation //////////////////////////////////////////////////////////////////////////////////////////////

private class ExampleBuilder(makeStorage : TreeBuilder.() -> Unit) {
    val locations = Allocator<LocationId, LocationMetadata>({ it }, "location")
    val calls     = Allocator<CallId, CallMetadata>({ it }, "call")

    val instructions = mutableListOf<Instruction>()

    val files     = mutableSetOf<String>()

    var currentState : MutableMap<LocationId, DataValue>

    var initialStorage : Map<LocationId, DataValue>
    var storageLayout  : DataTree.Structure

    init {
        val storageBuilder = TreeBuilderImpl("Storage", "", false)
        storageBuilder.makeStorage()
        initialStorage = storageBuilder.state.toMap()
        storageLayout  = storageBuilder.getStructure()
        currentState = storageBuilder.state
    }

    fun call(functionName: String, sourceFile: String, startLine: Int, body: TraceBuilder.() -> Unit) {
        val childBuilder = TraceBuilderImpl(functionName, SourceLocation(sourceFile, startLine))
        instructions.add(CallInstruction(childBuilder.callId))
        childBuilder.body()
        instructions.add(ReturnInstruction(childBuilder.callId))
        calls.objects[childBuilder.callId] = childBuilder.metadata
    }

    inner class TraceBuilderImpl(val functionName : String, val start : SourceLocation) : TraceBuilder {
        val locals : MutableList<Pair<String, DataTree>> = mutableListOf()
        val callId = calls.allocate(postfix = functionName)
        var sourceLocation = start

        override fun load(loc: LocationId) {
            instructions.add(LoadInstruction(loc))
        }

        val metadata : CallMetadata
            get() = CallMetadata(functionName, DataTree.Structure(locals, ""), start, sourceLocation)

        override fun store(loc: LocationId, value: String) {
            instructions.add(StoreInstruction(loc, currentState[loc], DataValue(value)))
            currentState[loc] = DataValue(value)
        }

        override fun call(
            functionName: String,
            sourceFile: String,
            startLine: Int,
            endLine: Int,
            body: TraceBuilder.() -> Unit
        ) {
            this@ExampleBuilder.call(functionName, sourceFile, startLine, body)
        }

        override fun newline(file: String, line: Int) {
            val newLocation = SourceLocation(file, line)
            instructions.add(NewlineInstruction(callId, sourceLocation, newLocation))
            sourceLocation = newLocation
        }

        override val storage: DataTree
            get() = getStorage()

        private fun newChild(type: String, name: String, isMapping: Boolean, f: TreeBuilder.() -> Unit): DataTree.Structure {
            val builder = TreeBuilderImpl(type, "", isMapping)
            builder.f()
            val structure = DataTree.Structure(builder.structure, type)
            locals.add(name to structure)
            builder.state.forEach { (loc, value) -> instructions.add(StoreInstruction(loc, null, value)) }
            return structure
        }

        override fun newMapping(type: String, name: String, f: TreeBuilder.() -> Unit): DataTree.Structure
            = newChild(type, name, isMapping = true, f)

        override fun newStruct(type: String, name: String, f: TreeBuilder.() -> Unit): DataTree.Structure
            = newChild(type, name, isMapping = false, f)

        override fun newVariable(type: String, name: String, value: String?): LocationId {
            val location = locations.allocate(LocationMetadata(type, "$functionName: $name"), name)
            locals.add(name to DataTree.Leaf(location, type))
            value?.let { store(location, it) }
            return location
        }
    }

    fun getStorage() : DataTree.Structure = storageLayout

    fun getTrace() = Trace(
        contracts = storageLayout,
        initState = initialStorage,
        instructions = instructions,
        calls = calls.objects,
        sources = files.toList(),
        locations = locations.objects,
    )

    inner class TreeBuilderImpl(val type : String, val prefix : String, val isMapping : Boolean) : TreeBuilder {
        val structure : MutableList<Pair<String, DataTree>> = mutableListOf()
        val state     : MutableMap<LocationId, DataValue>   = mutableMapOf()

        private fun nameFor(name : String) : String
            = if (isMapping) { "$prefix[$name]" } else if (prefix.isEmpty()) { name } else { "$prefix.$name" }


        fun newChild(type: String, name: String, f: TreeBuilder.() -> Unit, isMapping: Boolean): DataTree.Structure {
            val child = TreeBuilderImpl(type, nameFor(name), isMapping)
            child.f()
            val childTree = DataTree.Structure(child.structure, type)
            structure.add(name to childTree)
            state.putAll(child.state)
            return childTree
        }

        override fun newStruct(type: String, name: String, f: TreeBuilder.() -> Unit): DataTree.Structure
            = newChild(type, name, f, isMapping = false)

        override fun newMapping(type: String, name: String, f: TreeBuilder.() -> Unit): DataTree.Structure
            = newChild(type, name, f, isMapping = true)

        override fun newVariable(type: String, name: String, value: String?): LocationId {
            val location = locations.allocate(LocationMetadata(type, nameFor(name)), name)
            val leaf = DataTree.Leaf(location, type)
            structure.add(name to leaf)
            value?.let { state[location] = DataValue(it) }
            return location
        }

        fun getStructure() : DataTree.Structure = DataTree.Structure(structure, type)
    }

}

/** Convenience class for storing a mapping from fresh ids to values */
private class Allocator<K,V>(val wrapper : (String) -> K, val prefix : String) {
    var count = 0
    val objects : MutableMap<K,V> = mutableMapOf()
    fun allocate(value : V? = null, postfix : String) : K {
        val id = wrapper("$prefix ${count++} ($postfix)")
        value ?.let { objects[id] = it }
        return id
    }
}

