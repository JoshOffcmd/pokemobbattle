package com.josh.pokemobbattle

import com.google.gson.GsonBuilder
import net.minecraftforge.fml.loading.FMLPaths
import java.nio.file.Files

class Tier(var maxHealth: Float = 0f, var species: String = "rattata")

class MobOverride(var species: String = "rattata", var level: Int = 50)

class MobBattleConfig {
    // Objeto con el que haces clic derecho (agachado) sobre el mob para empezar la batalla
    var triggerItem: String = "minecraft:stick"
    var requireSneak: Boolean = true

    // Nivel del Pokemon doble = vida maxima del mob * levelPerHealth, limitado entre minLevel y maxLevel
    var levelPerHealth: Double = 1.0
    var minLevel: Int = 5
    var maxLevel: Int = 100

    // Si una batalla dura mas que esto (minutos), se cancela y el mob se libera
    var battleTimeoutMinutes: Int = 15

    // Mobs que no se pueden combatir. Ejemplo: "minecraft:iron_golem"
    var blacklist: MutableList<String> = mutableListOf()

    // Especie del Pokemon doble segun la vida maxima del mob (se usa la primera fila que cumpla)
    var tiers: MutableList<Tier> = mutableListOf(
        Tier(20f, "rattata"),
        Tier(60f, "machoke"),
        Tier(200f, "dragonite"),
        Tier(1.0E9f, "mewtwo")
    )

    // Valores concretos para mobs concretos (tienen prioridad sobre tiers)
    var overrides: MutableMap<String, MobOverride> = mutableMapOf(
        "minecraft:wither" to MobOverride("mewtwo", 100),
        "minecraft:ender_dragon" to MobOverride("rayquaza", 100),
        "minecraft:warden" to MobOverride("tyranitar", 90)
    )

    fun resolve(entityId: String, maxHealth: Float): Pair<String, Int> {
        val o = overrides[entityId]
        if (o != null) return Pair(o.species, o.level.coerceIn(1, 100))
        val species = (tiers.firstOrNull { maxHealth <= it.maxHealth } ?: tiers.lastOrNull())?.species ?: "rattata"
        val level = (maxHealth * levelPerHealth).toInt().coerceIn(minLevel, maxLevel).coerceIn(1, 100)
        return Pair(species, level)
    }

    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()
        private val path = FMLPaths.CONFIGDIR.get().resolve("pokemobbattle.json")

        @Volatile
        var current: MobBattleConfig = MobBattleConfig()
            private set

        fun load() {
            try {
                if (Files.exists(path)) {
                    val loaded = gson.fromJson(Files.readString(path), MobBattleConfig::class.java)
                    if (loaded != null) current = loaded
                }
                Files.writeString(path, gson.toJson(current))
            } catch (e: Exception) {
                PokeMobBattle.LOGGER.error("No se pudo leer la config, usando valores por defecto", e)
            }
        }
    }
}
