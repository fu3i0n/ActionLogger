package cat.daisy.core.commands.staff

import cat.daisy.core.guis.LogViewerFilter
import cat.daisy.core.guis.openLogViewer
import cat.daisy.core.utils.TextUtils.mm
import dev.jorel.commandapi.kotlindsl.commandAPICommand
import dev.jorel.commandapi.kotlindsl.integerArgument
import dev.jorel.commandapi.kotlindsl.playerExecutor
import dev.jorel.commandapi.kotlindsl.stringArgument
import dev.jorel.commandapi.kotlindsl.subcommand

fun registerLogViewerCommand() {
    commandAPICommand("logviewer") {
        withPermission("daisy.logger.viewer")

        // /logviewer (self open)
        playerExecutor { player, _ ->
            player.openLogViewer()
            player.sendMessage("<gradient:#00D4FF:#7B2FF7><bold>ActionLogger</bold></gradient> <white>Opening log viewer...".mm())
        }

        // /logviewer player <name>
        subcommand("player") {
            stringArgument("name")
            playerExecutor { player, args ->
                val name = args["name"] as String
                player.openLogViewer(filter = LogViewerFilter(player = name))
                player.sendMessage(
                    "<gradient:#00D4FF:#7B2FF7><bold>ActionLogger</bold></gradient> <white>Filtering logs for <#7B2FF7>$name".mm(),
                )
            }
        }

        // /logviewer action <code>
        subcommand("action") {
            integerArgument("code")
            playerExecutor { player, args ->
                val code = args["code"] as Int
                val actionName =
                    when (code) {
                        0 -> "Block Placed"
                        1 -> "Block Broken"
                        4 -> "Chat"
                        5 -> "Command"
                        6 -> "Login"
                        7 -> "Logout"
                        8 -> "Item Drop"
                        9 -> "Item Pickup"
                        else -> "Unknown"
                    }
                player.openLogViewer(filter = LogViewerFilter(action = code))
                player.sendMessage(
                    "<gradient:#00D4FF:#7B2FF7><bold>ActionLogger</bold></gradient> <white>Filtering logs for <#00D4FF>$actionName".mm(),
                )
            }
        }

        // /logviewer last <minutes>
        subcommand("last") {
            integerArgument("minutes")
            playerExecutor { player, args ->
                val minutes = (args["minutes"] as Int).coerceAtLeast(1)
                val now = (System.currentTimeMillis() / 1000L).toInt()
                val from = now - minutes * 60
                player.openLogViewer(filter = LogViewerFilter(fromEpochSec = from, toEpochSec = now))
                player.sendMessage(
                    "<gradient:#00D4FF:#7B2FF7><bold>ActionLogger</bold></gradient> <white>Showing logs from last <#FFD93D>$minutes minutes"
                        .mm(),
                )
            }
        }
    }
}
