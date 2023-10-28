package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.WorldEvent
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.value.BoolValue
import net.minecraft.entity.boss.IBossDisplayData
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.potion.Potion

object GameDetector: Module("GameDetector", ModuleCategory.MISC, gameDetecting = false) {
    // Check if player's gamemode is Survival or Adventure
    private val gameMode by BoolValue("GameModeCheck", true)

    // Check if player doesn't have unnatural capabilities
    private val capabilities by BoolValue("CapabilitiesCheck", true)

    // Check if there are > 1 players in tablist
    private val tabList by BoolValue("TabListCheck", true)

    // Check if there are > 1 teams or if friendly fire is enabled
    private val teams by BoolValue("TeamsCheck", true)

    // Check if player doesn't have infinite invisibility effect
    private val invisibility by BoolValue("InvisibilityCheck", true)

    // Check for any hub-like BossBar or ArmorStand entities
    private val entity by BoolValue("EntityCheck", false)

    // Check for strings in scoreboard that could signify that the game is waiting for players or if you are in a lobby
    // Needed on Gamster
    private val scoreboard by BoolValue("ScoreboardCheck", false)

    private val WHITELISTED_SUBSTRINGS = arrayOf(":", "Vazio!", "§6§lRumble Box", "§5§lDivine Drop")

    private var isPlaying = false

    private val LOBBY_SUBSTRINGS = arrayOf("lobby", "hub", "waiting", "loading", "starting")

    fun isInGame() = !state || isPlaying

    @EventTarget(priority = 1)
    fun onUpdate(updateEvent: UpdateEvent) {
        isPlaying = false

        val thePlayer = mc.thePlayer ?: return
        val theWorld = mc.theWorld ?: return
        val netHandler = mc.netHandler ?: return
        val capabilities = thePlayer.capabilities

        if (gameMode && !mc.playerController.gameIsSurvivalOrAdventure())
            return

        if (this.capabilities &&
            (!capabilities.allowEdit || capabilities.allowFlying || capabilities.isFlying || capabilities.disableDamage))
            return

        if (tabList && netHandler.playerInfoMap.size <= 1)
            return

        if (teams && thePlayer.team?.allowFriendlyFire == false && theWorld.scoreboard.teams.size == 1)
            return

        if (invisibility && thePlayer.getActivePotionEffect(Potion.invisibility)?.isPotionDurationMax == true)
            return

        if (scoreboard) {
            if (LOBBY_SUBSTRINGS in theWorld.scoreboard.getObjectiveInDisplaySlot(1)?.displayName)
                return

            if (theWorld.scoreboard.objectiveNames.any { LOBBY_SUBSTRINGS in it })
                return

            if (theWorld.scoreboard.teams.any { LOBBY_SUBSTRINGS in it.colorPrefix })
                return
        }

        if (entity) {
            for (entity in theWorld.loadedEntityList) {
                if (entity !is IBossDisplayData && entity !is EntityArmorStand)
                    continue

                val name = entity.customNameTag ?: continue

                // If an unnatural entity has been found, break the loop if its name includes a whitelisted substring
                if (WHITELISTED_SUBSTRINGS in name) break
                else return
            }
        }

        isPlaying = true
    }

    @EventTarget
    fun onWorld(event: WorldEvent) {
        isPlaying = false
    }

    override fun handleEvents() = true

    /**
     * Checks if a nullable String converted to lowercase contains any of the given lowercase substrings.
     * It returns true if at least one substring is found, false otherwise.
     * @param substrings an array of Strings to look for in the nullable String
     * @return true if any substring is found, false otherwise
     */
    private operator fun String?.contains(substrings: Array<String>): Boolean {
        val lowerCaseString = this?.lowercase() ?: return false
        return substrings.any { it in lowerCaseString }
    }
}