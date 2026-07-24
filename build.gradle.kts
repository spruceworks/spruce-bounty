plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "dev.spruceworks"
version = "1.0.0"
description = "SpruceBounty — free bounty plugin for Donut-like / Lifesteal SMPs"

java {
    // Paper 26.x requires Java 25 (https://docs.papermc.io/paper/dev/project-setup/).
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    // Classic VaultAPI, not VaultUnlocked: VaultUnlocked-backed servers still expose
    // the classic net.milkbowl.vault Economy service for backward compatibility, so
    // building against it maximizes which servers SpruceBounty works on.
    maven("https://jitpack.io") { name = "jitpack" }
    maven("https://repo.extendedclip.com/releases/") { name = "placeholderapi" }
}

dependencies {
    // Version format per https://docs.papermc.io/paper/dev/project-setup/ — resolves the latest 26.2 build.
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        // VaultAPI's POM pulls in an ancient org.bukkit:bukkit that conflicts with
        // paper-api's own Bukkit-API capability; we only use Vault's Economy
        // interface, which paper-api's newer Bukkit types satisfy fine.
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("me.clip:placeholderapi:2.12.3")

    implementation("org.bstats:bstats-bukkit:3.1.0")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
    }

    jar {
        // The unshaded jar has no bundled runtime deps and is not a usable plugin — label it clearly.
        archiveClassifier.set("unshaded")
    }

    shadowJar {
        archiveClassifier.set("")
        // Rewrites META-INF/services/* content too, not just class file locations —
        // required for sqlite-jdbc's JDBC 4 auto-registration to survive relocation.
        // Service files are explicitly allowed to merge; everything else keeps the
        // default EXCLUDE so unrelated duplicate resources don't silently bloat the jar.
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        mergeServiceFiles()
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        // Relocate bundled runtime deps so they cannot clash with other plugins that bundle them.
        relocate("org.bstats", "dev.spruceworks.bounty.libs.bstats")
        relocate("org.sqlite", "dev.spruceworks.bounty.libs.sqlite")
    }

    assemble {
        dependsOn(shadowJar)
    }

    runServer {
        // Downloads this Paper version and boots a local test server with the plugin installed.
        minecraftVersion("26.2")
    }
}
