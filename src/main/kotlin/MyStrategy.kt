import model.*
import model.Unit
import java.util.concurrent.LinkedBlockingQueue
import kotlin.collections.ArrayList
import kotlin.math.abs
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
    private lateinit var unitMovement: UnitMovement
    private var unitHeight = 0.0
    private var index = 0
    private val route = ArrayList<Vec2Double>()
    private val lootRoutes = HashMap<LootBox, ArrayList<Vec2Double>>()
    //    private var pa: ArrayList<ProbablyAction>? = null
    private var prevPoint: Vec2Double? = null

    fun getAction(unit: model.Unit, game: Game, debug: Debug): UnitAction {
        var nearestEnemy: model.Unit? = null

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
        }

        val level = ArrayList<ArrayList<TileMarked>>()
        for (i in game.level.tiles.indices) {
            level.add(ArrayList())
            for (element in game.level.tiles[i]) {
                level[i].add(TileMarked(element))
            }
        }

        for (other in game.units) {
            if (other.playerId != unit.playerId) {
                if (nearestEnemy == null || distanceSqr(
                        unit.position,
                        other.position
                    ) < distanceSqr(unit.position, nearestEnemy.position)
                ) {
                    nearestEnemy = other
                }
            }
        }


        if (route.isEmpty()) {
            lootRoutes.clear()
            for (lootBox in game.lootBoxes) {
                val localRoute = ArrayList<Vec2Double>()
                buildPath(unit, SquareObject(lootBox.position, lootBox.size), level, debug, localRoute)
                localRoute.reverse()
                level.forEach { it.forEach { jt -> jt.mark = 0 } }
                lootRoutes[lootBox] = localRoute
            }
            var nearestWeapon: LootBox? = null
            var nearestHealthPack: LootBox? = null
            lootRoutes.forEach { lootbox, route ->
                if (lootbox.item is Item.Weapon) {
                    if (nearestWeapon == null || route.size < lootRoutes[nearestWeapon as LootBox]?.size ?: 0) {
                        nearestWeapon = lootbox
                    }
                }
                if (lootbox.item is Item.HealthPack) {
                    if (nearestHealthPack == null || route.size < lootRoutes[nearestHealthPack as LootBox]?.size ?: 0) {
                        nearestHealthPack = lootbox
                    }
                }
            }

            route.clear()
            route.addAll(
                when {
                    unit.health <= game.properties.unitMaxHealth - game.properties.healthPackHealth &&
                            nearestHealthPack != null -> lootRoutes[nearestHealthPack!!] ?: ArrayList()
                    unit.weapon == null && nearestWeapon != null -> lootRoutes[nearestWeapon!!] ?: ArrayList()
                    else -> {
                        val localRoute = ArrayList<Vec2Double>()
                        buildPath(
                            unit,
                            SquareObject(
                                nearestEnemy?.size ?: Vec2Double(0.0, 0.0),
                                nearestEnemy?.position ?: Vec2Double(0.0, 0.0)
                            ),
                            level,
                            debug,
                            localRoute
                        )
                        localRoute.reverse()
                        localRoute
                    }
                }
            )
        }

        if (route.isNotEmpty() &&
            route[0].x <= unit.position.x &&
            (route[0].x + 1) >= unit.position.x &&
            (route.size == 1 || route[1].x != route[0].x ||
                    (route[0].y <= unit.position.y &&
                            (route[0].y) + 1 >= unit.position.y
                            )
                    )
        ) {
            prevPoint = route[0]
            route.removeAt(0)
        }

