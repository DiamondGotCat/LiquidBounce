/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.LiquidBounce.moduleManager
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.event.EventManager.callEvent
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleInfo
import net.ccbluex.liquidbounce.features.module.modules.misc.AntiBot.isBot
import net.ccbluex.liquidbounce.features.module.modules.misc.Teams
import net.ccbluex.liquidbounce.features.module.modules.player.Blink
import net.ccbluex.liquidbounce.features.module.modules.render.FreeCam
import net.ccbluex.liquidbounce.utils.CooldownHelper.getAttackCooldownProgress
import net.ccbluex.liquidbounce.utils.CooldownHelper.resetLastAttackedTicks
import net.ccbluex.liquidbounce.utils.EntityUtils.targetAnimals
import net.ccbluex.liquidbounce.utils.EntityUtils.targetDead
import net.ccbluex.liquidbounce.utils.EntityUtils.targetInvisible
import net.ccbluex.liquidbounce.utils.EntityUtils.targetMobs
import net.ccbluex.liquidbounce.utils.EntityUtils.targetPlayer
import net.ccbluex.liquidbounce.utils.RaycastUtils.raycastEntity
import net.ccbluex.liquidbounce.utils.Rotation
import net.ccbluex.liquidbounce.utils.RotationUtils.getRotationDifference
import net.ccbluex.liquidbounce.utils.RotationUtils.isFaced
import net.ccbluex.liquidbounce.utils.RotationUtils.isRotationFaced
import net.ccbluex.liquidbounce.utils.RotationUtils.limitAngleChange
import net.ccbluex.liquidbounce.utils.RotationUtils.searchCenter
import net.ccbluex.liquidbounce.utils.RotationUtils.serverRotation
import net.ccbluex.liquidbounce.utils.RotationUtils.setTargetRotation
import net.ccbluex.liquidbounce.utils.RotationUtils.targetRotation
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.misc.RandomUtils.nextFloat
import net.ccbluex.liquidbounce.utils.misc.RandomUtils.nextInt
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawPlatform
import net.ccbluex.liquidbounce.utils.timer.MSTimer
import net.ccbluex.liquidbounce.utils.timer.TimeUtils
import net.ccbluex.liquidbounce.value.BoolValue
import net.ccbluex.liquidbounce.value.FloatValue
import net.ccbluex.liquidbounce.value.IntegerValue
import net.ccbluex.liquidbounce.value.ListValue
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.*
import net.minecraft.potion.Potion
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.Vec3
import net.minecraft.world.WorldSettings
import org.lwjgl.input.Keyboard
import java.awt.Color
import kotlin.math.*

@ModuleInfo(name = "KillAura", description = "Automatically attacks targets around you.",
        category = ModuleCategory.COMBAT, keyBind = Keyboard.KEY_R)
object KillAura : Module() {

    /**
     * OPTIONS
     */

    // CPS - Attack speed
    private val maxCPS: IntegerValue = object : IntegerValue("MaxCPS", 8, 1, 20) {
        override fun onChanged(oldValue: Int, newValue: Int) {
            val i = minCPS.get()
            if (i > newValue) set(i)

            attackDelay = TimeUtils.randomClickDelay(minCPS.get(), get())
        }
    }

    private val minCPS: IntegerValue = object : IntegerValue("MinCPS", 5, 1, 20) {
        override fun onChanged(oldValue: Int, newValue: Int) {
            val i = maxCPS.get()
            if (i < newValue) set(i)

            attackDelay = TimeUtils.randomClickDelay(get(), maxCPS.get())
        }

        override fun isSupported() = !maxCPS.isMinimal()
    }

    private val hurtTimeValue = IntegerValue("HurtTime", 10, 0, 10)
    private val simulateCooldown = BoolValue("SimulateCooldown", false)

    // Range
    private val rangeValue = FloatValue("Range", 3.7f, 1f, 8f)
    private val throughWallsRangeValue = FloatValue("ThroughWallsRange", 3f, 0f, 8f)
    private val rangeSprintReductionValue = FloatValue("RangeSprintReduction", 0f, 0f, 0.4f)

