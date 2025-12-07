package cat.daisy.core.service

import cat.daisy.core.Core
import cat.daisy.core.utils.TextUtils.log
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class ConfigValueType {
    STRING,
    INT,
    DOUBLE,
    BOOLEAN,
    LONG,
    LIST,
    SECTION,
    ;

    override fun toString(): String = name.lowercase()
}

open class ConfigException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class ConfigValidationException(
    message: String,
) : ConfigException(message)

class ConfigSaveException(
    message: String,
    cause: Throwable? = null,
) : ConfigException(message, cause)

object ConfigService {
    private data class CachedConfig(
        val config: FileConfiguration,
        val timestamp: Long = System.currentTimeMillis(),
        val lastModified: Long = 0,
    )

    const val COMMANDS = "commands"
    const val EVENTS = "events"
    const val GUIS = "guis"

    private val configCache = ConcurrentHashMap<String, CachedConfig>()
    private val cacheExpiry = TimeUnit.MINUTES.toMillis(5)
    private val moduleConfigs = ConcurrentHashMap<String, FileConfiguration>()
    private val moduleFiles = ConcurrentHashMap<String, File>()

    // Module structure definition - only active modules needed for ActionLogger
    private val moduleStructure =
        mapOf(
            COMMANDS to
                mapOf(
                    "single" to emptyList<String>(),
                    "multi" to emptyMap<String, List<String>>(),
                ),
            EVENTS to
                mapOf(
                    "single" to emptyList<String>(),
                    "multi" to
                        mapOf(
                            "actionlogger" to listOf("settings.yml"),
                        ),
                ),
            GUIS to
                mapOf(
                    "single" to emptyList<String>(),
                    "multi" to
                        mapOf(
                            "logviewer" to listOf("lang.yml", "settings.yml"),
                        ),
                ),
        )

    /**
     * Load a configuration file
     */
    @Throws(ConfigException::class)
    fun loadConfig(
        plugin: JavaPlugin,
        fileName: String,
        validate: Boolean = true,
        requiredKeys: Map<String, ConfigValueType> = emptyMap(),
    ): FileConfiguration {
        val file = File(plugin.dataFolder, fileName)
        file.parentFile?.mkdirs()

        // Check cache
        configCache[fileName]?.let { cached ->
            if (file.exists() &&
                file.lastModified() <= cached.lastModified &&
                System.currentTimeMillis() - cached.timestamp < cacheExpiry
            ) {
                return cached.config
            }
        }

        return try {
            if (!file.exists()) {
                val resourcePath = fileName.removePrefix("/")
                plugin.saveResource(resourcePath, false)
                log("Created default configuration file: $fileName", "INFO")
            }

            val config = YamlConfiguration.loadConfiguration(file)

            if (validate && requiredKeys.isNotEmpty()) {
                validateConfig(config, fileName, requiredKeys)
            }

            configCache[fileName] =
                CachedConfig(
                    config = config,
                    timestamp = System.currentTimeMillis(),
                    lastModified = file.lastModified(),
                )

            config
        } catch (e: Exception) {
            throw when (e) {
                is IOException -> ConfigException("Failed to load config $fileName: ${e.message}", e)
                is ConfigException -> e
                else -> ConfigException("Unexpected error loading config $fileName: ${e.message}", e)
            }
        }
    }

    /**
     * Validate configuration against required keys and types
     */
    @Throws(ConfigValidationException::class)
    private fun validateConfig(
        config: FileConfiguration,
        fileName: String,
        requiredKeys: Map<String, ConfigValueType>,
    ) {
        val missingKeys = mutableListOf<String>()
        val typeErrors = mutableListOf<String>()

        requiredKeys.forEach { (key, type) ->
            if (!config.contains(key)) {
                missingKeys.add(key)
            } else if (!isCorrectType(config, key, type)) {
                typeErrors.add("$key (expected $type)")
            }
        }

        if (missingKeys.isNotEmpty() || typeErrors.isNotEmpty()) {
            val errorMsg =
                buildString {
                    append("Configuration validation failed for $fileName: ")
                    if (missingKeys.isNotEmpty()) {
                        append("Missing keys: ${missingKeys.joinToString(", ")}. ")
                    }
                    if (typeErrors.isNotEmpty()) {
                        append("Type errors: ${typeErrors.joinToString(", ")}.")
                    }
                }
            throw ConfigValidationException(errorMsg)
        }
    }

