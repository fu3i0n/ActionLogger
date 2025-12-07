package cat.daisy.core.commands.staff

import cat.daisy.core.Core
import cat.daisy.core.database.DatabaseManager
import cat.daisy.core.utils.TextUtils.mm
import dev.jorel.commandapi.kotlindsl.commandAPICommand
import dev.jorel.commandapi.kotlindsl.playerExecutor
import dev.jorel.commandapi.kotlindsl.subcommand

fun registerLoggerCommand() {
    commandAPICommand("logger") {
        withPermission("daisy.logger")

        playerExecutor { player, _ ->
            val svc = Core.instance.getActionLogService()
            val (lq, cq) = svc.stats()

            player.sendMessage("<gradient:#00D4FF:#7B2FF7>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>".mm())
            player.sendMessage("<gradient:#00D4FF:#7B2FF7><bold>  ActionLogger Pro - System Status</bold></gradient>".mm())
            player.sendMessage("<gradient:#00D4FF:#7B2FF7>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>".mm())
            player.sendMessage("".mm())
            player.sendMessage("<#00D4FF>📊 Queue Status:".mm())
            player.sendMessage("<#7B2FF7>  ├─ Logs: <white>$lq".mm())
            player.sendMessage("<#7B2FF7>  └─ Containers: <white>$cq".mm())
            player.sendMessage("".mm())
            player.sendMessage("<#FFD93D>🔧 Available Commands:".mm())
            player.sendMessage("<#8B93B8>  • <#00FF9F>/logger stats<#8B93B8> - View detailed statistics".mm())
            player.sendMessage("<#8B93B8>  • <#00FF9F>/logger flush<#8B93B8> - Force flush queues".mm())
            player.sendMessage("<#8B93B8>  • <#00FF9F>/logger pool<#8B93B8> - Check database pool".mm())
            player.sendMessage("".mm())
            player.sendMessage("<gradient:#00D4FF:#7B2FF7>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>".mm())
        }

        // /logger stats
        subcommand("stats") {
            playerExecutor { player, _ ->
                val (lq, cq) = Core.instance.getActionLogService().stats()
                val poolStats = DatabaseManager.getPoolStats()

                player.sendMessage("<gradient:#00D4FF:#7B2FF7>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>".mm())
                player.sendMessage("<gradient:#FFD93D:#FF3864><bold>  📊 Detailed Statistics</bold></gradient>".mm())
                player.sendMessage("<gradient:#00D4FF:#7B2FF7>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>".mm())
                player.sendMessage("".mm())
                player.sendMessage("<#00D4FF>⏳ Queue Statistics:".mm())
                player.sendMessage("<#7B2FF7>  ├─ Log Queue: <white>$lq <#8B93B8>(pending writes)".mm())
                player.sendMessage("<#7B2FF7>  └─ Container Queue: <white>$cq <#8B93B8>(pending writes)".mm())
                player.sendMessage("".mm())
                player.sendMessage("<#00FF9F>💾 Database Pool:".mm())
                player.sendMessage("<#7B2FF7>  └─ <white>$poolStats".mm())
                player.sendMessage("".mm())
                player.sendMessage("<gradient:#00D4FF:#7B2FF7>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>".mm())
            }
        }

        // /logger flush
        subcommand("flush") {
            playerExecutor { player, _ ->
                val before = Core.instance.getActionLogService().stats()
                Core.instance.getActionLogService().flush()

                player.sendMessage("<gradient:#00FF9F:#00D4FF>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>".mm())
                player.sendMessage("<gradient:#00FF9F:#00D4FF><bold>  🔄 Flush Triggered</bold></gradient>".mm())
                player.sendMessage("<gradient:#00FF9F:#00D4FF>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>".mm())
                player.sendMessage("".mm())
                player.sendMessage("<#00FF9F>✅ Flushing ${before.first} logs and ${before.second} containers...".mm())
                player.sendMessage("<#8B93B8>  This may take a few seconds for large queues.".mm())
                player.sendMessage("".mm())
                player.sendMessage("<gradient:#00FF9F:#00D4FF>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>".mm())
            }
        }

        // /logger pool
        subcommand("pool") {
            playerExecutor { player, _ ->
                val poolStats = DatabaseManager.getPoolStats()

                player.sendMessage("<gradient:#7B2FF7:#FF3864>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>".mm())
                player.sendMessage("<gradient:#7B2FF7:#FF3864><bold>  💾 Connection Pool Status</bold></gradient>".mm())
                player.sendMessage("<gradient:#7B2FF7:#FF3864>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>".mm())
                player.sendMessage("".mm())
                player.sendMessage("<#7B2FF7>Database Pool: <white>$poolStats".mm())
                player.sendMessage("".mm())
                player.sendMessage("<#8B93B8><italic>Low active connections = good performance".mm())
                player.sendMessage("<#8B93B8><italic>High waiting threads = need optimization".mm())
                player.sendMessage("".mm())
                player.sendMessage("<gradient:#7B2FF7:#FF3864>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>".mm())
            }
        }
    }
}