    // Modes
    private val priorityValue = ListValue("Priority", arrayOf("Health", "Distance", "Direction", "LivingTime"), "Distance")
    private val targetModeValue = ListValue("TargetMode", arrayOf("Single", "Switch", "Multi"), "Switch")
    private val limitedMultiTargetsValue = object : IntegerValue("LimitedMultiTargets", 0, 0, 50) {
        override fun isSupported() = targetModeValue.get() == "Multi"
    }

    // Bypass
    private val swingValue = BoolValue("Swing", true)
    private val keepSprintValue = BoolValue("KeepSprint", true)

    // AutoBlock
    private val autoBlockValue = ListValue("AutoBlock", arrayOf("Off", "Packet", "AfterTick", "Fake"), "Packet")
    private val interactAutoBlockValue = object : BoolValue("InteractAutoBlock", true) {
        override fun isSupported() = autoBlockValue.get() !in setOf("Off", "Fake")
    }
    private val blockRate = object : IntegerValue("BlockRate", 100, 1, 100) {
        override fun isSupported() = autoBlockValue.get() != "Off"
    }

    // Turn Speed
    private val maxTurnSpeed: FloatValue = object : FloatValue("MaxTurnSpeed", 180f, 0f, 180f) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val v = minTurnSpeed.get()
            if (v > newValue) set(v)
        }
    }
    private val minTurnSpeed: FloatValue = object : FloatValue("MinTurnSpeed", 180f, 0f, 180f) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val v = maxTurnSpeed.get()
            if (v < newValue) set(v)
        }

        override fun isSupported() = !maxTurnSpeed.isMinimal()
    }

    // Raycast
    private val raycastValue = object : BoolValue("RayCast", true) {
        override fun isSupported() = !maxTurnSpeed.isMinimal()
    }
    private val raycastIgnoredValue = object : BoolValue("RayCastIgnored", false) {
        override fun isSupported() = raycastValue.isActive()
    }
    private val livingRaycastValue = object : BoolValue("LivingRayCast", true) {
        override fun isSupported() = raycastValue.isActive()
    }

    // Bypass
    private val aacValue = object : BoolValue("AAC", false) {
        override fun isSupported() = !maxTurnSpeed.isMinimal()
        // AAC value also modifies target selection a bit, not just rotations, but it is minor
    }

    private val micronizedValue = object : BoolValue("Micronized", true) {
        override fun isSupported() = !maxTurnSpeed.isMinimal()
    }
    private val micronizedStrength = object : FloatValue("MicronizedStrength", 0.8f, 0.2f, 2f) {
        override fun isSupported() = micronizedValue.isActive()
    }

    // Rotations
    private val silentRotationValue = object : BoolValue("SilentRotation", true) {
        override fun isSupported() = !maxTurnSpeed.isMinimal()
    }
    private val rotationStrafeValue = object : ListValue("Strafe", arrayOf("Off", "Strict", "Silent"), "Off") {
        override fun isSupported() = silentRotationValue.isActive()
    }
    private val randomCenterValue = object : BoolValue("RandomCenter", true) {
        override fun isSupported() = !maxTurnSpeed.isMinimal()
    }
    private val outborderValue = object : BoolValue("Outborder", false) {
        override fun isSupported() = !maxTurnSpeed.isMinimal()
    }
    private val fovValue = FloatValue("FOV", 180f, 0f, 180f)

    // Predict
    private val predictValue = object : BoolValue("Predict", true) {
        override fun isSupported() = !maxTurnSpeed.isMinimal()
    }
    private val maxPredictSize: FloatValue = object : FloatValue("MaxPredictSize", 1f, 0.1f, 5f) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val v = minPredictSize.get()
            if (v > newValue) set(v)
        }

        override fun isSupported() = predictValue.isActive()
    }
    private val minPredictSize: FloatValue = object : FloatValue("MinPredictSize", 1f, 0.1f, 5f) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val v = maxPredictSize.get()
            if (v < newValue) set(v)
        }

        override fun isSupported() = predictValue.isActive() && !maxPredictSize.isMinimal()
    }

    // Bypass
    private val failRateValue = FloatValue("FailRate", 0f, 0f, 99f)
    private val fakeSwingValue = object : BoolValue("FakeSwing", true) {
        override fun isSupported() = swingValue.get()
    }
    private val noInventoryAttackValue = BoolValue("NoInvAttack", false)
    private val noInventoryDelayValue = object : IntegerValue("NoInvDelay", 200, 0, 500) {
        override fun isSupported() = noInventoryAttackValue.get()
    }

    // Visuals
    private val markValue = BoolValue("Mark", true)
    private val fakeSharpValue = BoolValue("FakeSharp", true)

    /**
     * MODULE
     */

    // Target
    var target: EntityLivingBase? = null
    private var currentTarget: EntityLivingBase? = null
    private var hitable = false
    private val prevTargetEntities = mutableListOf<Int>()

    // Attack delay
    private val attackTimer = MSTimer()
    private var attackDelay = 0L
    private var clicks = 0

    // Container Delay
    private var containerOpen = -1L

    // Block status
    var renderBlocking = false
    var blockStatus = false
    private var blockStopInDead = false

    /**
     * Enable kill aura module
     */
    override fun onEnable() {
        mc.thePlayer ?: return
        mc.theWorld ?: return

        updateTarget()
    }

    /**
     * Disable kill aura module
     */
    override fun onDisable() {
        target = null
        currentTarget = null
        hitable = false
        prevTargetEntities.clear()
        attackTimer.reset()
        clicks = 0

        stopBlocking()
    }

    /**
     * Motion event
     */
    @EventTarget
    fun onMotion(event: MotionEvent) {
        if (event.eventState == EventState.POST) {
            target ?: return
            currentTarget ?: return

            // Update hitable
            updateHitable()

            // AutoBlock
            if (canBlock) {
                when (autoBlockValue.get()) {
                    "AfterTick" -> startBlocking(currentTarget!!, hitable)
                    "Fake" -> startBlocking(currentTarget!!, hitable, fake = true)
                }

            }

            return
        }

        if (!silentRotationValue.get() || rotationStrafeValue.get() == "Off")
            update()
    }

    /**
     * Strafe event
     */
    @EventTarget
    fun onStrafe(event: StrafeEvent) {
        if (!silentRotationValue.get() || rotationStrafeValue.get() == "Off")
            return

        update()

        if (currentTarget != null && targetRotation != null) {
            when (rotationStrafeValue.get().lowercase()) {
                "strict" -> {
                    val (yaw) = targetRotation ?: return
                    var strafe = event.strafe
                    var forward = event.forward
                    val friction = event.friction

                    var f = strafe * strafe + forward * forward

                    if (f >= 1.0E-4F) {
                        f = sqrt(f)

                        if (f < 1f)
                            f = 1f

                        f = friction / f
                        strafe *= f
                        forward *= f

                        val yawSin = sin(yaw * Math.PI / 180f)
                        val yawCos = cos(yaw * Math.PI / 180f)

                        val player = mc.thePlayer

                        player.motionX += strafe * yawCos - forward * yawSin
                        player.motionZ += forward * yawCos + strafe * yawSin
                    }
                    event.cancelEvent()
                }
                "silent" -> {
                    update()

                    targetRotation?.applyStrafeToPlayer(event)
                    event.cancelEvent()
                }
            }
        }
    }

    fun update() {
        if (cancelRun || (noInventoryAttackValue.get() && (mc.currentScreen is GuiContainer ||
                        System.currentTimeMillis() - containerOpen < noInventoryDelayValue.get())))
            return

        // Update target
        updateTarget()

        if (target == null && !blockStopInDead) {
            blockStopInDead = true
            stopBlocking()
            return
        }

        // Target
        currentTarget = target

        /*
        TODO: Remove? -> currentTarget = target = currentTarget

        if (targetModeValue.get() != "Switch" && isEnemy(currentTarget))
            target = currentTarget
         */
    }

    /**
     * Update event
     */
    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        if (cancelRun) {
            target = null
            currentTarget = null
            hitable = false
            stopBlocking()
            return
        }

        if (noInventoryAttackValue.get() && (mc.currentScreen is GuiContainer ||
                        System.currentTimeMillis() - containerOpen < noInventoryDelayValue.get())) {
            target = null
            currentTarget = null
            hitable = false
            if (mc.currentScreen is GuiContainer) containerOpen = System.currentTimeMillis()
            return
        }

        if (simulateCooldown.get() && getAttackCooldownProgress() < 1f) {
            return
        }

        if (target != null && currentTarget != null) {
            while (clicks > 0) {
                runAttack()
                clicks--
            }
        }
    }

    /**
     * Render event
     */
    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        if (cancelRun) {
            target = null
            currentTarget = null
            hitable = false
            stopBlocking()
            return
        }

        if (noInventoryAttackValue.get() && (mc.currentScreen is GuiContainer ||
                        System.currentTimeMillis() - containerOpen < noInventoryDelayValue.get())) {
            target = null
            currentTarget = null
            hitable = false
            if (mc.currentScreen is GuiContainer) containerOpen = System.currentTimeMillis()
            return
        }

        target ?: return

        if (markValue.get() && targetModeValue.get() != "Multi")
            drawPlatform(target!!, if (hitable) Color(37, 126, 255, 70) else Color(255, 0, 0, 70))

        if (currentTarget != null && attackTimer.hasTimePassed(attackDelay) &&
                currentTarget!!.hurtTime <= hurtTimeValue.get()) {
            clicks++
            attackTimer.reset()
            attackDelay = TimeUtils.randomClickDelay(minCPS.get(), maxCPS.get())
        }
    }

    /**
     * Handle entity move
     */
    @EventTarget
    fun onEntityMove(event: EntityMovementEvent) {
        val movedEntity = event.movedEntity

        if (target == null || movedEntity != currentTarget)
            return

        updateHitable()
    }

    /**
     * Attack enemy
     */
    private fun runAttack() {
        target ?: return
        currentTarget ?: return
        val thePlayer = mc.thePlayer ?: return
        val theWorld = mc.theWorld ?: return

        // Settings
        val failRate = failRateValue.get()
        val swing = swingValue.get()
        val multi = targetModeValue.get() == "Multi"
        val openInventory = aacValue.get() && mc.currentScreen is GuiInventory
        val failHit = failRate > 0 && nextInt(endExclusive = 100) <= failRate

        // Close inventory when open
        if (openInventory)
            mc.netHandler.addToSendQueue(C0DPacketCloseWindow())

        // Check is not hitable or check failrate

        if (!hitable || failHit) {
            if (swing && (fakeSwingValue.get() || failHit))
                thePlayer.swingItem()
        } else {
            blockStopInDead = false
            // Attack
            if (!multi) {
                attackEntity(currentTarget!!)
            } else {
                var targets = 0

                for (entity in theWorld.loadedEntityList) {
                    val distance = thePlayer.getDistanceToEntityBox(entity)

                    if (entity is EntityLivingBase && isEnemy(entity) && distance <= getRange(entity)) {
                        attackEntity(entity)

                        targets += 1

                        if (limitedMultiTargetsValue.get() != 0 && limitedMultiTargetsValue.get() <= targets)
                            break
                    }
                }
            }

            prevTargetEntities.add(if (aacValue.get()) target!!.entityId else currentTarget!!.entityId)

            if (target == currentTarget)
                target = null
        }

        // Open inventory
        if (openInventory)
            mc.netHandler.addToSendQueue(C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT))
    }

    /**
     * Update current target
     */
    private fun updateTarget() {
        // Reset fixed target to null
        target = null

        // Settings
        val hurtTime = hurtTimeValue.get()
        val fov = fovValue.get()
        val switchMode = targetModeValue.get() == "Switch"

        // Find possible targets
        val targets = mutableListOf<EntityLivingBase>()

        val theWorld = mc.theWorld
        val thePlayer = mc.thePlayer

        for (entity in theWorld.loadedEntityList) {
            if (entity !is EntityLivingBase || !isEnemy(entity) || (switchMode && entity.entityId in prevTargetEntities))
                continue

            var distance = thePlayer.getDistanceToEntityBox(entity)
            if (Backtrack.state) {
                val trackedDistance = Backtrack.getNearestTrackedDistance(entity)

                if (distance > trackedDistance) {
                    distance = trackedDistance
                }
            }
            val entityFov = getRotationDifference(entity)

            if (distance <= maxRange && (fov == 180F || entityFov <= fov) && entity.hurtTime <= hurtTime) {
                targets.add(entity)
            }
        }

        // Sort targets by priority
        when (priorityValue.get().lowercase()) {
            "distance" -> targets.sortBy { thePlayer.getDistanceToEntityBox(it) } // Sort by distance
            "health" -> targets.sortBy { it.health } // Sort by health
            "direction" -> targets.sortBy { getRotationDifference(it) } // Sort by FOV
            "livingtime" -> targets.sortBy { -it.ticksExisted } // Sort by existence
        }

        // Find best target
        for (entity in targets) {
            // Update rotations to current target
            if (!updateRotations(entity)) {
                var success = false
                Backtrack.loopThroughBacktrackData(entity) {
                    if (updateRotations(entity)) {
                        success = true
                        return@loopThroughBacktrackData true
                    }

                    return@loopThroughBacktrackData false
                }

                if (!success) {
                    // when failed then try another target
                    continue
                }
            }

            // Set target to current entity
            target = entity
            return
        }

        // Cleanup last targets when no target found and try again
        if (prevTargetEntities.isNotEmpty()) {
            prevTargetEntities.clear()
            updateTarget()
        }
    }

    /**
     * Check if [entity] is selected as enemy with current target options and other modules
     */
    private fun isEnemy(entity: Entity?): Boolean {
        if (entity is EntityLivingBase && (targetDead || isAlive(entity)) && entity != mc.thePlayer) {
            if (!targetInvisible && entity.isInvisible)
                return false

            if (targetPlayer && entity is EntityPlayer) {
                if (entity.isSpectator || isBot(entity))
                    return false

                if (entity.isClientFriend() && !moduleManager[NoFriends::class.java].state)
                    return false

                val teams = moduleManager[Teams::class.java] as Teams

                return !teams.state || !teams.isInYourTeam(entity)
            }

            return targetMobs && entity.isMob() || targetAnimals && entity.isAnimal()
        }

        return false
    }

    /**
     * Attack [entity]
     */
    private fun attackEntity(entity: EntityLivingBase) {
        // Stop blocking
        val thePlayer = mc.thePlayer

        if (thePlayer.isBlocking || renderBlocking)
            stopBlocking()

        // Call attack event
        callEvent(AttackEvent(entity))

        // Attack target
        if (swingValue.get())
            thePlayer.swingItem()

        mc.netHandler.addToSendQueue(C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK))

        if (keepSprintValue.get()) {
            // Critical Effect
            if (thePlayer.fallDistance > 0F && !thePlayer.onGround && !thePlayer.isOnLadder &&
                !thePlayer.isInWater && !thePlayer.isPotionActive(Potion.blindness) && !thePlayer.isRiding)
                thePlayer.onCriticalHit(entity)

            // Enchant Effect
            if (EnchantmentHelper.getModifierForCreature(thePlayer.heldItem, entity.creatureAttribute) > 0F)
                thePlayer.onEnchantmentCritical(entity)
        } else {
            if (mc.playerController.currentGameType != WorldSettings.GameType.SPECTATOR)
                thePlayer.attackTargetEntityWithCurrentItem(entity)
        }

        // Extra critical effects
        val criticals = moduleManager[Criticals::class.java] as Criticals

        for (i in 0..2) {
            // Critical Effect
            if (thePlayer.fallDistance > 0F && !thePlayer.onGround && !thePlayer.isOnLadder && !thePlayer.isInWater && !thePlayer.isPotionActive(Potion.blindness) && thePlayer.ridingEntity == null || criticals.state && criticals.msTimer.hasTimePassed(criticals.delayValue.get()) && !thePlayer.isInWater && !thePlayer.isInLava && !thePlayer.isInWeb)
                thePlayer.onCriticalHit(target)

            // Enchant Effect
            if (EnchantmentHelper.getModifierForCreature(thePlayer.heldItem, target!!.creatureAttribute) > 0f || fakeSharpValue.get())
                thePlayer.onEnchantmentCritical(target)
        }

        //TODO: SHOULD THIS BE THIS? https://github.com/CCBlueX/LiquidBounce/blob/bb112eb53fdee22a974695a1dcaec3c6d9ec10eb/1.8.9-Forge/src/main/java/net/ccbluex/liquidbounce/features/module/modules/combat/KillAura.kt#L547
        /*

            // Start blocking after attack
        if (thePlayer.isBlocking || (autoBlockValue.get() && canBlock)) {
            if (!(blockRate.get() > 0 && Random().nextInt(100) <= blockRate.get()))
                return

            if (delayedBlockValue.get())
                return

            startBlocking(entity, interactAutoBlockValue.get())
        }
         */
        // Start blocking after attack
        if (autoBlockValue.get() == "Packet" && (thePlayer.isBlocking || canBlock))
            startBlocking(entity, interactAutoBlockValue.get())

        resetLastAttackedTicks()
    }

    /**
     * Update killaura rotations to enemy
     */
    private fun updateRotations(entity: Entity): Boolean {
        if (maxTurnSpeed.isMinimal())
            return true

        var boundingBox = entity.hitBox

        if (predictValue.get()) {
            boundingBox = boundingBox.offset(
                (entity.posX - entity.prevPosX - (mc.thePlayer.posX - mc.thePlayer.prevPosX)) * nextFloat(minPredictSize.get(), maxPredictSize.get()),
                (entity.posY - entity.prevPosY - (mc.thePlayer.posY - mc.thePlayer.prevPosY)) * nextFloat(minPredictSize.get(), maxPredictSize.get()),
                (entity.posZ - entity.prevPosZ - (mc.thePlayer.posZ - mc.thePlayer.prevPosZ)) * nextFloat(minPredictSize.get(), maxPredictSize.get())
            )
        }

        val (_, rotation) = searchCenter(
                boundingBox,
                outborderValue.get() && !attackTimer.hasTimePassed(attackDelay / 2),
                randomCenterValue.get(),
                predictValue.get(),
                mc.thePlayer.getDistanceToEntityBox(entity) < throughWallsRangeValue.get(),
                maxRange
        ) ?: return false

        var limitedRotation = limitAngleChange(serverRotation, rotation, nextFloat(minTurnSpeed.get(), maxTurnSpeed.get()))

        // Micronize rotations
        if (micronizedValue.get()) {
            // Get current target rotation if there are any
            val targetRotation = targetRotation ?: serverRotation // If there are no target rotations, use server rotation.

            // Does this rotation already face the target
            val reach = min(maxRange.toDouble(), mc.thePlayer.getDistanceToEntityBox(entity)) + 1

            if (isRotationFaced(entity, reach, targetRotation)) {
                // Micronize rotation
                limitedRotation = limitAngleChange(serverRotation, rotation, nextFloat(endInclusive = micronizedStrength.get()))
            }
        }

        if (silentRotationValue.get()) {
            setTargetRotation(limitedRotation, if (aacValue.get()) 15 else 0)
        } else {
            limitedRotation.toPlayer(mc.thePlayer)
        }

        return true
    }

    /**
     * Check if enemy is hitable with current rotations
     */
    private fun updateHitable() {
        // Disable hitable check if turn speed is zero
        if (maxTurnSpeed.isMinimal()) {
            hitable = true
            return
        }

        val reach = min(maxRange.toDouble(), mc.thePlayer.getDistanceToEntityBox(target!!)) + 1

        if (raycastValue.get()) {
            val raycastedEntity = raycastEntity(reach) { entity ->
                (!livingRaycastValue.get() || (entity is EntityLivingBase && entity !is EntityArmorStand)) &&
                        (isEnemy(entity) || raycastIgnoredValue.get() || aacValue.get() && mc.theWorld.getEntitiesWithinAABBExcludingEntity(entity, entity.entityBoundingBox).isNotEmpty())
            }

            if (raycastValue.get() && raycastedEntity != null && raycastedEntity is EntityLivingBase
                    && (moduleManager[NoFriends::class.java].state || !(raycastedEntity is EntityPlayer && raycastedEntity.isClientFriend())))
                currentTarget = raycastedEntity

            hitable = currentTarget == raycastedEntity
        } else
            hitable = isFaced(currentTarget!!, reach)
    }

    /**
     * Start blocking
     */
    private fun startBlocking(interactEntity: Entity, interact: Boolean, fake: Boolean = false) {
        if (!fake) {
            if (!(blockRate.get() > 0 && nextInt(endExclusive = 100) <= blockRate.get()))
                return

            if (interact) {
                val positionEye = mc.thePlayer.eyes

                val boundingBox = interactEntity.hitBox

                val (yaw, pitch) = targetRotation ?: Rotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch)
                val yawCos = cos(-yaw * 0.017453292 - Math.PI)
                val yawSin = sin(-yaw * 0.017453292 - Math.PI)
                val pitchCos = -cos(-pitch * 0.017453292)
                val pitchSin = sin(-pitch * 0.017453292)
                val range = min(maxRange.toDouble(), mc.thePlayer.getDistanceToEntityBox(interactEntity)) + 1
                val lookAt = positionEye.addVector(yawSin * pitchCos * range, pitchSin * range, yawCos * pitchCos * range)

                val movingObject = boundingBox.calculateIntercept(positionEye, lookAt) ?: return
                val hitVec = movingObject.hitVec

                mc.netHandler.addToSendQueue(C02PacketUseEntity(interactEntity, Vec3(
                    hitVec.xCoord - interactEntity.posX,
                    hitVec.yCoord - interactEntity.posY,
                    hitVec.zCoord - interactEntity.posZ)
                ))
                mc.netHandler.addToSendQueue(C02PacketUseEntity(interactEntity, C02PacketUseEntity.Action.INTERACT))
            }

            mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(BlockPos(-1, -1, -1), 255, mc.thePlayer.heldItem, 0f, 0f, 0f))
            blockStatus = true
        }

        renderBlocking = true
    }


    /**
     * Stop blocking
     */
    private fun stopBlocking() {
        if (blockStatus) {
            mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
            blockStatus = false
        }

        renderBlocking = false
    }

    /**
     * Check if run should be cancelled
     */
    private val cancelRun: Boolean
        inline get() = mc.thePlayer.isSpectator || !isAlive(mc.thePlayer)
                || moduleManager[Blink::class.java].state || moduleManager[FreeCam::class.java].state

    /**
     * Check if [entity] is alive
     */
    private fun isAlive(entity: EntityLivingBase) = entity.isEntityAlive && entity.health > 0 ||
            aacValue.get() && entity.hurtTime > 5

    /**
     * Check if player is able to block
     */
    private val canBlock: Boolean
        inline get() = mc.thePlayer?.heldItem?.item is ItemSword

    /**
     * Range
     */
    private val maxRange: Float
        get() = max(rangeValue.get(), throughWallsRangeValue.get())

    private fun getRange(entity: Entity) =
            (if (mc.thePlayer.getDistanceToEntityBox(entity) >= throughWallsRangeValue.get()) rangeValue.get() else throughWallsRangeValue.get()) - if (mc.thePlayer.isSprinting) rangeSprintReductionValue.get() else 0F

    /**
     * HUD Tag
     */
    override val tag
        get() = targetModeValue.get()

    val isBlockingChestAura: Boolean
        get() = state && target != null
}