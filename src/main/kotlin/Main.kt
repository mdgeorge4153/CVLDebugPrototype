import kotlinx.serialization.json.Json
import org.eclipse.lsp4j.debug.launch.DSPLauncher
import java.io.File

fun main(args : Array<String>) {
    val input = File("sampleWorkspace/example.trace.json").readText()
    val trace = Json.decodeFromString<Trace>(input)
    val adapter = DebugAdapter(trace)

    val launcher = DSPLauncher.createServerLauncher(adapter, System.`in`, System.out)
    adapter.connect(launcher.remoteProxy)

    launcher.startListening()
}