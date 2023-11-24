import kotlinx.serialization.Serializable

@Serializable
data class Trace(
    val contracts    : DataTree.Structure,
    val locations    : Map<LocationId, LocationMetadata>,
    val initState    : Map<LocationId, DataValue>,
    val instructions : List<Instruction>,
    val calls        : Map<CallId, CallMetadata>,
)

/** An abstract tree with [String]-labeled children and [LocationId]s as leaves */
@Serializable
sealed interface DataTree {
    @Serializable
    data class Leaf(val location: LocationId) : DataTree

    @Serializable
    data class Structure(val children : List<Pair<String, DataTree>>) : DataTree

    /**
     * Give the leaf location given by [path]
     * @throws Exception if path is not a valid path to a leaf
     */
    fun get(vararg path : String) : LocationId {
        return if (path.isEmpty()) {
            (this as Leaf).location
        }
        else {
            (this as Structure).children
                .find { it.first == path[0] }!!.second
                .get(*path.drop(1).toTypedArray())
        }
    }
}

/**
 * A location where data is stored (e.g. a storage variable)
 * @param id a unique identifier for the location
 */
typealias LocationId = String
// @Serializable
// data class LocationId(val id: String)

@Serializable
data class LocationMetadata(val type : String)

/**
 * Data that can be stored in a location
 */
@Serializable
data class DataValue(val value : String)

/**
 * An identifier for a specific function call
 * @param name the name of the called function
 * @param callId a unique identifier for this particular call
 */
typealias CallId = String
// @Serializable
// data class CallId(val id : String)

/**
 * Metadata about a specific function call
 */
@Serializable
data class CallMetadata(
    val functionName : String,
    val locals : DataTree.Structure,
    val sourceLocation : SourceLocation
)

/** A line in a source file */
@Serializable
data class SourceLocation(val file : String, val line : Int)