    /**
     * Check if config value is correct type
     */
    private fun isCorrectType(
        config: FileConfiguration,
        key: String,
        type: ConfigValueType,
    ): Boolean =
        when (type) {
            ConfigValueType.STRING -> config.isString(key)
            ConfigValueType.INT -> config.isInt(key)
            ConfigValueType.DOUBLE -> config.isDouble(key)
            ConfigValueType.BOOLEAN -> config.isBoolean(key)
            ConfigValueType.LONG -> config.isLong(key) || config.isInt(key)
            ConfigValueType.LIST -> config.isList(key)
            ConfigValueType.SECTION -> config.isConfigurationSection(key)
        }

    /**
     * Load a module configuration
     */
    @Throws(ConfigException::class)
    private fun loadModuleConfig(
        plugin: JavaPlugin,
        category: String,
        module: String,
        fileName: String,
        validate: Boolean = true,
        requiredKeys: Map<String, ConfigValueType> = emptyMap(),
    ): FileConfiguration {
        val path = "modules/$category/$module/$fileName"
        val config = loadConfig(plugin, path, validate, requiredKeys)

        val configKey = "$category/$module/$fileName"
        moduleConfigs[configKey] = config
        moduleFiles[configKey] = File(plugin.dataFolder, path)

        return config
    }

    /**
     * Get a module configuration
     */
    fun getModuleConfig(
        category: String,
        module: String,
        fileName: String,
    ): FileConfiguration? = moduleConfigs["$category/$module/$fileName"]

    /**
     * Load all configurations
     */
    @Throws(ConfigException::class)
    fun loadConfigs() {
        try {
            log("Loading all configurations...", "INFO")
            configCache.clear()
            moduleConfigs.clear()
            moduleFiles.clear()

            val plugin = Core.instance

            // Load all categories
            moduleStructure.forEach { (category, structure) ->
                loadModulesInCategory(plugin, category, structure)
            }

            log("Loaded ${moduleConfigs.size} module configurations successfully", "SUCCESS")
        } catch (e: ConfigException) {
            log("Failed to load configuration files", "ERROR", e)
            throw e
        }
    }

    /**
     * Load all modules in a category
     */
    private fun loadModulesInCategory(
        plugin: JavaPlugin,
        category: String,
        structure: Map<String, Any>,
    ) {
        // Load single-file modules
        @Suppress("UNCHECKED_CAST")
        val singleModules = structure["single"] as? List<String> ?: emptyList()
        singleModules.forEach { module ->
            loadModuleConfig(plugin, category, module, "lang.yml")
        }

        // Load multi-file modules
        @Suppress("UNCHECKED_CAST")
        val multiModules = structure["multi"] as? Map<String, List<String>> ?: emptyMap()
        multiModules.forEach { (module, files) ->
            files.forEach { file ->
                loadModuleConfig(plugin, category, module, file)
            }
        }
    }

    /**
     * Save a configuration file
     */
    @Throws(ConfigSaveException::class)
    private fun saveConfig(
        config: FileConfiguration,
        file: File,
    ) {
        try {
            file.parentFile?.mkdirs()
            config.save(file)

            val fileName = file.name
            configCache[fileName]?.let { cached ->
                configCache[fileName] = cached.copy(lastModified = file.lastModified())
            }
        } catch (e: IOException) {
            val errorMsg = "Failed to save config ${file.name}: ${e.message}"
            log(errorMsg, "ERROR", e)
            throw ConfigSaveException(errorMsg, e)
        }
    }

    /**
     * Save a specific module configuration
     */
    @Throws(ConfigSaveException::class)
    fun saveModuleConfig(
        category: String,
        module: String,
        fileName: String,
    ) {
        val key = "$category/$module/$fileName"
        val config = moduleConfigs[key] ?: throw ConfigException("Module config not found: $key")
        val file = moduleFiles[key] ?: throw ConfigException("Module file not found: $key")

        saveConfig(config, file)
        log("Saved module configuration: $key", "INFO")
    }

