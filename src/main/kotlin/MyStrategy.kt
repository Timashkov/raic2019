import model.*
import model.Unit
import java.util.concurrent.LinkedBlockingQueue
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign

class MyStrategy {

    companion object {
        internal fun distanceSqr(a: Vec2Double, b: Vec2Double): Double {
            return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
        }
    }

    private var boostJumpPerTick: Double = 0.0
    private var jumpPerTick: Double = 0.0
    private var maxDXPerTick: Double = 0.0
    private var maxJumpTick = 0
    private var maxBoostJumpTick = 0
    private var maxJumpTiles = 0.0
    private var maxBoostJumpTiles = 0.0
    private var ticksPerSec = 0.0
    private var unitHeight = 0.0
    private var unitWidth = 0.0
    private var enemyIndex = 0
    private val routes = HashMap<Int, Route>()
    private var nearestEnemy: model.Unit? = null

    fun getAction(unit: model.Unit, game: Game, debug: Debug): UnitAction {

        if (routes[unit.id] == null)
            routes[unit.id] = Route().apply {
                setPrevPosition(unit.position)
                default = unit.position
                targetNode = Node(Vec2Int(default.x.toInt(), default.y.toInt()))
            }

        if (boostJumpPerTick == 0.0) {
            maxDXPerTick = game.properties.unitMaxHorizontalSpeed / game.properties.ticksPerSecond
            jumpPerTick = game.properties.unitJumpSpeed / game.properties.ticksPerSecond
            boostJumpPerTick = game.properties.jumpPadJumpSpeed / game.properties.ticksPerSecond
            maxJumpTiles = (game.properties.unitJumpTime * game.properties.unitJumpSpeed)
            maxBoostJumpTiles = (game.properties.jumpPadJumpSpeed * game.properties.jumpPadJumpTime)
            maxJumpTick = (game.properties.unitJumpTime * game.properties.ticksPerSecond).toInt()
            maxBoostJumpTick = (game.properties.jumpPadJumpTime * game.properties.ticksPerSecond).toInt()
            ticksPerSec = game.properties.ticksPerSecond
            unitHeight = game.units[0].size.y
            unitWidth = game.units[0].size.x
        }

        val level = ArrayList<ArrayList<TileMarked>>()
        for (i in game.level.tiles.indices) {
            level.add(ArrayList())
            for (element in game.level.tiles[i]) {
                level[i].add(TileMarked(element))
            }
        }

        val units = game.units.filter { it.health > 0 }

        if (units.none { it.id == nearestEnemy?.id })
            nearestEnemy = null

        for (other in units) {
            if (other.playerId != unit.playerId) {
                if (nearestEnemy == null || nearestEnemy?.id == other.id || distanceSqr(
                        unit.position,
                        other.position
                    ) < distanceSqr(unit.position, nearestEnemy?.position ?: Vec2Double(0.0, 0.0))
                ) {
                    nearestEnemy = other
                }
            }
        }

        if (routes[unit.id]?.goToDefault == true &&
            game.units.none {
                it.playerId != unit.playerId
                        && abs(it.position.x - routes[unit.id]!!.targetNode.position.x) < abs(unit.position.x - routes[unit.id]!!.targetNode.position.x)
            } &&
            unit.weapon?.magazine != 0
        )
            routes[unit.id]!!.apply {
                goToDefault = false
                clearRoute()
            }

        if ((unit.health < game.properties.unitMaxHealth && unit.health < (nearestEnemy?.health ?: unit.health) ||
                    unit.health < game.properties.unitMaxHealth * 3 / 4)
            && !routes[unit.id]!!.isEmpty()
        ) {
            val target = routes[unit.id]!!.getTargetLootItem()
            if (target == null || game.lootBoxes.none {
                    it.item is Item.HealthPack &&
                            it.position.x == target.position.x &&
                            it.position.y == target.position.y
                } &&
                game.lootBoxes.any { lootbox ->
                    lootbox.item is Item.HealthPack && routes.none {
                        it.key != unit.id && units.any { unit -> unit.id == it.key } &&
                                it.value.getTargetLootItem()?.position?.x == lootbox.position.x &&
                                it.value.getTargetLootItem()?.position?.y == lootbox.position.y
                    }
                }) {
                routes[unit.id]!!.apply {
                    clearRoute()
                    goToDefault = true
                }
            }
        }

        if (game.lootBoxes.count { lootbox ->
                lootbox.item is Item.HealthPack && routes.none {
                    it.key != unit.id && units.any { unit -> unit.id == it.key } &&
                            it.value.getTargetLootItem()?.position?.x == lootbox.position.x &&
                            it.value.getTargetLootItem()?.position?.y == lootbox.position.y
                }
            } == 1 && game.units.any { it.playerId == unit.playerId && it.id < unit.id }) {
            routes[unit.id]!!.apply {
                clearRoute()
                goToDefault = true
            }
        }

        if (routes[unit.id]!!.isEmpty()) {
            routes[unit.id]!!.setNextPoint(
                Node(
                    position = Vec2Int(unit.position.x.toInt(), unit.position.y.toInt()),
                    jumpTile = if (unit.jumpState.canJump && unit.jumpState.maxTime < game.properties.unitJumpTime) getJumpTile(
                        unit.jumpState.maxTime,
                        game
                    ) else -1,
                    boostJumpTile = if (unit.jumpState.canJump && !unit.jumpState.canCancel) getJumpTile(
                        unit.jumpState.maxTime, game, true
                    ) else -1,
                    gen = 0
                )
            )
            routes[unit.id]!!.addRouteNodes(
                when {
                    (routes[unit.id]!!.goToDefault ||
                            (unit.health < game.properties.unitMaxHealth &&
                                    unit.health < (nearestEnemy?.health ?: unit.health
                                    ) ||
                                    unit.health < game.properties.unitMaxHealth * 3 / 4)
                            ) && game.lootBoxes.any { lootbox ->
                        lootbox.item is Item.HealthPack && routes.none {
                            it.key != unit.id && units.any { unit -> unit.id == it.key } &&
                                    it.value.getTargetLootItem()?.position?.x == lootbox.position.x &&
                                    it.value.getTargetLootItem()?.position?.y == lootbox.position.y
                        }
                    } -> {

                        val targets1 = game.lootBoxes.filter { lootbox ->
                            lootbox.item is Item.HealthPack &&
                                    routes.none {
                                        it.key != unit.id && units.any { unit -> unit.id == it.key } &&
                                                it.value.getTargetLootItem()?.position?.x == lootbox.position.x &&
                                                it.value.getTargetLootItem()?.position?.y == lootbox.position.y
                                    } &&
                                    abs(
                                        lootbox.position.x - (nearestEnemy?.position?.x ?: 0.0)
                                    ) > abs(lootbox.position.x - unit.position.x)
                        }.map { lootBox ->
                            SquareObject(
                                lootBox.position,
                                lootBox.size,
                                false
                            )
                        }
                        val targets2 = game.lootBoxes.filter { lootbox ->
                            lootbox.item is Item.HealthPack && routes.none {
                                it.key != unit.id && units.any { unit -> unit.id == it.key } &&
                                        it.value.getTargetLootItem()?.position?.x == lootbox.position.x &&
                                        it.value.getTargetLootItem()?.position?.y == lootbox.position.y
                            }
                        }.map { lootBox ->
                            SquareObject(
                                lootBox.position,
                                lootBox.size,
                                false
                            )
                        }

                        val localRoute = ArrayList<Node>()
                        buildPath(
                            unit,
                            targets1,
                            level,
                            debug,
                            game,
                            localRoute,
                            routes[unit.id]?.getNextPoint()
                        )
                        if (localRoute.isEmpty()) {
                            buildPath(
                                unit,
                                targets2,
                                level,
                                debug,
                                game,
                                localRoute,
                                routes[unit.id]?.getNextPoint()
                            )
                        }
                        if (localRoute.isNotEmpty()) {
                            for (item in game.lootBoxes) {
                                if (item.position.x.toInt() == localRoute[0].position.x && item.position.y.toInt() == localRoute[0].position.y)
                                    routes[unit.id]!!.setTargetLootItem(item)
                            }
                            localRoute.reverse()
                        }
                        localRoute
                    }
                    unit.weapon == null && game.lootBoxes.any { it.item is Item.Weapon } -> {
                        val localRoute = ArrayList<Node>()
                        buildPath(
                            unit,
                            game.lootBoxes.filter { lootbox ->
                                lootbox.item is Item.Weapon &&
                                        routes.none {
                                            it.value.getTargetLootItem()?.position?.x == lootbox.position.x &&
                                                    it.value.getTargetLootItem()?.position?.y == lootbox.position.y
                                        }
                            }.map { lootBox ->
                                SquareObject(
                                    lootBox.position,
                                    lootBox.size,
                                    false
                                )
                            },
                            level,
                            debug,
                            game,
                            localRoute,
                            routes[unit.id]?.getNextPoint()
                        )
                        if (localRoute.isNotEmpty()) {
                            for (item in game.lootBoxes) {
                                if (item.position.x.toInt() == localRoute[0].position.x && item.position.y.toInt() == localRoute[0].position.y)
                                    routes[unit.id]!!.setTargetLootItem(item)
                            }
                            localRoute.reverse()
                        }
                        localRoute
                    }
                    routes[unit.id]!!.goToDefault -> {
                        val localRoute = ArrayList<Node>()
                        buildPath(
                            unit,
                            listOf(SquareObject(routes[unit.id]!!.default, unit.size, false)),
                            level,
                            debug,
                            game,
                            localRoute,
                            routes[unit.id]?.getNextPoint()
                        )
                        if (localRoute.isNotEmpty()) {
                            for (item in game.lootBoxes) {
                                if (item.position.x.toInt() == localRoute[0].position.x && item.position.y.toInt() == localRoute[0].position.y)
                                    routes[unit.id]!!.setTargetLootItem(item)
                            }
                            localRoute.reverse()
                        }
                        localRoute
                    }
                    else -> {
                        enemyIndex = 5
                        ArrayList()
                    }
                }
            )
        }

        if (enemyIndex >= 5) {

            routes[unit.id]!!.setNextPoint(
                Node(
                    position = Vec2Int(unit.position.x.toInt(), unit.position.y.toInt()),
                    jumpTile = if (unit.jumpState.canJump && unit.jumpState.maxTime < game.properties.unitJumpTime) getJumpTile(
                        unit.jumpState.maxTime,
                        game
                    ) else -1,
                    boostJumpTile = if (unit.jumpState.canJump && !unit.jumpState.canCancel) getJumpTile(
                        unit.jumpState.maxTime, game, true
                    ) else -1,
                    gen = 0,
                    goingToEnemy = true
                )
            )
            enemyIndex = 0
            val enSquare = SquareObject(
                pos = nearestEnemy?.position ?: Vec2Double(0.0, 0.0),
                size = nearestEnemy?.size ?: Vec2Double(0.0, 0.0),
                isEnemy = true
            )
            if ((!routes[unit.id]!!.goToDefault && unit.health == game.properties.unitMaxHealth) || routes[unit.id]!!.isEmpty()) {
                routes[unit.id]!!.clearRoute()
                val localRoute = ArrayList<Node>()

                buildPath(
                    unit,
                    arrayListOf(enSquare),
                    level,
                    debug,
                    game,
                    localRoute,
                    routes[unit.id]?.getNextPoint()
                )
                localRoute.reverse()
                routes[unit.id]!!.addRouteNodes(localRoute)
            }
        }

        // drawRoute(route[unit.id], debug)

        var targetPos = routes[unit.id]?.getNextStep(unit, game)
        targetPos?.let {
            val tli = routes[unit.id]?.getTargetLootItem() ?: return@let
            if (tli.position.x.toInt() == it.x.toInt() && tli.position.y.toInt() == it.y.toInt()) {
                if (it.x.toInt() > 0 && game.level.tiles[it.x.toInt() - 1][it.y.toInt()].discriminant == Tile.JUMP_PAD.discriminant)
                    it.apply { x += 0.2 }

                if (it.x.toInt() < 39 && game.level.tiles[it.x.toInt() + 1][it.y.toInt()].discriminant == Tile.JUMP_PAD.discriminant)
                    it.apply { x -= 0.2 }
            }
        }
        if (targetPos == null) {
            targetPos = if (game.units.any { it.playerId == unit.playerId && it.id > unit.id }) {
                nearestEnemy?.position ?: Vec2Double(0.5, 0.5)
            } else {
                unit.position
            }
        }

        if (distanceSqr(targetPos!!, routes[unit.id]?.getUnitPrevPosition() ?: Vec2Double(0.0, 0.0)) < distanceSqr(
                targetPos!!,
                unit.position
            ) &&
            distanceSqr(targetPos!!, unit.position) > 5
        ) {
            routes[unit.id]?.clearRoute()
        }
        routes[unit.id]?.setPrevPosition(unit.position)

        var aim = Vec2Double(0.0, 0.0)
        nearestEnemy?.let {
            aim = Vec2Double(
                (it.position.x - unit.position.x) * 10,
                (it.position.y - unit.position.y) * 10
            )
        }

        val action = UnitAction()

        action.velocity = getVelocity(unit.position.x, targetPos.x)
        action.jump =
            targetPos.y > routes[unit.id]?.getPrevPoint()?.position?.y?.toDouble() ?: targetPos.y || targetPos.y > unit.position.y
        action.jumpDown =
            targetPos.y < routes[unit.id]?.getPrevPoint()?.position?.y?.toDouble() ?: targetPos.y && targetPos.y < unit.position.y - jumpPerTick

        action.aim = aim
//        action.reload = false

//        action.shoot = false
        if (nearestEnemy == null || unit.weapon == null) {
            action.shoot = false
        } else {
            if (shootAllowed(unit, nearestEnemy, game, debug)) {
                action.shoot = !canShutTeammate(unit, game, nearestEnemy, debug)// isShootEffective(unit, nearestEnemy!!)

                enemyIndex++

                if (unit.weapon?.magazine == 0 && distanceSqr(
                        unit.position,
                        nearestEnemy?.position ?: Vec2Double()
                    ) < 5
                ) {
                    routes[unit.id]?.apply {
                        clearRoute()
                        goToDefault = true
                    }
                }

                if (routes[unit.id]?.goToDefault == true) {
                    routes[unit.id]?.apply {
                        clearRoute()
                        goToDefault = false
                    }
                }
            } else {
                val en =
                    game.units.firstOrNull { it.playerId != unit.playerId && it.id != nearestEnemy?.id && it.health > 0 }
                if (en != null && shootAllowed(unit, en, game, debug)) {
                    action.shoot = !canShutTeammate(unit, game, en, debug)// isShootEffective(unit, nearestEnemy!!)

                    enemyIndex++

                    if (unit.weapon?.magazine == 0 && distanceSqr(
                            unit.position,
                            nearestEnemy?.position ?: Vec2Double()
                        ) < 5
                    ) {
                        routes[unit.id]?.apply {
                            clearRoute()
                            goToDefault = true
                        }
                    }

                    if (routes[unit.id]?.goToDefault == true) {
                        routes[unit.id]?.apply {
                            clearRoute()
                            goToDefault = false
                        }
                    }
                } else {
                    action.shoot = false
                    if (distanceSqr(unit.position, nearestEnemy?.position ?: Vec2Double()) < 5) {
                        routes[unit.id]?.apply {
                            clearRoute()
                            goToDefault = true
                        }
                    }
                }
            }
// add intelli
//            if (!action.shoot && unit.weapon?.params?.magazineSize != unit.weapon?.magazine) {
//                action.reload = true
//            }
        }
//        if (unit.weapon?.typ != WeaponType.PISTOL) {
//            for (item in game.lootBoxes) {
//
//                if (item.item is Item.Weapon &&
//                    (item.item as Item.Weapon).weaponType == WeaponType.PISTOL &&
//                    unitTargetCollisionDetected(unit.position, unit.size, item.position, item.size)
//                )
//                    action.swapWeapon = true
//            }
//        }
        action.plantMine = false

//        if (!::unitMovement.isInitialized)
//            unitMovement = UnitMovement(unit.position, unit.onGround)
//        else
//            unitMovement.apply {
//                pos.x = unit.position.x
//                pos.y = unit.position.y
//            }
//
//        unitMovement = getUnitMovement(unitMovement, unit.size, unit.id, action, game)
//        debug.draw(
//            CustomData.Rect(
//                Vec2Float(unitMovement.pos.x.toFloat() - 0.2f, unitMovement.pos.y.toFloat() - 0.2f),
//                Vec2Float(0.4f, 0.4f),
//                ColorFloat(0f, 255f, 0f, 255f)
//            )
//        )
//        debug.draw(
//            CustomData.Rect(
//                Vec2Float(unit.position.x.toFloat() - 0.1f, unit.position.y.toFloat() - 0.1f),
//                Vec2Float(0.2f, 0.2f),
//                ColorFloat(0f, 0f, 255f, 255f)
//            )
//        )

        return action
    }

