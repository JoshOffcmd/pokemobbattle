plugins {
    id("java")
    id("dev.architectury.loom") version("1.2-SNAPSHOT")
    id("architectury-plugin") version("3.4-SNAPSHOT")
    id("org.jetbrains.kotlin.jvm") version("1.8.21")
}

group = property("maven_group")!!
version = property("mod_version")!!

base {
    archivesName.set("pokemobbattle")
}

architectury {
    platformSetupLoomIde()
    forge()
}

loom {
    silentMojangMappingsLicense()

    forge {
    }
}

repositories {
    mavenCentral()
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
    maven("https://maven.impactdev.net/repository/development/")
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    maven("https://api.modrinth.com/maven")
}

dependencies {
    minecraft("net.minecraft:minecraft:1.20.1")
    mappings(loom.officialMojangMappings())

    forge("net.minecraftforge:forge:1.20.1-47.2.0")

    modImplementation("com.cobblemon:forge:${property("cobblemon_version")}")
    implementation("thedarkcolour:kotlinforforge:4.5.0")
}

tasks.withType<JavaCompile> {
    options.release.set(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/mods.toml") {
        expand(mapOf("version" to project.version))
    }
}