//        if (game.currentTick == 0 || route.isEmpty()) {
//
//
//            buildPath(unit, targetObj, level, debug, route)
//            route.reverse()
////                pa = buildPathForNearestWeapon2(unit, nearestWeapon, game, debug, route)
////                pa?.reverse()
//
//        }

        if (route.isNotEmpty()) {
            route.forEach { step ->
                debug.draw(
                    CustomData.Rect(
                        Vec2Float(
                            step.x.toFloat() + 0.4f,
                            step.y.toFloat() + 0.4f
                        ),
                        Vec2Float(0.2f, 0.2f),
                        ColorFloat(111f, 111f, 111f, 255f)
                    )
                )
            }
        }

        val targetPos = if (route.isEmpty())
            nearestEnemy?.position ?: Vec2Double(0.5, 0.5)
        else
            Vec2Double(route[0].x + 0.5, route[0].y)

        var aim = Vec2Double(0.0, 0.0)
        if (nearestEnemy != null) {
            aim = Vec2Double(
                (nearestEnemy.position.x - unit.position.x) * 10,
                (nearestEnemy.position.y - unit.position.y) * 10
            )
        }

//        var action = if (index < pa?.size ?: -1) {
//            index++
//            pa?.get(index - 1)?.action
//
//        } else null

//        if (action == null) {
        val action = UnitAction()
//        var jump = targetPos.y > unit.position.y
//        if (targetPos.x > unit.position.x && game.level.tiles[(unit.position.x + 1).toInt()][(unit.position.y).toInt()] == Tile.WALL) {
//            jump = true
//        }
//        if (targetPos.x < unit.position.x && game.level.tiles[(unit.position.x - 1).toInt()][(unit.position.y).toInt()] == Tile.WALL) {
//            jump = true
//        }

        action.velocity = getVelocity(unit.position.x, targetPos.x)
        action.jump = targetPos.y > prevPoint?.y ?: 0.0 || targetPos.y > unit.position.y
        action.jumpDown = targetPos.y < prevPoint?.y ?: 31.0 && targetPos.y < unit.position.y

//        }
        action.aim = aim
//        action.reload = false