    private fun getJumpTile(maxTime: Double, game: Game, isPad: Boolean = false): Int {
        val alreadyInFly = if (isPad) {
            (game.properties.jumpPadJumpTime - maxTime) * game.properties.jumpPadJumpSpeed
        } else {
            (game.properties.unitJumpSpeed - maxTime) * game.properties.unitJumpSpeed
        }

        return alreadyInFly.toInt()
    }

    private fun buildPath(
        unit: Unit,
        targets: List<SquareObject>,
        level: ArrayList<ArrayList<TileMarked>>,
        debug: Debug,
        game: Game,
        route: ArrayList<Node>,
        knownNode: Node? = null
    ) {
        if (targets.isNullOrEmpty())
            return
        val nodes = LinkedBlockingQueue<Node>()

        val firstNode = if (knownNode == null ||
            abs(knownNode.position.x - unit.position.x.toInt()) > unit.size.x * 2 ||
            abs(knownNode.position.y - unit.position.y.toInt()) > unit.size.y * 2
        ) Node(
            Vec2Int(unit.position.x.toInt(), unit.position.y.toInt()),
            jumpTile = -1,
            boostJumpTile = -1,
            goingToEnemy = targets[0].isEnemy
        ) else
            knownNode

        for (target in targets) {
            if (isTargetAchieved(target, firstNode, unit)) {
                route.add(firstNode.parentLessClone())
                return
            }
        }

        nodes.add(firstNode)

        var r: Node? = null

        while (nodes.isNotEmpty() && r == null) {
            val n = nodes.poll()
            r = checkTiles(n, targets, level, debug, game, unit, nodes)
        }

        level.forEach { it.forEach { jt -> jt.mark = 0 } }

        r?.let {
            var qq = it
            do {
                route.add(qq.parentLessClone())
                qq = qq.parentNode ?: return
            } while (qq != null)
        }
    }

