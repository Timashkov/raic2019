import model.*
import model.Unit
import java.lang.Math.abs
import java.lang.Math.atan2
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.sign
import kotlin.math.tan

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
    private var ticksPerSec = 0.0
    private lateinit var unitMovement: UnitMovement
    private var pa: ProbablyAction? = null

    fun getAction(unit: model.Unit, game: Game, debug: Debug): UnitAction {
        var nearestEnemy: model.Unit? = null

        if (boostJumpPerTick == 0.0) {
            maxDXPerTick = game.properties.unitMaxHorizontalSpeed / game.properties.ticksPerSecond
            jumpPerTick = game.properties.unitJumpSpeed / game.properties.ticksPerSecond
            boostJumpPerTick = game.properties.jumpPadJumpSpeed / game.properties.ticksPerSecond

            maxJumpTick = (game.properties.jumpPadJumpTime * game.properties.ticksPerSecond).toInt()
            maxBoostJumpTick = (game.properties.jumpPadJumpTime * game.properties.ticksPerSecond).toInt()
            ticksPerSec = game.properties.ticksPerSecond
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
        var nearestWeapon: LootBox? = null
        var nearestHealthPack: LootBox? = null
        for (lootBox in game.lootBoxes) {
            val item = lootBox.item
            if (item is Item.Weapon) {
                if (nearestWeapon == null || distanceSqr(
                                unit.position,
                                lootBox.position
                        ) < distanceSqr(unit.position, nearestWeapon.position)
                ) {
                    nearestWeapon = lootBox
                }
            }
            if (lootBox.item is Item.HealthPack) {
                if (nearestHealthPack == null ||
                        distanceSqr(unit.position, lootBox.position) < distanceSqr(unit.position, nearestHealthPack.position)
                ) {
                    nearestHealthPack = lootBox
                }
            }
        }


        if (game.currentTick == 0) {
            nearestWeapon?.let {
                val route = ArrayList<Node>()
                val level = ArrayList<ArrayList<TileMarked>>()
                for (i in game.level.tiles.indices) {
                    level.add(ArrayList())
                    for (element in game.level.tiles[i]) {
                        level[i].add(TileMarked(element))
                    }
                }
                buildPathForNearestWeapon1(unit, nearestWeapon, level, debug, route)
                route.reverse()
//                buildPathForNearestWeapon2(unit, nearestWeapon, game, debug, route)

            }
        }
        val targetPos = when {
            unit.health <= game.properties.unitMaxHealth - game.properties.healthPackHealth && nearestHealthPack != null -> nearestHealthPack.position
            unit.weapon == null && nearestWeapon != null -> nearestWeapon.position
            nearestEnemy != null -> nearestEnemy.position
            else -> unit.position
        }

        var aim = Vec2Double(0.0, 0.0)
        if (nearestEnemy != null) {
            aim = Vec2Double(
                    (nearestEnemy.position.x - unit.position.x) * 10,
                    (nearestEnemy.position.y - unit.position.y) * 10
            )
//            unit.weapon?.let {
//                debug.draw(
//                        CustomData.Log(
//                                "Weapon params: lft: ${it.lastFireTick} la: ${it.lastAngle} spr: ${it
//                                        .spread} mag: ${it.magazine} ft:${it.fireTimer} ws:${it.wasShooting}"
//                        )
//                )
//            }
        }
        var action = pa?.action
        pa = pa?.parentPA
        if (action == null) {
            action = UnitAction()
            var jump = targetPos.y > unit.position.y
            if (targetPos.x > unit.position.x && game.level.tiles[(unit.position.x + 1).toInt()][(unit.position.y).toInt()] == Tile.WALL) {
                jump = true
            }
            if (targetPos.x < unit.position.x && game.level.tiles[(unit.position.x - 1).toInt()][(unit.position.y).toInt()] == Tile.WALL) {
                jump = true
            }


            action.velocity = getVelocity(unit.position.x, targetPos.x)
            action.jump = jump
            action.jumpDown = !jump
            action.aim = aim
//        action.reload = false

            if (nearestEnemy == null || unit.weapon == null) {
                action.shoot = false
            } else {
                action.shoot = shootAllowed(unit, nearestEnemy, game, debug)
            }
            action.swapWeapon = false
            action.plantMine = false
        }
//        debug.draw(CustomData.Log("maxSp: ${game.properties.unitMaxHorizontalSpeed} maxJS:${game.properties
//                .unitJumpSpeed} jt:${game.properties.unitJumpTime}  ticksPS${game.properties.ticksPerSecond} dt:${game.properties.updatesPerTick}"))

        if (!::unitMovement.isInitialized)
            unitMovement = UnitMovement(unit.position, unit.onGround)
        else
            unitMovement.apply {
                pos.x = unit.position.x
                pos.y = unit.position.y
            }

        unitMovement = getUnitMovement(unitMovement, unit.size, unit.id, action, game)
//        debug.draw(
//                CustomData.Log(
//                        "Action: pos:${unit.position.x}:${unit
//                                .position.y} jump:${action.jump} jumptick ${unitMovement.jumpTick} maxjumptick $maxJumpTick boostJumpTick " +
//                                "${unitMovement.boostJumpTick} maxboostjumptick $maxBoostJumpTick onGround ${unit
//                                        .onGround}  " +
//                                "jumpDown: ${action.jumpDown}"
//                )
//        )
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

    private fun buildPathForNearestWeapon2(unit: Unit, nearestWeapon: LootBox, game: Game, debug: Debug, route: ArrayList<Node>): ProbablyAction? {

        val vel = getVelocity(unit.position.x, nearestWeapon.position.x)

        val nodes = LinkedBlockingQueue<ProbablyAction>()
        for (i in 2 downTo 0) {
            for (j in 0..2) {
                val act = model.UnitAction().apply {
                    velocity = (i - 1) * vel
                    jump = j == 0
                    jumpDown = j == 2
                }
                val pa = ProbablyAction(act, getUnitMovement(UnitMovement(unit.position, unit.onGround),
                        unit.size, unit.id,
                        act, game), 0,
                        null)
                nodes.add(pa)
            }
        }

        var r: ProbablyAction? = null
        var c = 0

        while (nodes.isNotEmpty() && r == null) {
            val n = nodes.poll()
            println("nodes size ${nodes.size}")

            for (i in 2 downTo 0) {
                for (j in 0..2) {
                    if (n.action.velocity == vel * (1 - i) && ((n.action.jump && j == 2) || (n.action.jumpDown && j == 1)))
                        continue
                    val act = model.UnitAction().apply {
                        velocity = (i - 1) * vel
                        jump = j == 0
                        jumpDown = j == 2
                    }

                    val pa = ProbablyAction(act, getUnitMovement(n.probablyPositionAfterAction, unit.size,
                            unit.id, act, game), 0, n)
                    println("pos after PA ${pa.probablyPositionAfterAction.pos.x}:${pa.probablyPositionAfterAction.pos.y}")
                    if (nodes.any { it.probablyPositionAfterAction.pos.x == pa.probablyPositionAfterAction.pos.x && it.probablyPositionAfterAction.pos.y == pa.probablyPositionAfterAction.pos.y })
                        continue
                    if ((pa.probablyPositionAfterAction.pos.x - nearestWeapon.position.x) < (unit.size.x / 2 + nearestWeapon.size.x / 2) &&
                            (pa.probablyPositionAfterAction.pos.y - nearestWeapon.position.y < unit.size.y / 2 + nearestWeapon.size.y / 2))
                        r = pa
                    nodes.add(pa)

                }
            }
        }
        nodes.clear()

        r?.let {
            var qq = it
            do {
                debug.draw(CustomData.Rect(
                        Vec2Float(qq.probablyPositionAfterAction.pos.x.toFloat() - 0.1f,
                                qq.probablyPositionAfterAction.pos.y.toFloat() - 0.1f),
                        Vec2Float(0.2f, 0.2f),
                        ColorFloat(111f, 111f, 111f, 255f)
                ))
                qq = qq.parentPA ?: return@let
            } while (qq.parentPA != null)
        }

        return r
    }

    data class Node(val x: Int, val y: Int, val parentNode: Node? = null)

    private fun buildPathForNearestWeapon1(unit: Unit, nearestWeapon: LootBox, level: ArrayList<ArrayList<TileMarked>>, debug: Debug, route: ArrayList<Node>) {
        val nodes = LinkedBlockingQueue<Node>()

        nodes.add(Node(unit.position.x.toInt(), unit.position.y.toInt()))

        var r: Node? = null

        while (nodes.isNotEmpty() && r == null) {
            val n = nodes.poll()
            r = checkTiles(n, unit.size, nearestWeapon, level, debug, nodes)
        }

        r?.let {
            var qq = it
            do {
                route.add(qq)
                debug.draw(CustomData.Rect(
                        Vec2Float(qq.x.toFloat() - 0.1f,
                                qq.y.toFloat() - 0.1f),
                        Vec2Float(0.2f, 0.2f),
                        ColorFloat(111f, 111f, 111f, 255f)
                ))
                qq = qq.parentNode ?: return
            } while (qq.parentNode != null)
        }
    }

    private fun checkTiles(currentNode: Node, unitSize: Vec2Double, target: LootBox, level: ArrayList<ArrayList<TileMarked>>, debug: Debug, nodes: LinkedBlockingQueue<Node>): Node? {

        val dx = target.position.x - currentNode.x

        for (i in if (dx > 0) 0..2 else 2 downTo 0) {
            for (j in 0..2) {
                val x = currentNode.x - i + 1
                val y = currentNode.y + j - 1
                if (x >= 0 && x < level.size && y >= 0 && y <= level[0].size && level[x][y].type != Tile.WALL && !level[x][y].mark) {
                    level[x][y].mark = true
                    val node = Node(x, y, currentNode)
                    nodes.add(node)
                    if (node.x == target.position.x.toInt() && node.y == target.position.y.toInt())
                        return node
                }
            }
        }

        return null
    }

    private fun getVelocity(xCurrent: Double, xTarget: Double): Double {
        val dx = (xTarget - xCurrent)
        return if (abs(dx) > maxDXPerTick) {
            dx * 10
        } else {
            dx * ticksPerSec
        }
    }


    private fun getUnitMovement(sourceMovement: UnitMovement,
                                unitSize: Vec2Double,
                                unitId: Int,
                                act: UnitAction,
                                game: Game): UnitMovement {
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

        /*удариться головой*/

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
                if ((pos.y + unitSize.y).toInt() >= game.level.tiles[0].size) {
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

                if ((pos.y + unitSize.y).toInt() >= game.level.tiles[0].size) {
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

                    return UnitMovement(pos.apply { y -= jumpPerTick }, jumpAllowed, boostJump = false, boostJumpTick = boostJumpTick)
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
                                tileRight in arrayOf(Tile.WALL, Tile.PLATFORM) && (pos.y - pos.y.toInt().toDouble()) <
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

        return UnitMovement(pos, jumpAllowed, jumpTick = jumpTick, boostJump = boostJumpTick in 1..maxBoostJumpTick, boostJumpTick = boostJumpTick)
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
                        ) && y - yz.toDouble() < jumpPerTick && y >= yz.toDouble()
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

    private fun shootAllowed(unit: model.Unit, nearestEnemy: model.Unit, game: Game, debug: Debug): Boolean {
        val unitCenter = Vec2Double(unit.position.x, unit.position.y + unit.size.y / 2)
        val enemyCenter = Vec2Double(nearestEnemy.position.x, nearestEnemy.position.y + nearestEnemy.size.y / 2)

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

        //drawing section
        val K = (unitCenter.y - enemyCenter.y) / (unitCenter.x - enemyCenter.x)

        val Xt = if (unitCenter.x > enemyCenter.x) 0 else game.level.tiles.size - 1
        val Yt = K * (Xt - unitCenter.x) + unitCenter.y

        debug.draw(
                CustomData.Line(
                        Vec2Float(unitCenter.x.toFloat(), unitCenter.y.toFloat()),
                        Vec2Float(Xt.toFloat(), Yt.toFloat()),
                        0.1f,
                        ColorFloat(10f, 10f, 10f, 10f)
                )
        )

        val debugLA = unit.weapon?.lastAngle ?: 0.0
        val debugMin = (unit.weapon?.lastAngle ?: 0.0) - (unit.weapon?.spread ?: 0.0)
        val debugMax = (unit.weapon?.lastAngle ?: 0.0) + (unit.weapon?.spread ?: 0.0)

        val lastAngleK = tan(unit.weapon?.lastAngle ?: 0.0)
        val anglePlusK = tan((unit.weapon?.lastAngle ?: 0.0) + (unit.weapon?.spread ?: 0.0))
        val angleMinusK = tan((unit.weapon?.lastAngle ?: 0.0) - (unit.weapon?.spread ?: 0.0))

        debug.draw(CustomData.Log("Angles: $debugLA:$debugMin$debugMax , K:$lastAngleK:$angleMinusK:$anglePlusK"))

        val Yta = lastAngleK * (Xt - unitCenter.x) + unitCenter.y
        debug.draw(
                CustomData.Line(
                        Vec2Float(unitCenter.x.toFloat(), unitCenter.y.toFloat()),
                        Vec2Float(Xt.toFloat(), Yta.toFloat()),
                        0.1f,
                        ColorFloat(255f, 0f, 0f, 155f)
                )
        )
        val Ytp = anglePlusK * (-unitCenter.x) + unitCenter.y
        debug.draw(
                CustomData.Line(
                        Vec2Float(unitCenter.x.toFloat(), unitCenter.y.toFloat()),
                        Vec2Float(0.0f, Ytp.toFloat()),
                        0.1f,
                        ColorFloat(0f, 255f, 0f, 255f)
                )
        )
        val Ytp1 = anglePlusK * (31 - unitCenter.x) + unitCenter.y
        debug.draw(
                CustomData.Line(
                        Vec2Float(unitCenter.x.toFloat(), unitCenter.y.toFloat()),
                        Vec2Float(31f, Ytp1.toFloat()),
                        0.1f,
                        ColorFloat(0f, 255f, 0f, 255f)
                )
        )
        val Ytm = angleMinusK * (-unitCenter.x) + unitCenter.y
        debug.draw(
                CustomData.Line(
                        Vec2Float(unitCenter.x.toFloat(), unitCenter.y.toFloat()),
                        Vec2Float(0f, Ytm.toFloat()),
                        0.1f,
                        ColorFloat(0f, 0f, 255f, 255f)
                )
        )
        val Ytm1 = angleMinusK * (31 - unitCenter.x) + unitCenter.y
        debug.draw(
                CustomData.Line(
                        Vec2Float(unitCenter.x.toFloat(), unitCenter.y.toFloat()),
                        Vec2Float(31f, Ytm1.toFloat()),
                        0.1f,
                        ColorFloat(0f, 0f, 255f, 255f)
                )
        )
        //

        val indexLeft = xl.toInt()
        val indexRight = xr.toInt()
        val indexBottom = yb.toInt()
        val indexTop = yt.toInt()

        for (i in if (unitCenter.x > enemyCenter.x) indexRight downTo indexLeft else indexLeft..indexRight) {
            for (j in indexBottom..indexTop) {
                if (game.level.tiles[i][j].discriminant == Tile.WALL.discriminant) {
                    if (xl.toInt() == xr.toInt() || yt.toInt() == yb.toInt() ||
                            directrixTileCollision(i, j, unitCenter, enemyCenter,
                                    unit.weapon!!.spread, game.properties.weaponParams[unit.weapon!!.typ]?.bullet?.size
                                    ?: 0.0)
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
            val left = if (enemyCenter.x < unitCenter.x) (unit.position.x - (unit.size.x / 2) - r).toInt() else unitCenter.x.toInt()
            val right = if (enemyCenter.x >= unitCenter.x) (unit.position.x + (unit.size.x / 2) + r).toInt() else unitCenter.x.toInt()
            val top = if (enemyCenter.y >= unitCenter.y) (unit.position.y + (unit.size.y) + r).toInt() else unit.position.y.toInt()
            val bottom = if (enemyCenter.y < unitCenter.y) (unit.position.y - r).toInt() else unit.position.y.toInt()

            for (i in left..right) {
                for (j in bottom..top) {
                    if ((i < 0 || j < 0 || i >= game.level.tiles.size || j >= game.level.tiles[0].size || game.level.tiles[i][j].discriminant == Tile.WALL.discriminant)
                            && directrixTileCollision(i, j, unitCenter, enemyCenter,
                                    unit.weapon!!.spread, game.properties.weaponParams[WeaponType.ROCKET_LAUNCHER]?.bullet?.size
                                    ?: 0.0)
                            && (dam > unit.health) && (dam < nearestEnemy.health || nearestEnemy.health > unit.health)) {
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
            enemyCenter: Vec2Double,
            deltaAngle: Double,
            bulletSize: Double
    ):
            Boolean {

        val actualAlpha = atan2(enemyCenter.y - unitCenter.y, enemyCenter.x - unitCenter.x)

        val alpha1 = atan2(tileYIndex - bulletSize / 2 - unitCenter.y, tileXIndex - bulletSize / 2 - unitCenter.x)
        if (alpha1 <= actualAlpha + deltaAngle && alpha1 >= actualAlpha - deltaAngle)
            return true

        val alpha2 = atan2(tileYIndex + 1 + bulletSize / 2 - unitCenter.y, tileXIndex - bulletSize / 2 - unitCenter.x)
        if (alpha2 <= actualAlpha + deltaAngle && alpha2 >= actualAlpha - deltaAngle)
            return true

        val alpha3 = atan2(tileYIndex - bulletSize / 2 - unitCenter.y, tileXIndex + 1 + bulletSize / 2 - unitCenter.x)
        if (alpha3 <= actualAlpha + deltaAngle && alpha3 >= actualAlpha - deltaAngle)
            return true

        val alpha4 = atan2(tileYIndex + 1 + bulletSize / 2 - unitCenter.y, tileXIndex + 1 + bulletSize / 2 - unitCenter.x)
        if (alpha4 <= actualAlpha + deltaAngle && alpha4 >= actualAlpha - deltaAngle)
            return true
        return false
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

    data class TileMarked(val type: Tile, var mark: Boolean = false)
}
