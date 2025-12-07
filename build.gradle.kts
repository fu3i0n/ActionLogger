import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.attributes.Bundling
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val kotlinVersion = "2.2.21"
val targetJavaVersion = 21

plugins {
    java
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "9.2.2"
}

group = "cat.daisy"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

val versions =
    mapOf(
        "paperApi" to "1.21.8-R0.1-SNAPSHOT",
        "placeholderApi" to "2.11.7",
        "kotlin" to kotlinVersion,
        "kotlinCoroutines" to "1.10.2",
        "mcChestUi" to "1.6.0",
        "hikariCP" to "7.0.2",
        "sqlite" to "3.51.0.0",
        "exposed" to "0.61.0",
        "ktlint" to "1.8.0",
        "json" to "1.1.1",
        "commandapi" to "10.1.2",
    )

val ktlint by configurations.creating

dependencies {
    // CommandAPI (shaded into JAR)
    implementation("dev.jorel:commandapi-bukkit-shade:${versions["commandapi"]}")
    implementation("dev.jorel:commandapi-bukkit-kotlin:${versions["commandapi"]}")

    // GUI Libraries (shaded into JAR)
    implementation("com.github.DebitCardz:mc-chestui-plus:${versions["mcChestUi"]}")

    // Database (shaded into JAR)
    implementation("org.jetbrains.exposed:exposed-core:${versions["exposed"]}")
    implementation("org.jetbrains.exposed:exposed-dao:${versions["exposed"]}")
    implementation("org.jetbrains.exposed:exposed-jdbc:${versions["exposed"]}")
    implementation("org.jetbrains.exposed:exposed-java-time:${versions["exposed"]}")
    implementation("com.googlecode.json-simple:json-simple:${versions["json"]}")

    // Paper API & Plugins (provided by server)
    compileOnly("io.papermc.paper:paper-api:${versions["paperApi"]}")
    compileOnly("me.clip:placeholderapi:${versions["placeholderApi"]}")

    // Kotlin & Coroutines (provided by server via plugin.yml)
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${versions["kotlin"]}")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:${versions["kotlinCoroutines"]}")

    // Data Source (provided by server via plugin.yml)
    compileOnly("com.zaxxer:HikariCP:${versions["hikariCP"]}")
    compileOnly("org.xerial:sqlite-jdbc:${versions["sqlite"]}") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    // Code Quality
    ktlint("com.pinterest.ktlint:ktlint-cli:${versions["ktlint"]}") {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
}

kotlin {
    jvmToolchain(targetJavaVersion)
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.set(listOf("-opt-in=kotlin.RequiresOptIn"))
            jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
        }
    }
}

// Disable default Java sources, we are fully Kotlin now
sourceSets {
    named("main") {
        java.setSrcDirs(emptyList<File>())
    }
}

tasks {
    val ktlintCheck by registering(JavaExec::class) {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Check Kotlin code style"
        classpath = ktlint
        mainClass.set("com.pinterest.ktlint.Main")
        args("**/src/**/*.kt", "**.kts", "!**/build/**")
    }

    register<JavaExec>("ktlintFormat") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Check Kotlin code style and format"
        classpath = ktlint
        mainClass.set("com.pinterest.ktlint.Main")
        jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
        args("-F", "**/src/**/*.kt", "**.kts", "!**/build/**")
    }

    check {
        dependsOn(ktlintCheck)
    }

    build {
        dependsOn("shadowJar")
        finalizedBy("printJarSize")
    }

    processResources {
        val versionValue = project.version.toString()
        inputs.property("version", versionValue)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(mapOf("version" to versionValue))
        }
    }

    withType<ShadowJar> {
        archiveClassifier.set("shaded")

        // Relocate shaded dependencies to avoid conflicts
        relocate("dev.jorel.commandapi", "cat.daisy.libs.commandapi")
        relocate("com.github.DebitCardz.mcchestui", "cat.daisy.libs.chestui")
        relocate("org.json.simple", "cat.daisy.libs.jsonsimple")
        relocate("org.jetbrains.exposed", "cat.daisy.libs.exposed")

        minimize {
            exclude(dependency("org.jetbrains.exposed:.*:.*"))
            exclude(dependency("org.jetbrains.kotlin:.*:.*"))
            exclude(dependency("org.jetbrains.kotlinx:.*:.*"))
        }

        exclude(
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/DEPENDENCIES",
            "META-INF/maven/**",
            "META-INF/versions/**",
            "META-INF/services/javax.*",
            "**/*.html",
            "**/*.txt",
            "**/*.properties",
            "**/*.kotlin_module",
            "**/*.kotlin_metadata",
            "**/*.kotlin_builtins",
        )

        mergeServiceFiles()

        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "Daisy",
                "Built-By" to System.getProperty("user.name"),
                "Build-Timestamp" to System.currentTimeMillis(),
            )
        }
    }

    register("printJarSize") {
        group = "build"
        description = "Print the size of the built JAR file"
        dependsOn("shadowJar")
        doLast {
            val libsDir = file("$buildDir/libs")
            val jarFiles = libsDir.listFiles { file -> file.name.endsWith("-shaded.jar") }

            if (jarFiles != null && jarFiles.isNotEmpty()) {
                val jarFile = jarFiles.first()
                val sizeInMB = jarFile.length() / (1024.0 * 1024.0)
                val sizeInKB = jarFile.length() / 1024.0
                println("\n" + "=".repeat(50))
                println("✓ Build successful!")
                println("  Final JAR size: ${String.format("%.2f", sizeInMB)} MB (${String.format("%.0f", sizeInKB)} KB)")
                println("  Location: ${jarFile.absolutePath}")
                println("=".repeat(50) + "\n")
            } else {
                println("\n⚠ Warning: No shaded JAR files found in ${libsDir.absolutePath}\n")
            }
        }
    }
}