    private fun checkTiles(
        currentNode: Node,
        targets: List<SquareObject>,
        level: ArrayList<ArrayList<TileMarked>>,
        debug: Debug,
        game: Game,
        unit: Unit,
        nodes: LinkedBlockingQueue<Node>
    ): Node? {

        var dx = targets[0].pos.x - currentNode.position.x
        val dy = targets[0].pos.y - currentNode.position.y

        if (targets[0].isEnemy &&
            abs(currentNode.position.x - targets[0].pos.x) < 3 &&
            abs(currentNode.position.y - targets[0].pos.y) < 3
        )
            dx *= -1

        if (currentNode.jumpTile >= maxJumpTiles.toInt() || currentNode.position.y < currentNode.parentNode?.position?.y ?: 0)
            currentNode.jumpTile = -1
        if (currentNode.boostJumpTile >= maxBoostJumpTiles.toInt() || currentNode.position.y < currentNode.parentNode?.position?.y ?: 0)
            currentNode.boostJumpTile = -1
        if (currentNode.boostJumpTile != 0 && (level[currentNode.position.x][currentNode.position.y].type == Tile.JUMP_PAD ||
                    (currentNode.position.y > 0 && level[currentNode.position.x][currentNode.position.y - 1].type == Tile.JUMP_PAD))
        ) {
            currentNode.boostJumpTile = 0
        }

        val canGoUp = canGoUpTile(currentNode, level, game, unit)
        if (!canGoUp) {
            currentNode.boostJumpTile = -1
            currentNode.jumpTile = -1
        }

        for (i in 0..4) {
            val direction = Vec2Int(currentNode.position.x, currentNode.position.y)
            when (i) {
                0 -> direction.apply { x += if (dx > 0) 1 else -1 }
//                1 -> direction.apply { x -= if (dx > 0) 1 else -1 }
                1 -> direction.apply {
                    x += if (dx > 0) 1 else -1
                    y += if (dy > 0) 1 else -1
                }
//                3 -> direction.apply {
//                    x -= if (dx > 0) 1 else -1
//                    y += if (dy > 0) 1 else -1
//                }
                2 -> direction.apply {
                    x += if (dx > 0) 1 else -1
                    y -= if (dy > 0) 1 else -1
                }
//                5 -> direction.apply {
//                    x -= if (dx > 0) 1 else -1
//                    y -= if (dy > 0) 1 else -1
//                }
                3 -> direction.apply { y += if (dy > 0) 1 else -1 }
                else -> direction.apply { y -= if (dy > 0) 1 else -1 }
            }

            if (currentNode.parentNode?.position?.y == direction.y && currentNode.parentNode.position.x == direction.x)
                continue

            if (currentNode.position.y == direction.y && !canGoHorizontalTile(currentNode, level, direction.x))
                continue

            if (currentNode.position.y + 1 == direction.y && !canGoUp) {
                continue
            }

            if (direction.y == currentNode.position.y - 1 && !canGoDown(
                    currentNode,
                    level,
                    direction.x,
                    game,
                    unit
                )
            ) {
                continue
            }

            val jmpStep = when {
                currentNode.jumpTile >= 0 && direction.y == currentNode.position.y + 1 -> currentNode.jumpTile + 1
                currentNode.jumpTile >= 0 && direction.y == currentNode.position.y -> currentNode.jumpTile
                else -> -1
            }

            val bstJmpStep = when {
                currentNode.boostJumpTile >= 0 && direction.y == currentNode.position.y + 1 -> currentNode.boostJumpTile + 1
                else -> -1
            }

            if (direction.x >= 0 &&
                direction.x < level.size &&
                direction.y >= 0 &&
                direction.y < level[0].size - 1 &&
                level[direction.x][direction.y].type != Tile.WALL &&
                level[direction.x][direction.y + 1].type != Tile.WALL &&
                nearestUnitIsNotBreak(direction, currentNode, game, unit) &&
                level[direction.x][direction.y].mark < 10 &&
                nodes.none {
                    it.position.x == direction.x && it.position.y == direction.y &&
                            (it.gen == currentNode.gen + 1 || (it.jumpTile == jmpStep && it.boostJumpTile == bstJmpStep))
                }
            ) {

                level[direction.x][direction.y].mark++

                val node = Node(
                    Vec2Int(direction.x, direction.y),
                    jmpStep,
                    bstJmpStep,
                    currentNode.gen + 1,
                    currentNode.goingToEnemy,
                    currentNode
                )
                nodes.add(node)

                nodes.removeIf {
                    it.position.x == direction.x && it.position.y == direction.y &&
                            it.gen == currentNode.gen + 1 && it.jumpTile > jmpStep && jmpStep > 0
                }
                for (target in targets) {
                    if (isTargetAchieved(target, node, unit))
                        return node
                }
            }
        }

        return null
    }

