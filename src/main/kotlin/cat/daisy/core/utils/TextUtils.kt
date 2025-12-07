package cat.daisy.core.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object TextUtils {
    private val mm = MiniMessage.miniMessage()
    private val logFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private var pluginInstance: Plugin? = null

    object Colors {
        const val PRIMARY = "#3498db"
        const val SECONDARY = "#2ecc71"
        const val ERROR = "#e74c3c"
        const val SUCCESS = "#2ecc71"
        const val WARNING = "#f1c40f"
        const val INFO = "#3498db"
        const val BROADCAST = "#9b59b6"
        const val SYSTEM = "#34495e"
    }

    object Prefix {
        val ERROR = "<${Colors.ERROR}>✖ ".mm()
        val SUCCESS = "<${Colors.SUCCESS}>✔ ".mm()
        val INFO = "<${Colors.INFO}>✦ ".mm()
        val WARNING = "<${Colors.WARNING}>⚠ ".mm()
        val ADMIN = "<${Colors.ERROR}>⚡ ".mm()
        val BROADCAST = "<${Colors.BROADCAST}>» ".mm()
        val SYSTEM = "<${Colors.SYSTEM}>✦ ".mm()
    }

    private val legacyColorMap =
        mapOf(
            "0" to "black",
            "1" to "dark_blue",
            "2" to "dark_green",
            "3" to "dark_aqua",
            "4" to "dark_red",
            "5" to "dark_purple",
            "6" to "gold",
            "7" to "gray",
            "8" to "dark_gray",
            "9" to "blue",
            "a" to "green",
            "b" to "aqua",
            "c" to "red",
            "d" to "light_purple",
            "e" to "yellow",
            "f" to "white",
        )

    private val legacyFormattingMap =
        mapOf(
            "k" to "obfuscated",
            "l" to "bold",
            "m" to "strikethrough",
            "n" to "underlined",
            "o" to "italic",
            "r" to "reset",
        )

    private val legacyColorRegex = """&([0-9a-fk-or])""".toRegex()

    fun initialize(plugin: Plugin) {
        pluginInstance = plugin
    }

    fun log(
        message: String,
        level: String = "INFO",
        throwable: Throwable? = null,
        additionalContext: Map<String, Any> = emptyMap(),
    ) {
        val timestamp = LocalDateTime.now().format(logFormatter)
        val (color, prefix) =
            when (level.uppercase()) {
                "ERROR" -> Colors.ERROR to "✖ "
                "SUCCESS" -> Colors.SUCCESS to "✔ "
                "WARNING" -> Colors.WARNING to "⚠ "
                "BROADCAST" -> Colors.BROADCAST to "» "
                "SYSTEM" -> Colors.SYSTEM to "✦ "
                else -> Colors.INFO to "✦ "
            }

        val detailedMessage =
            buildString {
                append("[$timestamp] [$level] ")
                append(message)

                if (additionalContext.isNotEmpty()) {
                    additionalContext.forEach { (key, value) ->
                        append(" | $key: $value")
                    }
                }
            }

        Bukkit.getConsoleSender().sendMessage("<$color>$prefix$detailedMessage</$color>".mm())

        throwable?.let { exception ->
            Bukkit.getConsoleSender().sendMessage(
                Prefix.ERROR.append("<${Colors.ERROR}>${exception.message}</${Colors.ERROR}>".mm()),
            )
            Bukkit.getConsoleSender().sendMessage("<${Colors.ERROR}>${exception.stackTraceToString()}</${Colors.ERROR}>".mm())
        }
    }

    fun String.mm(): Component =
        mm
            .deserialize(convertLegacyColors())
            .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

    fun String.convertLegacyColors(): String =
        replace(legacyColorRegex) { match ->
            val code = match.groupValues[1]

            when {
                code == "r" -> "<reset><white>"
                legacyColorMap.containsKey(code) -> "<${legacyColorMap[code]}>"
                legacyFormattingMap.containsKey(code) -> "<${legacyFormattingMap[code]}>"
                else -> match.value
            }
        }
}
