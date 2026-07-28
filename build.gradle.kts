plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    // Modrinth's own publishing plugin. Used by the release pipeline; running
    // `./gradlew modrinth` locally without MODRINTH_TOKEN simply fails, it
    // cannot publish by accident.
    id("com.modrinth.minotaur") version "2.+"
}

group = "dev.spruceworks"
version = "1.0.1"
description = "SpruceBounty — free bounty plugin for Donut-like / Lifesteal SMPs"

// Single source of truth for the runtime-downloaded driver. processResources
// substitutes it into plugin.yml's `libraries:` block, so the compile
// classpath and what the server actually fetches can never drift apart.
val sqliteJdbcVersion = "3.49.1.0"

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

    // bStats stays shaded: 53 KB, and metrics must not depend on the server
    // reaching a Maven repo at startup.
    implementation("org.bstats:bstats-bukkit:3.1.0")

    // sqlite-jdbc is NOT shaded. Its native binaries for every platform are
    // ~24 MB of the jar and pushed us over SpigotMC's upload limit. It is
    // declared in plugin.yml's `libraries:` block instead, so Paper's library
    // loader fetches it from Maven Central on first start.
    compileOnly("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")
    testImplementation("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")

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
        val props = mapOf(
            "version" to project.version.toString(),
            "sqliteJdbcVersion" to sqliteJdbcVersion,
        )
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
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        mergeServiceFiles()
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        // Only bStats is bundled now, relocated so it cannot clash with another
        // plugin that bundles its own copy. sqlite-jdbc is fetched at runtime by
        // Paper's library loader and is deliberately absent from this jar.
        relocate("org.bstats", "dev.spruceworks.bounty.libs.bstats")

        doLast {
            val jar = archiveFile.get().asFile
            val mb = jar.length() / 1024.0 / 1024.0
            logger.lifecycle("shadowJar: ${jar.name} = %.2f MB".format(mb))
            // Guard against a dependency silently becoming bundled again and
            // pushing us back over the marketplace upload limit.
            zipTree(jar).matching { include("**/sqlite/**", "**/org/sqlite/**") }
                .files.firstOrNull()?.let {
                    throw GradleException(
                        "sqlite-jdbc was shaded into the jar again (found ${it.name}). " +
                        "It must stay in plugin.yml's libraries: block — see the " +
                        "comment on the dependency declaration."
                    )
                }
        }
    }

    assemble {
        dependsOn(shadowJar)
    }

    runServer {
        // Downloads this Paper version and boots a local test server with the plugin installed.
        minecraftVersion("26.2")
    }
}

modrinth {
    // Project ID, not the slug: a slug can be renamed and would break the
    // pipeline silently, at the worst possible moment.
    projectId.set("KJ7ESaxJ")
    token.set(providers.environmentVariable("MODRINTH_TOKEN"))

    versionNumber.set(project.version.toString())
    versionName.set("SpruceBounty ${project.version}")
    versionType.set("release")

    // Publish the exact artifact the release pipeline already gated. shadowJar
    // is up to date by then, so this uploads that file rather than rebuilding.
    uploadFile.set(tasks.shadowJar)

    // Only what we have actually run. 26.1 was never tested, so it is not listed.
    gameVersions.addAll("26.2")
    // Pure Paper API, api-version 26.2 — we do not claim Spigot or Bukkit.
    loaders.addAll("paper")

    // Written by the pipeline's gate step from CHANGELOG.md. Empty locally,
    // which is fine: a local run is a rehearsal, not a release.
    changelog.set(
        providers.fileContents(layout.projectDirectory.file("release-notes.md"))
            .asText.orElse("")
    )

    // Deliberately NOT syncing the project description from README.md —
    // syncBodyFrom would overwrite the hand-written marketplace listing.
}