    private fun isTargetAchieved(target: SquareObject, node: Node, unit: Unit): Boolean {
        if (abs(node.position.x + unit.size.x / 2 - target.pos.x) < (target.size.x / 2) &&
            abs(node.position.y - target.pos.y) < (target.size.y)
        )
            return true

        if (target.isEnemy &&
            abs(node.position.x - target.pos.x) < 4 &&
            abs(node.position.y - target.pos.y) < 4 &&
            (abs(node.position.x - target.pos.x) > 2 || abs(node.position.y - target.pos.y) > 2)
        )
            return true
        return false
    }

    private fun canGoUpTile(
        currentNode: Node,
        level: ArrayList<ArrayList<TileMarked>>,
        game: Game,
        unit: Unit
    ): Boolean {
        //up restriction
        if (currentNode.position.y >= (level[0].size - unitHeight).toInt() ||
            level[currentNode.position.x][currentNode.position.y + 2].type == Tile.WALL ||
            level[currentNode.parentNode?.position?.x
                ?: currentNode.position.x][currentNode.position.y + 2].type == Tile.WALL
        ) {
            //потолок
            return false
        }

        game.units.forEach {
            if (it.id != unit.id) {

                if (abs(it.position.x - currentNode.position.x) < unitWidth) {
                    val enemyPositionYDiff =
                        it.position.y + it.size.y - (currentNode.position.y + unitHeight)
                    if (enemyPositionYDiff > jumpPerTick && enemyPositionYDiff < 1)
                    //вражина мешает
                        return@canGoUpTile false
                }
            }
        }

        // end up restriction

        if (level[currentNode.position.x][currentNode.position.y].type == Tile.LADDER ||
            currentNode.position.y <= 0 ||
            level[currentNode.position.x][currentNode.position.y - 1].type != Tile.EMPTY
        ) {
            // когда не пусто под ногами - можно прыгать
            if (currentNode.jumpTile != 0)
                currentNode.jumpTile = 0
            return true
        }

        game.units.forEach {
            if (it.id != unit.id) {
                if ((abs(it.position.y + unitHeight - currentNode.position.y) <= jumpPerTick &&
                            abs(it.position.x - currentNode.position.x) <= 1)
                )
                // когда не пусто под ногами - можно прыгать
                    if (currentNode.jumpTile != 0)
                        currentNode.jumpTile = 0
                return@canGoUpTile true
            }
        }

        if ((currentNode.jumpTile == -1 &&
                    currentNode.boostJumpTile == -1)
        )
        //не могём тут прыгать
            return false

        return true
    }

    private fun canGoDown(
        currentNode: Node,
        level: ArrayList<ArrayList<TileMarked>>,
        newX: Int,
        game: Game,
        unit: Unit
    ): Boolean {
        if (currentNode.boostJumpTile != -1)
            return false
        if (currentNode.position.y <= 0 ||
            level[currentNode.position.x][currentNode.position.y - 1].type == Tile.WALL
        )
            return false
        game.units.forEach {
            if (it.id != unit.id && abs(it.position.y + it.size.y - currentNode.position.y) <= jumpPerTick &&
                abs(it.position.x - currentNode.position.x) <= (it.size.x / 2 + unitWidth / 2)
            )
                return@canGoDown false
        }
        return true
    }

