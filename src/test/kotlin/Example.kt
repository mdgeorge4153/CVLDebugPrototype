import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

val solFile = "/home/mdgeorge/certora/Debugger/sampleWorkspace/ERC20.sol"
val specFile = "/home/mdgeorge/certora/Debugger/sampleWorkspace/ERC20.spec"

/** Based on spec and source in example/ERC20 */
object example : Example {
    override fun TreeBuilder.initStorage() {
        newStruct("ERC20", "ERC20") {
            newVariable("address", "(address)", "0xfffe")

            newStruct("mapping(address => uint)", "_balances") {
                newVariable("uint256", "0xffff", "16")
                newVariable("uing256", "0xfffe")
            }

            newStruct("mapping(address => mapping(address => uint))", "_allowances") {
                newStruct("mapping(address => uint)", "0xfffe") {
                    newVariable("uint256", "0xfffe", "10")
                    newVariable("uint256", "0xffff", "5")
                }
                newStruct("mapping(address => uint)", "TODO address 2") {
                    newVariable("uint256", "0xfffe", "4")
                    newVariable("uint256", "0xffff", "12")
                }

            }

            newVariable("uint256", "_totalSupply")
            newVariable("address", "_owner")
        }
        newStruct("OtherContract", "OtherContract") {
            newVariable("address", "(address)", "0xfffd")
            newVariable("uint256", "x")
        }
        newStruct("", "Ghosts") {
            newStruct("mapping(address => uint)", "nativeBalances") {
                newVariable("uint", "0xfffe", "0")
                newVariable("uint", "0xffff", "0")
            }
            newVariable("mathint", "sum_of_balances", "-2")
        }
    }

    override val ruleFile = specFile
    override val ruleName = "transferSpec"
    override val ruleLine = 17
    override val ruleEndLine = 36

    override fun TraceBuilder.trace() {
        newline(specFile, 18)
        val sender = newVariable("address", "sender", "0xffff")
        val recip  = newVariable("address", "recip", "0xffff")
        val amount = newVariable("uint", "amount", "15")

        newline(specFile, 20)
        val e = newStruct("env", "e") {
            newStruct("env.msg", "msg") {
                newVariable("address", "sender", "0xffff")
                newVariable("uint", "value", "0")
            }
            newStruct("env.block", "block") {
                newVariable("uint", "timestamp", "2")
                newVariable("uint", "number", "3")
            }
        }

        newline(specFile, 21)

        newline(specFile, 23)
        val balance_sender_before = newVariable("mathint", "balance_sender_before")
        call("balanceOf", solFile, 102, 110) {
            val account = newVariable("address", "account", "0xffff")
            newline(solFile, 109)
            load(storage.get("ERC20", "_balances", "0xffff"))
        }
        store(balance_sender_before, "16")

        newline(specFile, 24)
        val balance_recip_before  = newVariable("mathint", "balance_recip_before")
        call("balanceOf", solFile, 102, 110) {
            val account = newVariable("address", "account", "0xffff")
            newline(solFile, 109)
            load(storage.get("ERC20", "_balances", "0xffff"))
        }
        store(balance_recip_before, "16")

        newline(specFile, 26)
        call("transfer", solFile, 120, 128) {
            val recipient = newVariable("address", "recipient", "0xffff")
            val amount    = newVariable("uint256", "amount", "15")

            // transfer
            load(storage.get("Ghosts", "nativeBalances", "0xffff"))
            store(storage.get("Ghosts", "nativeBalances", "0xffff"), "0")
            load(storage.get("Ghosts", "nativeBalances", "0xfffe"))
            store(storage.get("Ghosts", "nativeBalances", "0xfffe"), "0")

            newline(solFile, 126)
            call("_transfer", solFile, 262, 285) {
                newVariable("address", "sender", "0xffff")
                newVariable("address", "recipient", "0xffff")
                newVariable("uint256", "amount", "15")

                newline(solFile, 267)
                newline(solFile, 268)

                newline(solFile, 270)
                call("_beforeTokenTransfer", solFile, 374, 378) {
                    newline(solFile, 378)
                }

                newline(solFile, 272)
                val senderBalance = newVariable("uint256", "senderBalance")
                load(storage.get("ERC20", "_balances", "0xffff"))
                store(senderBalance, "16")

                newline(solFile, 278)
                call("hook Sstore ERC20._balances", specFile, 42, 45) {
                    newVariable("address", "a", "0xffff")
                    newVariable("uint", "new_value", "0x10")
                    newVariable("uint", "old_value", "0x1")

                    newline(specFile, 44)
                    load(storage.get("Ghosts", "sum_of_balances"))
                    store(storage.get("Ghosts", "sum_of_balances"), "-17")
                }
                store(storage.get("ERC20", "_balances", "0xffff"), "1")

                newline(solFile, 280)
                load(storage.get("ERC20", "_balances", "0xffff"))
                call("hook Sstore ERC20._balances", specFile, 42, 45) {
                    newVariable("address", "a", "0xffff")
                    newVariable("uint", "new_value", "0x10")
                    newVariable("uint", "old_value", "0x1")

                    newline(specFile, 44)
                    load(storage.get("Ghosts", "sum_of_balances"))
                    store(storage.get("Ghosts", "sum_of_balances"), "-2")
                }
                store(storage.get("ERC20", "_balances", "0xffff"), "16")

                call("_afterTokenTransfer", solFile, 394, 398) {
                    newline(solFile, 398)
                }
            }
        }

        newline(specFile, 28)
        val balance_sender_after = newVariable("mathint", "balance_sender_after")
        call("balanceOf", solFile, 102, 110) {
            newVariable("address", "account", "0xffff")
            newline(solFile, 109)
            load(storage.get("ERC20", "_balances", "0xffff"))
        }
        store(balance_sender_after, "16")

        newline(specFile, 29)
        val balance_recip_after  = newVariable("mathint", "balance_recip_after")
        call("balanceOf", solFile, 102, 110) {
            newVariable("address", "account", "0xffff")
            newline(solFile, 109)
            load(storage.get("ERC20", "_balances", "0xffff"))
        }
        store(balance_recip_after, "16")

        newline(specFile, 31)
        load(balance_sender_after)
        load(balance_sender_before)

        newline(specFile, 34)
        load(balance_recip_after)
        load(balance_recip_before)
    }
}

fun main() {
    val output = Json.encodeToString(example.makeTrace())
    File("sampleWorkspace/example.trace.json").writeText(output)
}
