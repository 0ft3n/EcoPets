package com.willfp.ecopets.pets

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.scheduling.UnifiedTask
import com.willfp.eco.util.NumberUtils
import com.willfp.eco.util.formatEco
import com.willfp.ecopets.ecoPetsPlugin
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.*
import kotlin.math.PI
import kotlin.math.abs

class PetDisplay(
    private val plugin: EcoPlugin
) : Listener {
    private val trackedEntities = mutableMapOf<UUID, PetArmorStand>()
    private val playerTickers = mutableMapOf<UUID, UnifiedTask>()
    private val playerTicks = mutableMapOf<UUID, Int>()

    fun update() {
        playerTickers.values.forEach { it.cancel() }
        playerTickers.clear()
        playerTickers.putAll(Bukkit.getOnlinePlayers().associate { it.uniqueId to ecoPetsPlugin.scheduler
            .runTimer(it, 1, 1) { tickPlayer(it) } })
    }

    private fun addPlayer(player: Player) {
        playerTickers.remove(player.uniqueId)?.cancel()
        playerTickers[player.uniqueId] = ecoPetsPlugin.scheduler.runTimer(player, 1, 1) { tickPlayer(player) }
    }

    private fun removePlayer(player: Player) {
        playerTickers.remove(player.uniqueId)?.cancel()
    }

    private fun tickPlayer(player: Player) {
        val tick = playerTicks[player.uniqueId] ?: 0
        val stand = getOrNew(player) ?: return
        val pet = player.activePet

        if (pet != null) {
            if (player.isInvisible) {
                remove(player)
                return
            }

            @Suppress("DEPRECATION")
            stand.customName = plugin.configYml.getString("pet-entity.name")
                .replace("%player%", player.displayName)
                .replace("%pet%", pet.name)
                .replace("%level%", player.getPetLevel(pet).toString())
                .formatEco(player)

            val location = getLocation(player)

            location.y += NumberUtils.fastSin(tick / (2 * PI) * 0.5) * 0.15

            if (location.world != null) {
                stand.teleportAsync(location)
            }

            if (!pet.entityTexture.contains(":")) {
                stand.setRotation((20 * tick / (2 * PI)).toFloat(), 0f)
            }
        }

        playerTicks[player.uniqueId] = tick + 1
    }

    private fun getLocation(player: Player): Location {
        val offset = player.eyeLocation.direction.clone().normalize()
            .multiply(-1)
            .apply {
                y = abs(y)

                if (abs(x) < 0.5) {
                    x = 0.5
                }

                if (abs(z) < 0.5) {
                    z = 0.5
                }
            }
            .rotateAroundY(PI / 6)

        return player.eyeLocation.clone().add(offset)
    }

    private fun getOrNew(player: Player): ArmorStand? {
        if (player.isInvisible) {
            return null
        }

        val tracked = trackedEntities[player.uniqueId]
        val existing = tracked?.stand

        val pet = player.activePet
        if (pet != tracked?.pet) {
            tracked?.stand?.let {
                plugin.scheduler.run(it) {
                    it.remove()
                }
            }
        }

        if (existing == null || existing.isDead || pet == null) {
            existing?.remove()
            trackedEntities.remove(player.uniqueId)

            if (pet == null) {
                return null
            }

            val location = getLocation(player)
            val stand = pet.makePetEntity().spawn(location)

            trackedEntities[player.uniqueId] = PetArmorStand(stand, pet)
        }

        return trackedEntities[player.uniqueId]?.stand
    }

    fun shutdown() {
        for (stand in trackedEntities.values) {
            stand.stand.remove()
        }

        trackedEntities.clear()
    }

    private fun remove(player: Player) {
        trackedEntities[player.uniqueId]?.stand?.let {
            plugin.scheduler.run(it) {
                it.remove()
                trackedEntities.remove(player.uniqueId)
            }
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        addPlayer(event.player)
    }

    @EventHandler
    fun onLeave(event: PlayerQuitEvent) {
        remove(event.player)
        removePlayer(event.player)
    }

    @EventHandler
    fun onTeleport(event: PlayerTeleportEvent) {
        remove(event.player)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        remove(event.player)
    }

    private data class PetArmorStand(
        val stand: ArmorStand,
        val pet: Pet
    )
}
