import kotlinx.serialization.Serializable

@Serializable
data class Trace(
    val contracts    : DataTree.Structure,
    val initState    : Map<LocationId, DataValue>,
    val instructions : List<Instruction>,
    val calls        : Map<CallId, CallMetadata>,
    val locations    : Map<LocationId, LocationMetadata>,
    val sources      : List<String>,
)

/** An abstract tree with [String]-labeled children and [LocationId]s as leaves */
@Serializable
sealed interface DataTree {
    val cvlType : String

    @Serializable
    data class Leaf(val location: LocationId, override val cvlType: String) : DataTree

    @Serializable
    data class Structure(val children : List<Pair<String, DataTree>>, override val cvlType: String) : DataTree

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

/** A location where data is stored (e.g. a storage variable) */
typealias LocationId = String
// @Serializable
// data class LocationId(val id: String)

/**
 * Metadata about a location
 * @param type the CVL or Solidity type to be displayed for the location
 * @param name the human-readable name to be displayed for data breakpoints on this location
 */
@Serializable
data class LocationMetadata(
    val type : String,
    val name : String,
)

/**
 * Data that can be stored in a location
 */
@Serializable
data class DataValue(val value : String)

/** An identifier for a specific function call */
typealias CallId = String
// @Serializable
// data class CallId(val id : String)

/** Metadata about a specific function call */
@Serializable
data class CallMetadata(
    val functionName : String,
    val locals : DataTree.Structure,
    val startLocation : SourceLocation,
    val endLocation   : SourceLocation,
)

/** A line in a source file */
@Serializable
data class SourceLocation(val file : String, val line : Int)