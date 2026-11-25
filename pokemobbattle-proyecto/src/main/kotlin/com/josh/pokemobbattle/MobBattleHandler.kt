package com.josh.pokemobbattle

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.PokemonBattle
import com.cobblemon.mod.common.battles.SuccessfulBattleStart
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Mob
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.registries.ForgeRegistries
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object MobBattleHandler {

    private class Session(
        val battle: PokemonBattle,
        val player: ServerPlayer,
        val mob: Mob,
        val proxy: PokemonEntity
    ) {
        var ticks = 0
        var endedTicks = 0
    }

    private val sessions = ConcurrentHashMap<UUID, Session>()
    private val busyMobs = ConcurrentHashMap.newKeySet<UUID>()

    fun registerCobblemonEvents() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event: BattleVictoryEvent ->
            onVictory(event)
        }
    }

    @SubscribeEvent
    fun onInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide) return
        if (event.hand != InteractionHand.MAIN_HAND) return
        val player = event.entity as? ServerPlayer ?: return
        val target = event.target as? Mob ?: return
        val cfg = MobBattleConfig.current

        if (cfg.requireSneak && !player.isShiftKeyDown) return
        val heldId = ForgeRegistries.ITEMS.getKey(player.mainHandItem.item)?.toString()
        if (heldId != cfg.triggerItem) return

        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS

        startBattle(player, target)
    }

    private fun msg(player: ServerPlayer, text: String) {
        player.sendSystemMessage(Component.literal(text))
    }

    private fun startBattle(player: ServerPlayer, mob: Mob) {
        val cfg = MobBattleConfig.current
        val mobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.type)?.toString() ?: return

        if (mob is PokemonEntity) {
            msg(player, "Eso ya es un Pokémon, usa el combate normal.")
            return
        }
        if (mobId in cfg.blacklist) {
            msg(player, "Este mob no se puede combatir.")
            return
        }
        if (!mob.isAlive || busyMobs.contains(mob.uuid)) return

        val (species, level) = cfg.resolve(mobId, mob.maxHealth)
        val world = player.serverLevel()

        val proxy: PokemonEntity
        try {
            val props = PokemonProperties.parse("$species level=$level uncatchable=true")
            proxy = props.createEntity(world)
            proxy.setPos(mob.x, mob.y, mob.z)
            proxy.isInvisible = true
            proxy.isSilent = true
            proxy.isInvulnerable = true
            proxy.pokemon.nickname = mob.displayName.copy()
            world.addFreshEntity(proxy)
        } catch (e: Exception) {
            PokeMobBattle.LOGGER.error("No se pudo crear el Pokémon doble ($species nivel $level)", e)
            msg(player, "Error al crear el combate. Revisa que la especie '$species' exista en la config.")
            return
        }

        mob.isNoAi = true
        busyMobs.add(mob.uuid)

        val result = try {
            BattleBuilder.pve(player, proxy)
        } catch (e: Exception) {
            PokeMobBattle.LOGGER.error("Fallo al iniciar la batalla", e)
            null
        }

        if (result is SuccessfulBattleStart) {
            sessions[result.battle.battleId] = Session(result.battle, player, mob, proxy)
            msg(player, "¡Batalla contra ${mob.displayName.string} (Nv. $level)!")
        } else {
            busyMobs.remove(mob.uuid)
            mob.isNoAi = false
            proxy.discard()
            msg(player, "No se pudo iniciar la batalla. ¿Tienes un Pokémon sano en tu equipo?")
        }
    }

    private fun onVictory(event: BattleVictoryEvent) {
        val session = sessions.remove(event.battle.battleId) ?: return
        val playerWon = event.winners.any { it is PlayerBattleActor && it.uuid == session.player.uuid }
        finish(session, playerWon)
    }

    private fun finish(s: Session, playerWon: Boolean) {
        busyMobs.remove(s.mob.uuid)
        if (s.mob.isAlive) s.mob.isNoAi = false
        s.proxy.discard()

        if (!playerWon || !s.mob.isAlive) {
            if (!playerWon) msg(s.player, "Has perdido o huido: el mob sigue vivo.")
            return
        }

        val source = s.mob.level().damageSources().playerAttack(s.player)
        s.mob.hurt(source, Float.MAX_VALUE)
        if (s.mob.isAlive) {
            s.mob.health = 0f
            s.mob.die(source)
        }
        if (s.mob.isAlive) s.mob.kill()
        msg(s.player, "¡Has ganado la batalla!")
    }

    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END || sessions.isEmpty()) return
        val timeout = MobBattleConfig.current.battleTimeoutMinutes * 60 * 20
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val s = iterator.next().value
            s.ticks++
            if (s.battle.ended) s.endedTicks++

            // Si la batalla termino sin evento de victoria (huida, desconexion), liberar al mob
            val endedWithoutVictory = s.endedTicks > 40
            if (endedWithoutVictory || s.ticks > timeout || s.player.isRemoved) {
                iterator.remove()
                finish(s, false)
            }
        }
    }
}
