import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File


/** Based on spec and source in example/ERC20 */
object example : Example {
    override fun TreeBuilder.initStorage() {
        newStruct("ERC20") {
            newVariable("address", "(address)", "0xfffe")

            newStruct("_balances") {
                newVariable("uint256", "0xffff", "16")
                newVariable("uing256", "0xfffe")
            }

            newStruct("_allowances") {
                newStruct("0xfffe") {
                    newVariable("uint256", "0xfffe")
                    newVariable("uint256", "0xffff")
                }
                newStruct("TODO address 2") {
                    newVariable("uint256", "0xfffe")
                    newVariable("uint256", "0xffff")
                }

            }

            newVariable("uint256", "_totalSupply")
            newVariable("address", "_owner")
        }
        newStruct("OtherContract") {
            newVariable("address", "(address)", "0xfffd")
            newVariable("uint256", "x")
        }
        newStruct("Ghosts") {
            newStruct("nativeBalances") {
                newVariable("uint", "0xfffe", "0")
                newVariable("uint", "0xffff", "0")
            }
            newVariable("mathint", "sum_of_balances", "-2")
        }
    }

    override val ruleFile = "ERC20.spec"
    override val ruleName = "transferSpec"
    override val ruleLine = 17
    override val ruleEndLine = 36

    override fun TraceBuilder.trace() {
        newline("ERC20.spec", 18)
        val sender = newVariable("address", "sender", "0xffff")
        val recip  = newVariable("address", "recip", "0xffff")
        val amount = newVariable("uint", "amount", "15")

        newline("ERC20.spec", 20)
        val e = newStruct("e") {
            newStruct("msg") {
                newVariable("address", "sender", "0xffff")
                newVariable("uint", "value", "0")
            }
            newStruct("block") {
                newVariable("uint", "timestamp", "2")
                newVariable("uint", "number", "3")
            }
        }

        newline("ERC20.spec", 21)

        newline("ERC20.spec", 23)
        val balance_sender_before = newVariable("mathint", "balance_sender_before")
        call("balanceOf", "ERC20.sol", 102, 110) {
            val account = newVariable("address", "account", "0xffff")
            newline("ERC20.sol", 109)
            load(storage.get("ERC20", "_balances", "0xffff"))
        }
        store(balance_sender_before, "16")

        newline("ERC20.spec", 24)
        val balance_recip_before  = newVariable("mathint", "balance_recip_before")
        call("balanceOf", "ERC20.sol", 102, 110) {
            val account = newVariable("address", "account", "0xffff")
            newline("ERC20.sol", 109)
            load(storage.get("ERC20", "_balances", "0xffff"))
        }
        store(balance_recip_before, "16")

        newline("ERC20.spec", 26)
        call("transfer", "ERC20.sol", 120, 128) {
            val recipient = newVariable("address", "recipient", "0xffff")
            val amount    = newVariable("uint256", "amount", "15")

            // transfer
            load(storage.get("Ghosts", "nativeBalances", "0xffff"))
            store(storage.get("Ghosts", "nativeBalances", "0xffff"), "0")
            load(storage.get("Ghosts", "nativeBalances", "0xfffe"))
            store(storage.get("Ghosts", "nativeBalances", "0xfffe"), "0")

            newline("ERC20.sol", 126)
            call("_transfer", "ERC20.sol", 262, 285) {
                newVariable("address", "sender", "0xffff")
                newVariable("address", "recipient", "0xffff")
                newVariable("uint256", "amount", "15")

                newline("ERC20.sol", 267)
                newline("ERC20.sol", 268)

                newline("ERC20.sol", 270)
                call("_beforeTokenTransfer", "ERC20.sol", 374, 378) {
                    newline("ERC20.sol", 378)
                }

                newline("ERC20.sol", 272)
                val senderBalance = newVariable("uint256", "senderBalance")
                load(storage.get("ERC20", "_balances", "0xffff"))
                store(senderBalance, "16")

                newline("ERC20.sol", 278)
                call("hook Sstore ERC20._balances", "ERC20.spec", 42, 45) {
                    newVariable("address", "a", "0xffff")
                    newVariable("uint", "new_value", "0x10")
                    newVariable("uint", "old_value", "0x1")

                    newline("ERC20.spec", 44)
                    load(storage.get("Ghosts", "sum_of_balances"))
                    store(storage.get("Ghosts", "sum_of_balances"), "-17")
                }
                store(storage.get("ERC20", "_balances", "0xffff"), "1")

                newline("ERC20.sol", 280)
                load(storage.get("ERC20", "_balances", "0xffff"))
                call("hook Sstore ERC20._balances", "ERC20.spec", 42, 45) {
                    newVariable("address", "a", "0xffff")
                    newVariable("uint", "new_value", "0x10")
                    newVariable("uint", "old_value", "0x1")

                    newline("ERC20.spec", 44)
                    load(storage.get("Ghosts", "sum_of_balances"))
                    store(storage.get("Ghosts", "sum_of_balances"), "-2")
                }
                store(storage.get("ERC20", "_balances", "0xffff"), "16")

                call("_afterTokenTransfer", "ERC20.sol", 394, 398) {
                    newline("ERC20.sol", 398)
                }
            }
        }

        newline("ERC20.spec", 28)
        val balance_sender_after = newVariable("mathint", "balance_sender_after")
        call("balanceOf", "ERC20.sol", 102, 110) {
            newVariable("address", "account", "0xffff")
            newline("ERC20.sol", 109)
            load(storage.get("ERC20", "_balances", "0xffff"))
        }
        store(balance_sender_after, "16")

        newline("ERC20.spec", 29)
        val balance_recip_after  = newVariable("mathint", "balance_recip_after")
        call("balanceOf", "ERC20.sol", 102, 110) {
            newVariable("address", "account", "0xffff")
            newline("ERC20.sol", 109)
            load(storage.get("ERC20", "_balances", "0xffff"))
        }
        store(balance_recip_after, "16")

        newline("ERC20.spec", 31)
        load(balance_sender_after)
        load(balance_sender_before)

        newline("ERC20.spec", 34)
        load(balance_recip_after)
        load(balance_recip_before)
    }
}

fun main() {
    val output = Json.encodeToString(example.makeTrace())
    File("sampleWorkspace/example.trace.json").writeText(output)
}
