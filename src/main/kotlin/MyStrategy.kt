import model.*
import model.Unit
import java.lang.Math.abs
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
    private lateinit var unitMovement: UnitMovement

    fun getAction(unit: model.Unit, game: Game, debug: Debug): UnitAction {
        var nearestEnemy: model.Unit? = null

        maxDXPerTick = game.properties.unitMaxHorizontalSpeed / game.properties.ticksPerSecond
        jumpPerTick = game.properties.unitJumpSpeed / game.properties.ticksPerSecond
        boostJumpPerTick = game.properties.jumpPadJumpSpeed / game.properties.ticksPerSecond

        maxJumpTick = (game.properties.jumpPadJumpTime * game.properties.ticksPerSecond).toInt()
        maxBoostJumpTick = (game.properties.jumpPadJumpTime * game.properties.ticksPerSecond).toInt()

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
                if (nearestHealthPack == null || distanceSqr(unit.position, lootBox.position) < distanceSqr(
                                unit
                                        .position, nearestHealthPack.position
                        )
                ) {
                    nearestHealthPack = lootBox
                }
            }
        }

        if (game.currentTick == 1) {
            nearestWeapon?.let {
                buildPathForNearestWeapon(unit, nearestWeapon, game, debug)
            }
        }
        val targetPos = when {
            unit.health != game.properties.unitMaxHealth && nearestHealthPack != null -> nearestHealthPack.position
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
            debug.draw(
                    CustomData.Line(
                            Vec2Float(
                                    unit.position.x.toFloat(),
                                    (unit.position.y + unit.size.y * 0.5).toFloat()
                            ),
                            Vec2Float(
                                    nearestEnemy.position.x.toFloat(),
                                    (nearestEnemy.position.y + nearestEnemy.size.y * 0.5).toFloat()
                            ),
                            0.1f,
                            ColorFloat(10f, 10f, 10f, 10f)
                    )
            )
            unit.weapon?.let {
                debug.draw(
                        CustomData.Log(
                                "Weapon params: lft: ${it.lastFireTick} la: ${it.lastAngle} spr: ${it
                                        .spread} mag: ${it.magazine} ft:${it.fireTimer} ws:${it.wasShooting}"
                        )
                )
            }
        }

        var jump = targetPos.y > unit.position.y
        if (targetPos.x > unit.position.x && game.level.tiles[(unit.position.x + 1).toInt()][(unit.position.y).toInt()] == Tile.WALL) {
            jump = true
        }
        if (targetPos.x < unit.position.x && game.level.tiles[(unit.position.x - 1).toInt()][(unit.position.y).toInt()] == Tile.WALL) {
            jump = true
        }

        val action = UnitAction()
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
        debug.draw(
                CustomData.Log(
                        "Action: pos:${unit.position.x}:${unit
                                .position.y} jump:${action.jump} jumptick ${unitMovement.jumpTick} maxjumptick $maxJumpTick boostJumpTick " +
                                "${unitMovement.boostJumpTick} maxboostjumptick $maxBoostJumpTick onGround ${unit
                                        .onGround}  " +
                                "jumpDown: ${action.jumpDown}"
                )
        )
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

    private fun buildPathForNearestWeapon(unit: Unit, nearestWeapon: LootBox, game: Game, debug: Debug) {

        val vel = getVelocity(unit.position.x, nearestWeapon.position.x)

        val probActions = HashMap<Int, ArrayList<ProbablyAction>>()

        for (t in 1..10) {

            val pas = probActions[t - 1]
            if (pas.isNullOrEmpty()) {
//                for (i in -1..1) {
                for (j in 0..2) {
                    val act = model.UnitAction().apply {
                        velocity = /*i **/ vel
                        jump = j == 0
                        jumpDown = j == 2
                    }

                    if (probActions[t].isNullOrEmpty()) {
                        probActions[t] = ArrayList<ProbablyAction>()
                    }
                    val r = ProbablyAction(act, getUnitMovement(UnitMovement(unit.position, unit.onGround),
                            unit.size, unit.id,
                            act, game), t,
                            null)
                    debug.draw(
                            CustomData.Rect(
                                    Vec2Float(r.probablyPositionAfterAction.pos.x.toFloat() - 0.1f,
                                            r.probablyPositionAfterAction.pos.y.toFloat() - 0.1f),
                                    Vec2Float(0.2f, 0.2f),
                                    ColorFloat(111f, 111f, 111f, 255f)
                            )
                    )
                    probActions[t]?.add(r)
                }
//                }
            } else {
                pas.forEach { parent ->
                    //for (i in -1..1) {
                    for (j in 0..2) {
                        val act = model.UnitAction().apply {
                            velocity = /*i **/ vel
                            jump = j == 0
                            jumpDown = j == 2
                        }

                        if (probActions[t].isNullOrEmpty()) {
                            probActions[t] = ArrayList<ProbablyAction>()
                        }
                        val r = ProbablyAction(act, getUnitMovement(parent.probablyPositionAfterAction, unit.size,
                                unit.id, act, game), t, null)
                        debug.draw(
                                CustomData.Rect(
                                        Vec2Float(r.probablyPositionAfterAction.pos.x.toFloat() - 0.1f,
                                                r.probablyPositionAfterAction.pos.y.toFloat() - 0.1f),
                                        Vec2Float(0.2f, 0.2f),
                                        ColorFloat(111f, 111f, 111f, 255f)
                                )
                        )
                        probActions[t]?.add(r)
                    }
                    //}
                }
            }
        }

    }

    private fun getVelocity(xCurrent: Double, xTarget: Double): Double {
        val dx = xTarget - xCurrent
        return if (abs(dx) > maxDXPerTick) {
            dx * 10
        } else {
            dx
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
        var xl = unit.position.x
        var xr = nearestEnemy.position.x
        if (unit.position.x > nearestEnemy.position.x) {
            xl = nearestEnemy.position.x
            xr = unit.position.x
        }

        var yt = nearestEnemy.position.y + 0.5 * nearestEnemy.size.y
        var yb = unit.position.y + 0.5 * unit.size.y
        if (unit.position.y > nearestEnemy.position.y) {
            val yy = yb
            yb = yt
            yt = yy
        }

        val indexLeft = xl.toInt()
        val indexRight = xr.toInt()
        val indexBottom = yb.toInt()
        val indexTop = yt.toInt()

        for (i in if (unit.position.x > nearestEnemy.position.x) indexRight downTo indexLeft else indexLeft..indexRight) {
            for (j in indexBottom..indexTop) {
                if (game.level.tiles[i][j].discriminant == Tile.WALL.discriminant) {
                    if (xl.toInt() == xr.toInt() || yt.toInt() == yb.toInt() || directrixTileCollision(
                                    i,
                                    j,
                                    unit.position.x,
                                    nearestEnemy.position.x,
                                    unit.position.y + unit.size.y * 0.5,
                                    nearestEnemy.position.y + nearestEnemy.size.y * 0.5
                            )
                    ) {
                        debug.draw(
                                CustomData.Rect(
                                        Vec2Float(
                                                i.toFloat(),
                                                j.toFloat()
                                        ),
                                        Vec2Float(
                                                1f,
                                                1f
                                        ),
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
            x1: Double,
            x2: Double,
            y1: Double,
            y2: Double
    ):
            Boolean {

        val first = y1 - y2
        val second = y2 * x1 - y1 * x2
        val third = x1 - x2

        val yl = (tileXIndex * first + second) / third
        if (yl < tileYIndex + 1 && yl > tileYIndex) {
            return true
        }

        val yr = ((tileXIndex + 1) * first + second) / third
        if (yr < tileYIndex + 1 && yr > tileYIndex) {
            return true
        }

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
    ) {
        var childs: ArrayList<ProbablyAction> = ArrayList()
    }
}
