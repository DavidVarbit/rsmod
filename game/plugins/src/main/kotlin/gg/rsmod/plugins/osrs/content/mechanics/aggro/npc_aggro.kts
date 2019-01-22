
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.TimerKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.osrs.api.helper.isAttacking
import gg.rsmod.plugins.osrs.api.helper.npc

val AGGRO_CHECK_TIMER = TimerKey()

val defaultAggressiveness: (Npc, Player) -> Boolean = boolean@ { n, p ->
    // TODO: check if player has been in area for more than 10-20 minutes
    val npcLvl = n.combatDef.combatLvl
    val playerLvl = p.getSkills().combatLevel
    return@boolean playerLvl > npcLvl * 2
}

r.bindGlobalNpcSpawn {
    val npc = it.npc()

    if (npc.combatDef.aggressiveRadius > 0 && npc.combatDef.findTargetDelay > 0) {
        npc.aggroCheck = defaultAggressiveness
        npc.timers[AGGRO_CHECK_TIMER] = npc.combatDef.findTargetDelay
    }
}

r.bindTimer(AGGRO_CHECK_TIMER) {
    val npc = it.npc()

    if (!npc.isAttacking() && npc.lock.canAttack() && npc.isActive()) {
        checkRadius(npc)
    }
    npc.timers[AGGRO_CHECK_TIMER] = npc.combatDef.findTargetDelay
}

fun checkRadius(npc: Npc) {
    val world = npc.world
    val centre = npc.calculateCentreTile()
    val radius = npc.combatDef.aggressiveRadius

    mainLoop@
    for (x in -radius .. radius) {
        for (z in -radius .. radius) {
            val tile = centre.transform(x, z)
            val chunk = world.chunks.getOrCreate(tile.toChunkCoords(), create = false) ?: continue

            val players = chunk.getEntities<Player>(EntityType.PLAYER, EntityType.CLIENT)
            if (players.isEmpty()) {
                continue
            }

            val targets = players.filter { canAttack(npc, it) }
            if (targets.isEmpty()) {
                continue
            }

            val target = targets.random()
            npc.attack(target)
            break@mainLoop
        }
    }
}

fun canAttack(npc: Npc, target: Player): Boolean {
    if (!target.isOnline() || target.invisible) {
        return false
    }
    return npc.aggroCheck?.invoke(npc, target) == true
}