    private fun canGoHorizontalTile(
        currentNode: Node,
        level: ArrayList<ArrayList<TileMarked>>,
        newX: Int
    ): Boolean {
        if (currentNode.boostJumpTile != -1)
            return false
        return currentNode.position.y == 0 ||
                (newX < level.size &&
                        level[currentNode.position.x][currentNode.position.y - 1].type != Tile.EMPTY &&
                        level[newX][currentNode.position.y - 1].type != Tile.EMPTY)
    }

    private fun getVelocity(xCurrent: Double, xTarget: Double): Double {
        val dx = (xTarget - xCurrent)
        return when {
            abs(dx) > maxDXPerTick -> {
                dx * ticksPerSec
            }
            abs(dx) < 0.001 -> {
                sign(dx) * ticksPerSec // fix for infinite moving
            }
            else -> {
                dx * ticksPerSec
            }
        }
    }

    private fun nearestUnitIsNotBreak(direction: Vec2Int, currentNode: Node, game: Game, unit: Unit): Boolean {
        game.units.forEach {
            if (it.id != unit.id) {
                if (direction.x > currentNode.position.x) {
                    if (!(it.position.x < currentNode.position.x ||
                                direction.x < it.position.x - it.size.x * 2 ||
                                (direction.y > (it.position.y + it.size.y) ||
                                        direction.y + unitHeight < it.position.y))
                    )
                        return@nearestUnitIsNotBreak false

                } else {
                    if (!(it.position.x > currentNode.position.x ||
                                direction.x > it.position.x + it.size.x * 2 ||
                                (direction.y > (it.position.y + it.size.y) ||
                                        direction.y + unitHeight < it.position.y))
                    )
                        return@nearestUnitIsNotBreak false
                }
            }
        }

        return true
    }

    private fun drawRoute(r: ArrayList<Node>, debug: Debug) {
        if (r.isNotEmpty()) {
            r.forEach { step ->
                debug.draw(
                    CustomData.Rect(
                        Vec2Float(
                            step.position.x.toFloat() + 0.4f,
                            step.position.y.toFloat() + 0.4f
                        ),
                        Vec2Float(0.2f, 0.2f),
                        ColorFloat(111f, 111f, 111f, 255f)
                    )
                )
            }
        }

    }

    private fun getUnitMovement(
        sourceMovement: UnitMovement,
        unitSize: Vec2Double,
        unitId: Int,
        act: UnitAction,
        game: Game
    ): UnitMovement {
        val pos = Vec2Double(sourceMovement.pos.x, sourceMovement.pos.y)
        //X prediction
        val requiredDx = act.velocity / game.properties.ticksPerSecond

        pos.x += if (abs(requiredDx) <= maxDXPerTick) requiredDx else maxDXPerTick * sign(requiredDx)

        val checkingX = pos.x + sign(requiredDx) * unitSize.x / 2

        if (game.level.tiles[checkingX.toInt()][(pos.y).toInt()] == Tile.WALL ||
            game.level.tiles[checkingX.toInt()][(pos.y).toInt() + 1] == Tile.WALL
        )
            pos.x = checkingX.toInt() - sign(requiredDx) * ((if (sign(requiredDx) < 0) 1 else 0) + unitSize
                .x / 2)
        if (game.units.any {
                it.id != unitId &&
                        abs(it.position.x - pos.x) < unitSize.x &&
                        abs(it.position.x - pos.x) > unitSize.x / 2 &&
                        abs(it.position.y - pos.y) < unitSize.y
            }) {
            pos.x = sourceMovement.pos.x
        }

        //Y prediction

        val boostJumpStarts = game.level.tiles.any {
            (game.level.tiles.indexOf(it) == (pos.x - unitSize.x / 2).toInt() ||
                    game.level.tiles.indexOf(it) == (pos.x + unitSize.x / 2).toInt()) &&
                    (it[pos.y.toInt()] == Tile.JUMP_PAD || it[(pos.y + unitSize.y / 2).toInt()] == Tile.JUMP_PAD)
        }

        if (boostJumpStarts) {
            pos.y += boostJumpPerTick
            return UnitMovement(pos, jumpAllowed = false, boostJump = true, boostJumpTick = 1)
        }

        var boostJumpTick = sourceMovement.boostJumpTick
        var jumpTick = sourceMovement.jumpTick
        var jumpAllowed = sourceMovement.jumpAllowed

        when {

            sourceMovement.boostJumpTick in 1..maxBoostJumpTick -> {
                pos.y += boostJumpPerTick
                boostJumpTick++
                if (isUnitOnLadder(pos, unitSize, game)) {
                    jumpTick = 0
                    boostJumpTick = 0
                    jumpAllowed = true
                    return UnitMovement(pos, jumpAllowed, boostJump = true, boostJumpTick = boostJumpTick)
                }
                if ((pos.y + unitSize.y) >= (game.level.tiles[0].size - boostJumpPerTick)) {
                    boostJumpTick = 0
                    jumpTick = 0
                    jumpAllowed = false
                    pos.y = game.level.tiles[0].size - unitSize.y
                    return UnitMovement(pos, jumpAllowed, boostJump = false, boostJumpTick = boostJumpTick)
                }
            }

            isJump(
                act.jump,
                jumpTick,
                maxJumpTick,
                game,
                pos, unitSize,
                jumpAllowed,
                pos.y
            ) -> {
                boostJumpTick = 0

                pos.y += jumpPerTick

                if ((pos.y + unitSize.y) >= (game.level.tiles[0].size - jumpPerTick)) {
                    jumpTick = 0
                    jumpAllowed = false
                    pos.y = game.level.tiles[0].size - unitSize.y
                    return UnitMovement(pos, jumpAllowed, boostJump = false, boostJumpTick = boostJumpTick)
                }
                if (isUnitOnLadder(sourceMovement.pos, unitSize, game)) {
                    jumpTick = 0
                } else {
                    jumpTick++
                }

            }
            act.jumpDown -> {
                boostJumpTick = 0

                pos.y -= jumpPerTick
                jumpTick = -1
                val tileLeft = game.level.tiles[(pos.x - unitSize.x / 2).toInt()][(pos.y).toInt()]
                val tileRight = game.level.tiles[(pos.x + unitSize.x / 2).toInt()][(pos.y).toInt()]
                if (tileLeft == Tile.LADDER && tileRight in arrayOf(Tile.LADDER, Tile.EMPTY)
                    || tileRight == Tile.LADDER && tileLeft in arrayOf(
                        Tile.LADDER, Tile
                            .EMPTY
                    )
                ) {
                    jumpTick = 0
                    jumpAllowed = true
                    return UnitMovement(pos, jumpAllowed)
                }

                when {
                    pos.y < 0 -> {
                        pos.y = 0.0
                        jumpAllowed = true
                    }
                    pos.y.toInt() >= 0 -> {
                        if (tileLeft == Tile.WALL || tileRight == Tile.WALL) {
                            pos.y = (pos.y.toInt() + 1).toDouble()
                            jumpAllowed = true
                        }
                    }
                }
            }
            else -> {
                boostJumpTick = 0

                jumpAllowed = false
                jumpTick = -1
                val tileBeforeMoving = game.level.tiles[(pos.x).toInt()][pos.y.toInt()]
                if (tileBeforeMoving == Tile.LADDER) {
                    jumpTick = 0
                    jumpAllowed = true
                    return UnitMovement(pos, jumpAllowed)
                }

                if (pos.y.toInt() == (pos.y - jumpPerTick).toInt()) {

                    return UnitMovement(
                        pos.apply { y -= jumpPerTick },
                        jumpAllowed,
                        boostJump = false,
                        boostJumpTick = boostJumpTick
                    )
                }

                pos.y -= jumpPerTick
                val tileLeft = game.level.tiles[(pos.x - unitSize.x / 2).toInt()][pos.y.toInt()]
                val tileRight = game.level.tiles[(pos.x + unitSize.x / 2).toInt()][pos.y.toInt()]

                when {
                    pos.y < 0 -> {
                        jumpAllowed = true
                        jumpTick = 0
                        pos.y = 0.0
                    }
                    pos.y.toInt() >= 0 -> {
                        if (tileLeft in arrayOf(Tile.WALL, Tile.PLATFORM) ||
                            tileRight in arrayOf(
                                Tile.WALL,
                                Tile.PLATFORM
                            ) && (pos.y - pos.y.toInt().toDouble()) <
                            jumpPerTick
                        ) {
                            jumpTick = 0
                            jumpAllowed = true
                            pos.y = (pos.y.toInt() + 1).toDouble()
                        }
                    }
                }
            }
        }

        return UnitMovement(
            pos,
            jumpAllowed,
            jumpTick = jumpTick,
            boostJump = boostJumpTick in 1..maxBoostJumpTick,
            boostJumpTick = boostJumpTick
        )
    }

