package com.josh.pokemobbattle

import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import org.apache.logging.log4j.LogManager

@Mod(PokeMobBattle.MOD_ID)
object PokeMobBattle {
    const val MOD_ID = "pokemobbattle"
    val LOGGER = LogManager.getLogger(MOD_ID)

    init {
        MobBattleConfig.load()
        MinecraftForge.EVENT_BUS.register(MobBattleHandler)
        MobBattleHandler.registerCobblemonEvents()
        LOGGER.info("PokeMob Battle cargado")
    }
}