//        action.shoot = false
        if (nearestEnemy == null || unit.weapon == null) {
            action.shoot = false
        } else {
            action.shoot = shootAllowed(unit, nearestEnemy, game, debug)
            if (!action.shoot && unit.weapon?.params?.magazineSize != unit.weapon?.magazine) {
                action.reload = true
            }
        }
        action.swapWeapon = false
        action.plantMine = false

        if (!::unitMovement.isInitialized)
            unitMovement = UnitMovement(unit.position, unit.onGround)
        else
            unitMovement.apply {
                pos.x = unit.position.x
                pos.y = unit.position.y
            }

        unitMovement = getUnitMovement(unitMovement, unit.size, unit.id, action, game)
        debug.draw(
            CustomData.Rect(
                Vec2Float(unitMovement.pos.x.toFloat() - 0.2f, unitMovement.pos.y.toFloat() - 0.2f),
                Vec2Float(0.4f, 0.4f),
                ColorFloat(0f, 255f, 0f, 255f)
            )
        )
        debug.draw(
            CustomData.Rect(
                Vec2Float(unit.position.x.toFloat() - 0.1f, unit.position.y.toFloat() - 0.1f),
                Vec2Float(0.2f, 0.2f),
                ColorFloat(0f, 0f, 255f, 255f)
            )
        )

        return action
    }

    private fun buildPath(
        unit: Unit,
        target: SquareObject,
        level: ArrayList<ArrayList<TileMarked>>,
        debug: Debug,
        route: ArrayList<Vec2Double>
    ) {
        val nodes = LinkedBlockingQueue<Node>()

        val firstNode = Node(unit.position.x.toInt(), unit.position.y.toInt())
        if (objectsCollisionDetected(
                unit.position,
                unit.size,
                target.pos,
                target.size
            )
        ) {
            route.add(Vec2Double(firstNode.x.toDouble(), firstNode.y.toDouble()))
            return
        }

        nodes.add(firstNode)

        var r: Node? = null

        while (nodes.isNotEmpty() && r == null) {
            val n = nodes.poll()
            r = checkTiles(n, unit.size, target, level, debug, nodes)
        }

        r?.let {
            var qq = it
            do {
                route.add(Vec2Double(qq.x.toDouble(), qq.y.toDouble()))
                qq = qq.parentNode ?: return
            } while (qq != null)
        }
    }

    private fun checkTiles(
        currentNode: Node,
        unitSize: Vec2Double,
        target: SquareObject,
        level: ArrayList<ArrayList<TileMarked>>,
        debug: Debug,
        nodes: LinkedBlockingQueue<Node>
    ): Node? {

        val dx = target.pos.x - currentNode.x

        if (currentNode.jumpTile >= maxJumpTiles || currentNode.y < currentNode.parentNode?.y ?: 0)
            currentNode.jumpTile = -1
        if (currentNode.boostJumpTile >= maxBoostJumpTiles || currentNode.y < currentNode.parentNode?.y ?: 0)
            currentNode.boostJumpTile = -1

//        for (i in if (dx > 0) 0..1 else 2 downTo 1) {
        for (i in 0..2) {

            val x = when {
                i == 0 && currentNode.y > currentNode.parentNode?.y ?: 1 -> currentNode.x
                (i == 0 || i == 1 && currentNode.y > currentNode.parentNode?.y ?: 31) && dx > 0 ||
                        i == 2 && dx < 0
                -> currentNode.x + 1
                (i == 0 || i == 1 && currentNode.y > currentNode.parentNode?.y ?: 31) && dx < 0 ||
                        i == 2 && dx > 0 -> currentNode.x - 1
                else -> currentNode.x

            }

            for (j in 0..2) {

                val y = when (j) {
                    0 -> currentNode.y
                    1 -> currentNode.y + 1
                    else -> currentNode.y - 1
                }

                if (currentNode.y == y && !canGoHorizontalTile(currentNode, level, x))
                    continue

                if (currentNode.y == y - 1 && !canGoUpTile(currentNode, level, x)) {
                    continue
                }

                if (x >= 0 &&
                    x < level.size &&
                    y >= 0 &&
                    y < level[0].size - 1 &&
                    level[x][y].type != Tile.WALL &&
                    level[x][y + 1].type != Tile.WALL &&
                    level[x][y].mark < 10
                ) {
                    level[x][y].mark++

                    val node = Node(
                        x, y,
                        if (currentNode.jumpTile >= 0 && y == currentNode.y + 1) currentNode.jumpTile + 1 else currentNode.jumpTile,
                        if (currentNode.boostJumpTile >= 0 && y == currentNode.y + 1) currentNode.boostJumpTile + 1 else currentNode.boostJumpTile,
                        currentNode
                    )
                    nodes.add(node)
                    if (abs(node.x + unitSize.x / 2 - target.pos.x) < (unitSize.x / 2 + target.size.x / 2) &&
                        abs(node.y + unitSize.y / 2 - target.pos.y) < (target.size.y / 2 + unitSize.y / 2)
                    )
                        return node
                }
            }
        }

        return null
    }

    private fun canGoUpTile(currentNode: Node, level: ArrayList<ArrayList<TileMarked>>, newX: Int): Boolean {
        // Check current going down
        if (currentNode.y < currentNode.parentNode?.y ?: 0 &&
            level[currentNode.x][currentNode.y].type != Tile.LADDER &&
            currentNode.y > 0 &&
            level[currentNode.x][currentNode.y - 1].type == Tile.EMPTY
        )
            return false
        if ((currentNode.y == 0 ||
                    level[currentNode.x][currentNode.y - 1].type != Tile.EMPTY) &&
            currentNode.boostJumpTile + currentNode.jumpTile == -2
        ) {
            currentNode.boostJumpTile = 0
            currentNode.jumpTile = 0
        }
        if (currentNode.y >= (level[0].size - unitHeight).toInt() ||
            level[currentNode.x][currentNode.y + 2].type == Tile.WALL ||
            level[currentNode.x][currentNode.parentNode?.y ?: currentNode.y + 2].type == Tile.WALL ||
            currentNode.jumpTile == -1 || currentNode.boostJumpTile == -1
        )
            return false
        return true
    }

    private fun canGoHorizontalTile(currentNode: Node, level: ArrayList<ArrayList<TileMarked>>, newX: Int): Boolean {
        return currentNode.y == 0 ||
                (level[currentNode.x][currentNode.y - 1].type != Tile.EMPTY &&
                        level[newX][currentNode.y - 1].type != Tile.EMPTY)
    }

    private fun getVelocity(xCurrent: Double, xTarget: Double): Double {
        val dx = (xTarget - xCurrent)
        return if (abs(dx) > maxDXPerTick) {
            dx * 10
        } else if (abs(dx) < 0.001) {
            0.0001 // fix for infinite moving
        } else {
            dx * ticksPerSec
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

    private fun shootAllowed(
        unit: model.Unit,
        nearestEnemy: model.Unit,
        game: Game,
        debug: Debug
    ): Boolean {
        val unitCenter = Vec2Double(unit.position.x, unit.position.y + unit.size.y / 2)
        val enemyCenter =
            Vec2Double(nearestEnemy.position.x, nearestEnemy.position.y + nearestEnemy.size.y / 2)

        var xl = unitCenter.x
        var xr = enemyCenter.x
        if (unitCenter.x > enemyCenter.x) {
            xl = enemyCenter.x
            xr = unitCenter.x
        }

        var yt = enemyCenter.y
        var yb = unitCenter.y
        if (unitCenter.y > enemyCenter.y) {
            yb = enemyCenter.y
            yt = unitCenter.y
        }

        val indexLeft = xl.toInt()
        val indexRight = xr.toInt()
        val indexBottom = yb.toInt()
        val indexTop = yt.toInt()

        for (i in if (unitCenter.x > enemyCenter.x) indexRight downTo indexLeft else indexLeft..indexRight) {
            for (j in indexBottom..indexTop) {
                if (game.level.tiles[i][j].discriminant == Tile.WALL.discriminant) {
                    if (xl.toInt() == xr.toInt() || yt.toInt() == yb.toInt() ||
                        directrixTileCollision(
                            i,
                            j,
                            unitCenter,
                            enemyCenter,
                            unit.weapon!!.spread,
                            game.properties.weaponParams[unit.weapon!!.typ]?.bullet?.size
                                ?: 0.0
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
            val left =
                if (enemyCenter.x < unitCenter.x) (unit.position.x - (unit.size.x / 2) - r).toInt() else unitCenter.x.toInt()
            val right =
                if (enemyCenter.x >= unitCenter.x) (unit.position.x + (unit.size.x / 2) + r).toInt() else unitCenter.x.toInt()
            val top =
                if (enemyCenter.y >= unitCenter.y) (unit.position.y + (unit.size.y) + r).toInt() else unit.position.y.toInt()
            val bottom =
                if (enemyCenter.y < unitCenter.y) (unit.position.y - r).toInt() else unit.position.y.toInt()

            for (i in left..right) {
                for (j in bottom..top) {
                    if ((i < 0 || j < 0 || i >= game.level.tiles.size || j >= game.level.tiles[0].size || game.level.tiles[i][j].discriminant == Tile.WALL.discriminant)
                        && directrixTileCollision(
                            i,
                            j,
                            unitCenter,
                            enemyCenter,
                            unit.weapon!!.spread,
                            game.properties.weaponParams[WeaponType.ROCKET_LAUNCHER]?.bullet?.size
                                ?: 0.0
                        )
                        && (dam > unit.health) && (dam < nearestEnemy.health || nearestEnemy.health > unit.health)
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
        return true
    }

    private fun directrixTileCollision(
        tileXIndex: Int,
        tileYIndex: Int,
        unitCenter: Vec2Double,
        aimCenter: Vec2Double,
        deltaAngle: Double,
        bulletSize: Double
    ): Boolean {

        val bulletSqr = bulletSize * bulletSize
        if (distanceSqr(Vec2Double(tileXIndex.toDouble(), tileYIndex.toDouble()), unitCenter) <= bulletSqr ||
            distanceSqr(Vec2Double((tileXIndex + 1).toDouble(), tileYIndex.toDouble()), unitCenter) <= bulletSqr ||
            distanceSqr(Vec2Double((tileXIndex).toDouble(), (tileYIndex + 1).toDouble()), unitCenter) <= bulletSqr ||
            distanceSqr(Vec2Double((tileXIndex + 1).toDouble(), (tileYIndex + 1).toDouble()), unitCenter) <= bulletSqr
        ) {
            return false
        }

        val actualAlpha = kotlin.math.atan2(aimCenter.y - unitCenter.y, aimCenter.x - unitCenter.x)

        val corner1 = Vec2Double(tileXIndex - bulletSize / 2, tileYIndex - bulletSize / 2)
        val alpha1 = kotlin.math.atan2(corner1.y - unitCenter.y, corner1.x - unitCenter.x)
        if (alpha1 <= actualAlpha + deltaAngle && alpha1 >= actualAlpha - deltaAngle)
            return true

        val corner2 = Vec2Double(tileXIndex - bulletSize / 2, tileYIndex + 1 + bulletSize / 2)
        val alpha2 = kotlin.math.atan2(corner2.y - unitCenter.y, corner2.x - unitCenter.x)
        if (alpha2 <= actualAlpha + deltaAngle && alpha2 >= actualAlpha - deltaAngle)
            return true

        val corner3 = Vec2Double(tileXIndex + 1 + bulletSize / 2, tileYIndex - bulletSize / 2)
        val alpha3 =
            kotlin.math.atan2(corner3.y - unitCenter.y, corner3.x - unitCenter.x)
        if (alpha3 <= actualAlpha + deltaAngle && alpha3 >= actualAlpha - deltaAngle)
            return true

        val corner4 = Vec2Double(tileXIndex + 1 + bulletSize / 2, tileYIndex + 1 + bulletSize / 2)
        val alpha4 =
            Math.atan2(corner4.y - unitCenter.y, corner4.x - unitCenter.x)
        if (alpha4 <= actualAlpha + deltaAngle && alpha4 >= actualAlpha - deltaAngle)
            return true
        val s1 = sign(actualAlpha - alpha1)
        if (s1 != sign(actualAlpha - alpha2) || s1 != sign(actualAlpha - alpha3) || s1 != sign(actualAlpha - alpha4))
            return true

        return false
    }

    fun objectsCollisionDetected(pos1: Vec2Double, size1: Vec2Double, pos2: Vec2Double, size2: Vec2Double) =
        abs(pos1.x - pos2.x) < abs(size1.x / 2 + size2.x / 2) && abs(pos1.y - pos2.y) < abs(size1.y / 1 + size2.y / 2)
}

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
    val x: Int,
    val y: Int,
    var jumpTile: Int = -1,
    var boostJumpTile: Int = -1,
    val parentNode: Node? = null
)

data class SquareObject(val pos: Vec2Double, val size: Vec2Double)


//    private fun buildPathForNearestWeapon2(
//        unit: Unit,
//        nearestWeapon: LootBox,
//        game: Game,
//        debug: Debug,
//        route: ArrayList<Node>
//    ): ArrayList<ProbablyAction>? {
//
//        val vel = abs(getVelocity(unit.position.x, nearestWeapon.position.x))
//
//        val nodes = LinkedBlockingQueue<ProbablyAction>()
//
//        var currentStep = route.firstOrNull { step ->
//            step.x <= unit.position.x &&
//                    step.x + 1 > unit.position.x &&
//                    step.y <= unit.position.y && step.y + 1 > unit.position.y
//        }
//        var nextStep = if (currentStep != null && route.indexOf(currentStep) != route.size - 1) {
//            route[route.indexOf(currentStep) + 1]
//        } else null
//
//        for (i in 2 downTo 0) {
//            if (nextStep != null && currentStep != null) {
//                if (i == 0 && nextStep.x > currentStep.x) {
//                    continue
//                }
//                if (i == 2 && nextStep.x < currentStep.x) {
//                    continue
//                }
//            } else continue
//            for (j in 0..2) {
//
//                if (j == 2 && currentStep.y <= nextStep.y && currentStep.x != nextStep.x)
//                    continue
//                if (j == 0 && currentStep.y >= nextStep.y && currentStep.x != nextStep.x)
//                    continue
//                val act = model.UnitAction().apply {
//                    velocity = (i - 1) * vel
//                    jump = j == 0
//                    jumpDown = j == 2
//                }
//
//                val pa = ProbablyAction(
//                    act, getUnitMovement(
//                        UnitMovement(
//                            unit.position,
//                            (unit.position.y - unit.position.y.toInt().toDouble()) < jumpPerTick
//                        ),
//                        unit.size, unit.id,
//                        act, game
//                    ), 0,
//                    null
//                )
//                if (pa.probablyPositionAfterAction.pos.x != unit.position.x || pa.probablyPositionAfterAction.pos.y != unit.position.y)
//                    nodes.add(pa)
//            }
//        }
//
//        var r: ProbablyAction? = null
//        var c = 0
//
//        while (nodes.isNotEmpty() && r == null) {
//            val n = nodes.poll()
//            currentStep = route.firstOrNull { step ->
//                step.x <= n.probablyPositionAfterAction.pos.x &&
//                        step.x + 1 > n.probablyPositionAfterAction.pos.x &&
//                        step.y <= n.probablyPositionAfterAction.pos.y && step.y + 1 > n.probablyPositionAfterAction.pos.y
//            }
//            nextStep = if (currentStep != null && route.indexOf(currentStep) != route.size - 1) {
//                route[route.indexOf(currentStep) + 1]
//            } else null
//
//            for (i in 2 downTo 0) {
//                if (nextStep != null && currentStep != null) {
//                    if (i < 2 && currentStep.x < nextStep.x) {
//                        continue
//                    }
//                    if (i > 0 && currentStep.x > nextStep.x) {
//                        continue
//                    }
//
//                } else continue
//
//                for (j in 0..2) {
//                    if (j == 2 && currentStep.y <= nextStep.y && currentStep.x != nextStep.x)
//                        continue
//                    if (j == 0 && currentStep.y >= nextStep.y && currentStep.x != nextStep.x)
//                        continue
//
//                    if ((currentStep.x != nextStep.x || currentStep.y != nextStep.y) && i == 1 && j == 1) {
//                        continue
//                    }
//                    val act = model.UnitAction().apply {
//                        velocity = (i - 1) * vel
//                        jump = j == 0
//                        jumpDown = j == 2
//                    }
//
//                    val pa = ProbablyAction(
//                        act, getUnitMovement(
//                            n.probablyPositionAfterAction, unit.size,
//                            unit.id, act, game
//                        ), n.currentTick + 1, n
//                    )
//                    if (nodes.none { it.probablyPositionAfterAction.pos.x == pa.probablyPositionAfterAction.pos.x && it.probablyPositionAfterAction.pos.y == pa.probablyPositionAfterAction.pos.y })
//                        nodes.add(pa)
//                    if (objectsCollisionDetected(
//                            pa.probablyPositionAfterAction.pos,
//                            nearestWeapon.position,
//                            unit.size,
//                            nearestWeapon.size
//                        )
//                    )
//                        r = pa
//                }
//            }
//        }
//        nodes.clear()
//
//        r?.let {
//            val result = ArrayList<ProbablyAction>()
//
//            var qq = it
//            do {
//                result.add(qq)
//                debug.draw(
//                    CustomData.Rect(
//                        Vec2Float(
//                            qq.probablyPositionAfterAction.pos.x.toFloat() + 0.4f,
//                            qq.probablyPositionAfterAction.pos.y.toFloat() + 0.4f
//                        ),
//                        Vec2Float(0.2f, 0.2f),
//                        ColorFloat(111f, 111f, 111f, 255f)
//                    )
//                )
//                qq = qq.parentPA ?: return@let
//            } while (qq.parentPA != null)
//            return result
//        }
//
//        return null
//    }