    private fun isJump(
        actionJump: Boolean,
        jumpTick: Int,
        maxJumpTick: Int,
        game: Game,
        pos: Vec2Double,
        unitSize: Vec2Double,
        jumpAllowed: Boolean,
        y: Double
    ): Boolean {
        if (!actionJump) return false
        if (jumpTick in 0..maxJumpTick + 1) return true
        val tileBeforeMoving = game.level.tiles[pos.x.toInt()][pos.y.toInt()]

        if (tileBeforeMoving == Tile.LADDER) return true
        if (!jumpAllowed) return false

        if (y < jumpPerTick) return true
        if (y.toInt() == 0) return false

        val yz = y.toInt() - 1

        val tileBeforeMovingLeft = game.level.tiles[(pos.x - unitSize.x / 2).toInt()][yz]
        val tileBeforeMovingRight = game.level.tiles[(pos.x + unitSize.x / 2).toInt()][yz]

        if ((tileBeforeMovingLeft == Tile.WALL ||
                    tileBeforeMovingRight == Tile.WALL ||
                    tileBeforeMovingLeft == Tile.PLATFORM ||
                    tileBeforeMovingRight == Tile.PLATFORM
                    ) && (y - yz.toDouble() < jumpPerTick || y == (yz + 1).toDouble()) && y >= yz.toDouble()
        )
            return true

        return false
    }

    private fun isUnitOnLadder(position: Vec2Double, size: Vec2Double, game: Game): Boolean {
        val unitCenter = Vec2Double(position.x, position.y + size.y / 2)
        val tileBottom = game.level.tiles[unitCenter.x.toInt()][unitCenter.y.toInt()]
        val tileTop = game.level.tiles[unitCenter.x.toInt()][(unitCenter.y + size.y / 2).toInt()]
        return tileBottom == Tile.LADDER || tileTop == Tile.LADDER
    }

    private fun isShootEffective(unit: Unit, nearestEnemy: Unit): Boolean {
        val dx = if (unit.position.x > nearestEnemy.position.x)
            -nearestEnemy.size.x / 2
        else
            nearestEnemy.size.x / 2
        val x1 = nearestEnemy.position.x + dx
        val x2 = nearestEnemy.position.x - dx
        val y1 = nearestEnemy.position.y + nearestEnemy.size.y
        val y2 = nearestEnemy.position.y + 0

        val unitCenter = Vec2Double(unit.position.x, unit.position.y + unit.size.y / 2)

        val a1 = atan2(y1 - unitCenter.y, x1 - unitCenter.x)
        val a2 = atan2(y2 - unitCenter.y, x2 - unitCenter.x)

        var deltaAngle = abs(a1 - a2)
        while (deltaAngle > kotlin.math.PI * 2) {
            deltaAngle -= kotlin.math.PI * 2
        }
        if (deltaAngle < (unit.weapon?.spread ?: 0.0 * 3 / 5))
            return false
        return true
    }