    /**
     * Save all module configurations
     */
    @Throws(ConfigSaveException::class)
    fun saveAllConfigs() {
        try {
            log("Saving all module configurations...", "INFO")
            var saved = 0

            moduleConfigs.keys.forEach { key ->
                try {
                    val parts = key.split("/")
                    if (parts.size == 3) {
                        saveModuleConfig(parts[0], parts[1], parts[2])
                        saved++
                    }
                } catch (e: Exception) {
                    log("Failed to save module config $key: ${e.message}", "WARNING")
                }
            }

            log("Saved $saved module configurations successfully", "SUCCESS")
        } catch (e: ConfigSaveException) {
            log("Failed to save all configurations", "ERROR", e)
            throw e
        }
    }

    /**
     * Clear all caches
     */
    fun clearCaches() {
        configCache.clear()
        log("Configuration cache cleared", "INFO")
    }

    /**
     * Get language configuration
     */
    fun getModuleLang(
        category: String,
        module: String,
    ): FileConfiguration? = getModuleConfig(category, module, "lang.yml")

    /**
     * Get language string with default
     */
    fun getModuleLangString(
        category: String,
        module: String,
        path: String,
        default: String = "",
    ): String {
        val config = getModuleLang(category, module) ?: return default
        return config.getString(path) ?: default
    }

    /**
     * Get settings configuration
     */
    fun getModuleSettings(
        category: String,
        module: String,
    ): FileConfiguration? = getModuleConfig(category, module, "settings.yml")

    /**
     * Get settings string with default
     */
    fun getModuleSettingString(
        category: String,
        module: String,
        path: String,
        default: String = "",
    ): String {
        val config = getModuleSettings(category, module) ?: return default
        return config.getString(path) ?: default
    }

    /**
     * Get settings integer with default
     */
    fun getModuleSettingInt(
        category: String,
        module: String,
        path: String,
        default: Int = 0,
    ): Int {
        val config = getModuleSettings(category, module) ?: return default
        return config.getInt(path, default)
    }

    /**
     * Get settings double with default
     */
    fun getModuleSettingDouble(
        category: String,
        module: String,
        path: String,
        default: Double = 0.0,
    ): Double {
        val config = getModuleSettings(category, module) ?: return default
        return config.getDouble(path, default)
    }

    /**
     * Get settings boolean with default
     */
    fun getModuleSettingBoolean(
        category: String,
        module: String,
        path: String,
        default: Boolean = false,
    ): Boolean {
        val config = getModuleSettings(category, module) ?: return default
        return config.getBoolean(path, default)
    }

    /**
     * Get settings section with default
     */
    fun getModuleSettingSection(
        category: String,
        module: String,
        path: String,
    ): ConfigurationSection? {
        val config = getModuleSettings(category, module) ?: return null
        return config.getConfigurationSection(path)
    }

    /**
     * Get settings with generic type
     */
    inline fun <reified T> getModuleSetting(
        category: String,
        module: String,
        path: String,
        default: T,
    ): T {
        val config = getModuleSettings(category, module) ?: return default
        return getConfigValue(config, path, default)
    }

    /**
     * Get config value with generic type
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> getConfigValue(
        config: FileConfiguration,
        path: String,
        default: T,
    ): T =
        when (T::class) {
            String::class -> config.getString(path, default as? String ?: "") as T
            Int::class -> config.getInt(path, default as? Int ?: 0) as T
            Double::class -> config.getDouble(path, default as? Double ?: 0.0) as T
            Boolean::class -> config.getBoolean(path, default as? Boolean ?: false) as T
            Long::class -> config.getLong(path, default as? Long ?: 0L) as T
            List::class -> config.getList(path, default as? List<*> ?: emptyList<Any>()) as T
            ConfigurationSection::class -> config.getConfigurationSection(path) as? T ?: default
            else -> default
        }
}