    private fun shootAllowed(
        unit: model.Unit,
        nearestEnemy: model.Unit?,
        game: Game,
        debug: Debug
    ): Boolean {
        if (nearestEnemy == null)
            return false
        val unitCenter = Vec2Double(unit.position.x, unit.position.y + unit.size.y / 2)
        val enemyCenter =
            Vec2Double(nearestEnemy.position.x, nearestEnemy.position.y + nearestEnemy.size.y / 2)

        var xl = unitCenter.x
        var xr = enemyCenter.x
        if (unitCenter.x > enemyCenter.x) {
            xl = enemyCenter.x
            xr = unitCenter.x
        }

        var yt = enemyCenter.y - if (unit.weapon?.typ == WeaponType.ROCKET_LAUNCHER) unit.size.y / 2 else 0.0
        var yb = unitCenter.y
        if (unitCenter.y > enemyCenter.y) {
            yb = enemyCenter.y
            yt = unitCenter.y
        }

        val indexLeft = xl.toInt()
        val indexRight = xr.toInt() + 1
        val indexBottom = yb.toInt()
        val indexTop = yt.toInt()

        for (i in if (unitCenter.x > enemyCenter.x) indexRight downTo indexLeft else indexLeft..indexRight) {
            for (j in indexBottom..indexTop) {
                if (game.level.tiles[i][j].discriminant == Tile.WALL.discriminant) {
                    if (xl.toInt() == xr.toInt() || yt.toInt() == yb.toInt() ||
                        directrixTileCollision(
                            Vec2Double(
                                i.toDouble(),
                                j.toDouble()
                            ),
                            Vec2Double(1.0, 1.0),
                            unitCenter,
                            enemyCenter,
                            unit.weapon!!.spread,
                            game.properties.weaponParams[unit.weapon!!.typ]?.bullet?.size
                                ?: 0.0,
                            debug
                        )
                    /* + check for rocket and distances*/
                    ) {
                        debug.draw(
                            CustomData.Rect(
                                Vec2Float(i.toFloat(), j.toFloat()),
                                Vec2Float(1f, 1f),
                                ColorFloat(0f, 155f, 155f, 110f)
                            )
                        )
                        return false
                    }
                }
            }
        }
        if (unit.weapon?.typ == WeaponType.ROCKET_LAUNCHER) {

            val r = game.properties.weaponParams[WeaponType.ROCKET_LAUNCHER]?.explosion?.radius ?: 0.0
            val dam = game.properties.weaponParams[WeaponType.ROCKET_LAUNCHER]?.explosion?.damage ?: 0
            var left =
                if (enemyCenter.x < unitCenter.x)
                    (enemyCenter.x - (unit.size.x / 2) - r).toInt()
                else
                    (unitCenter.x - (unit.size.x / 2) - r).toInt()
            if (left < 0) left = 0

            var right =
                if (enemyCenter.x >= unitCenter.x)
                    (enemyCenter.x + (unit.size.x / 2) + r).toInt()
                else
                    (unitCenter.x + (unit.size.x / 2) + r).toInt()
            if (right >= game.level.tiles.size)
                right = game.level.tiles.size - 1

            var top =
                if (enemyCenter.y >= unitCenter.y)
                    (nearestEnemy.position.y + (unit.size.y) + r).toInt()
                else
                    (unit.position.y + (unit.size.y) + r).toInt()
            if (top >= game.level.tiles[0].size)
                top = game.level.tiles[0].size - 1
            var bottom =
                if (enemyCenter.y < unitCenter.y)
                    (nearestEnemy.position.y - r).toInt()
                else
                    (unit.position.y - r).toInt()
            if (bottom < 0)
                bottom = 0

            if (left > 0)
                left--
            if (top < game.level.tiles[0].size - 1)
                top++
            if (right < game.level.tiles.size - 1)
                right++
            if (bottom > 0)
                bottom--

            for (i in left..right) {
                for (j in bottom..top) {
                    if ((i < 0 || j < 0 || i >= game.level.tiles.size || j >= game.level.tiles[0].size || game.level.tiles[i][j].discriminant == Tile.WALL.discriminant)
                        && directrixTileCollision(
                            Vec2Double(
                                i.toDouble(),
                                j.toDouble()
                            ),
                            Vec2Double(1.0, 1.0),
                            unitCenter,
                            enemyCenter,
                            unit.weapon!!.spread,
                            game.properties.weaponParams[WeaponType.ROCKET_LAUNCHER]?.bullet?.size
                                ?: 0.0,
                            debug
                        )
                    ) {

                        if (distanceSqr(unitCenter, Vec2Double(i.toDouble(), j.toDouble())) > (r + 2) || (
                                    distanceSqr(enemyCenter, Vec2Double(i.toDouble(), j.toDouble())) <= r &&
                                            dam >= nearestEnemy.health && nearestEnemy.health < unit.health)

                        )
                            continue

                        if (game.units.count { it.playerId != unit.playerId && it.health > 0 } < game.units.count { it.playerId == unit.playerId && it.health > 0 })
                            continue
                        debug.draw(
                            CustomData.Rect(
                                Vec2Float(i.toFloat(), j.toFloat()),
                                Vec2Float(1f, 1f),
                                ColorFloat(155f, 155f, 155f, 110f)
                            )
                        )
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun canShutTeammate(unit: Unit, game: Game, aim: Unit?, debug: Debug): Boolean {
        if (aim == null)
            return false
        val aimCenter =
            Vec2Double(aim.position.x, aim.position.y + aim.size.y / 2)
        val unitCenter = Vec2Double(unit.position.x, unit.position.y + unit.size.y / 2)

        val teammate = game.units.firstOrNull { it.playerId == unit.playerId && it.id != unit.id && it.health > 0 }
        teammate?.let {
            val distanceXTA = abs(it.position.x - aimCenter.x)
            val distanceXUT = abs(it.position.x - unit.position.x)
            val distanceXUA = abs(aimCenter.x - unit.position.x)
            if (distanceXUA < distanceXUT || distanceXUA < distanceXTA)
                return false

            val distanceYTA = abs(it.position.y - aimCenter.y)
            val distanceYUT = abs(it.position.y - unit.position.y)
            val distanceYUA = abs(aimCenter.y - unit.position.y)
            if (distanceYUA < distanceYUT || distanceYUA < distanceYTA)
                return false

            if (directrixTileCollision(
                    Vec2Double(it.position.x - it.size.x / 2, it.position.y),
                    it.size,
                    unitCenter,
                    aimCenter,
                    unit.weapon!!.spread,
                    game.properties.weaponParams[unit.weapon!!.typ]?.bullet?.size
                        ?: 0.0,
                    debug
                )
            )
                return true

        }
        return false
    }

    private fun directrixTileCollision(
        square: Vec2Double,
        squareSize: Vec2Double,
        unitCenter: Vec2Double,
        aimCenter: Vec2Double,
        deltaAngle: Double,
        bulletSize: Double,
        debug: Debug
    ): Boolean {

        val bulletSqr = bulletSize * bulletSize
        if (distanceSqr(Vec2Double(square.x, square.y), unitCenter) <= bulletSqr ||
            distanceSqr(Vec2Double(square.x + squareSize.x, square.y), unitCenter) <= bulletSqr ||
            distanceSqr(Vec2Double(square.x, square.y + squareSize.y), unitCenter) <= bulletSqr ||
            distanceSqr(Vec2Double(square.x + squareSize.x, square.y + squareSize.y), unitCenter) <= bulletSqr
        ) {
            return true
        }

        val actualAlpha = atan2(aimCenter.y - unitCenter.y, aimCenter.x - unitCenter.x)

//        debug.draw(CustomData.Log("spread + alpha $deltaAngle  , $actualAlpha"))

        val corner1 = Vec2Double(square.x - bulletSize / 2, square.y - bulletSize / 2)
        var alpha1 = atan2(corner1.y - unitCenter.y, corner1.x - unitCenter.x)
        alpha1 = when {
            (actualAlpha < 0 && alpha1 > 0 && aimCenter.x < unitCenter.x) -> alpha1 - 2 * kotlin.math.PI
            (actualAlpha > 0 && alpha1 < 0 && aimCenter.x < unitCenter.x) -> alpha1 + 2 * kotlin.math.PI
            else -> alpha1
        }
        if (alpha1 <= actualAlpha + deltaAngle && alpha1 >= actualAlpha - deltaAngle)
            return true

        val corner2 = Vec2Double(square.x - bulletSize / 2, square.y + squareSize.y + bulletSize / 2)
        var alpha2 = atan2(corner2.y - unitCenter.y, corner2.x - unitCenter.x)
        alpha2 = when {
            (actualAlpha < 0 && alpha2 > 0 && aimCenter.x < unitCenter.x) -> alpha2 - 2 * kotlin.math.PI
            (actualAlpha > 0 && alpha2 < 0 && aimCenter.x < unitCenter.x) -> alpha2 + 2 * kotlin.math.PI
            else -> alpha2
        }

        if (alpha2 <= actualAlpha + deltaAngle && alpha2 >= actualAlpha - deltaAngle)
            return true

        val corner3 = Vec2Double(square.x + squareSize.x + bulletSize / 2, square.y - bulletSize / 2)
        var alpha3 =
            atan2(corner3.y - unitCenter.y, corner3.x - unitCenter.x)
        alpha3 = when {
            (actualAlpha < 0 && alpha3 > 0 && aimCenter.x < unitCenter.x) -> alpha3 - 2 * kotlin.math.PI
            (actualAlpha > 0 && alpha3 < 0 && aimCenter.x < unitCenter.x) -> alpha3 + 2 * kotlin.math.PI
            else -> alpha3
        }

        if (alpha3 <= actualAlpha + deltaAngle && alpha3 >= actualAlpha - deltaAngle)
            return true

        val corner4 = Vec2Double(square.x + squareSize.x + bulletSize / 2, square.y + squareSize.y + bulletSize / 2)
        var alpha4 =
            atan2(corner4.y - unitCenter.y, corner4.x - unitCenter.x)

        alpha4 = when {
            (actualAlpha < 0 && alpha4 > 0 && aimCenter.x < unitCenter.x) -> alpha4 - 2 * kotlin.math.PI
            (actualAlpha > 0 && alpha4 < 0 && aimCenter.x < unitCenter.x) -> alpha4 + 2 * kotlin.math.PI
            else -> alpha4
        }

        if (alpha4 <= actualAlpha + deltaAngle && alpha4 >= actualAlpha - deltaAngle)
            return true
        val s1 = sign(actualAlpha - alpha1)
        if (s1 != sign(actualAlpha - alpha2) || s1 != sign(actualAlpha - alpha3) || s1 != sign(actualAlpha - alpha4))
            return true

        return false
    }

    private fun unitTargetCollisionDetected(
        unitPosition: Vec2Double,
        unitSize: Vec2Double,
        targetPosition: Vec2Double,
        targetSize: Vec2Double
    ) =
        abs(unitPosition.x - targetPosition.x) < abs(unitSize.x / 2 + targetSize.x / 2) &&
                abs(unitPosition.y - targetPosition.y) < abs(targetSize.y)
}

data class Vec2Int(var x: Int = 0, var y: Int = 0)

data class UnitMovement(
    val pos: Vec2Double, val jumpAllowed: Boolean, val boostJump: Boolean = false,
    val jumpTick: Int = -1, val boostJumpTick: Int = -1
)

data class ProbablyAction(
    val action: model.UnitAction,
    val probablyPositionAfterAction: UnitMovement,
    val currentTick: Int,
    val parentPA: ProbablyAction?
)

data class TileMarked(val type: Tile, var mark: Int = 0)
data class Node(
    val position: Vec2Int,
    var jumpTile: Int = -1,
    var boostJumpTile: Int = -1,
    val gen: Int = 0,
    var goingToEnemy: Boolean = false,
    val parentNode: Node? = null
) {
    fun parentLessClone(): Node {
        return Node(
            Vec2Int(this.position.x, this.position.y),
            jumpTile = this.jumpTile,
            boostJumpTile = this.boostJumpTile,
            goingToEnemy = this.goingToEnemy
        )
    }
}

data class SquareObject(val pos: Vec2Double, val size: Vec2Double, val isEnemy: Boolean) {
    override fun toString(): String {
        return "pos: ${pos.x}:${pos.y} , size: ${size.x}:${size.y}, isEnemy $isEnemy"
    }
}

class Route {
    private val routeNodes = ArrayList<Node>()
    private var targetLootItem: LootBox? = null
    lateinit var targetNode: Node
    lateinit var default: Vec2Double

    private var prevPoint: Node? = null
    private var nextPoint: Node? = null
    private lateinit var unitPrevPosition: Vec2Double

    var goToDefault = false

    fun getTargetLootItem() = targetLootItem
    fun getPrevPoint() = prevPoint
    fun getNextPoint() = nextPoint
    fun getUnitPrevPosition() = unitPrevPosition

    fun isEmpty() = routeNodes.isEmpty()
    fun clearRoute() {
        prevPoint = null
        routeNodes.clear()
        targetLootItem = null
    }

    fun addRouteNodes(nodes: List<Node>) {
        if (nodes.isNotEmpty()) {
            routeNodes.addAll(nodes)
            targetNode = nodes[nodes.size - 1]
        }
    }

    fun getNextStep(unit: Unit, game: Game): Vec2Double? {
        if (routeNodes.isNotEmpty() &&
            routeNodes[0].position.x <= unit.position.x &&
            routeNodes[0].position.x + 1 >= unit.position.x &&
            routeNodes[0].position.y <= unit.position.y &&
            routeNodes[0].position.y + 1 >= unit.position.y
        ) {
            prevPoint = routeNodes[0]
            if (routeNodes.size >= 2)
                nextPoint = routeNodes[1]
            if (routeNodes.size >= 3 &&
                routeNodes[2].position.x == routeNodes[0].position.x &&
                game.level.tiles[routeNodes[1].position.x][routeNodes[1].position.y].discriminant != Tile.LADDER.discriminant
            ) {
                routeNodes[1].position.x = routeNodes[0].position.x
                nextPoint?.position?.x = routeNodes[0].position.x
            }

            routeNodes.removeAt(0)
        }

        // drawRoute(route[unit.id], debug)

        return if (routeNodes.isNotEmpty()) {
            Vec2Double(
                routeNodes[0].position.x + 0.5,
                routeNodes[0].position.y.toDouble()
            )
        } else {
            clearRoute()
            null
        }
    }

    fun setPrevPosition(position: Vec2Double) {
        unitPrevPosition = position
    }

    fun setNextPoint(node: Node) {
        nextPoint = node
    }

    fun setTargetLootItem(item: LootBox) {
        targetLootItem = item
    }
}