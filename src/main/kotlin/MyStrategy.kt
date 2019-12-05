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

    fun getAction(unit: model.Unit, game: Game, debug: Debug): UnitAction {
        var nearestEnemy: model.Unit? = null

        maxDXPerTick = game.properties.unitMaxHorizontalSpeed / game.properties.ticksPerSecond
        jumpPerTick = game.properties.unitJumpSpeed / game.properties.ticksPerSecond
        boostJumpPerTick = game.properties.jumpPadJumpSpeed / game.properties.ticksPerSecond

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

//        nearestWeapon?.let {
//            buildPathForNearestWeapon(unit, nearestWeapon, game)
//        }
        val targetPos = when {
            unit.health != game.properties.unitMaxHealth && nearestHealthPack != null -> nearestHealthPack.position
            unit.weapon == null && nearestWeapon != null -> nearestWeapon.position
            nearestEnemy != null -> nearestEnemy.position
            else -> unit.position
        }


        debug.draw(CustomData.Log("Target pos: ${targetPos.x}:${targetPos.y}"))
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
                debug.draw(CustomData.Log("Weapon params: lft: ${it.lastFireTick} la: ${it.lastAngle} spr: ${it
                        .spread} mag: ${it.magazine} ft:${it.fireTimer} ws:${it.wasShooting}"))
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
        action.velocity = (targetPos.x - unit.position.x) * 1000
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

        debug.draw(CustomData.Log("Action: aim:${action.aim.x}:${action.aim.y} pos:${unit.position.x}:${unit
                .position.y} targetVel ${action.velocity} jump:${action.jump} "))

        debug.draw(CustomData.Log("maxSp: ${game.properties.unitMaxHorizontalSpeed} maxJS:${game.properties
                .unitJumpSpeed} jt:${game.properties.unitJumpTime}  ticksPS${game.properties.ticksPerSecond} dt:${game.properties.updatesPerTick}"))

        val pos = getPositionAfterAction(unit, action, game)
        debug.draw(CustomData.Rect(
                Vec2Float(pos.x.toFloat() - 0.2f, pos.y.toFloat() - 0.2f),
                Vec2Float(0.4f, 0.4f),
                ColorFloat(0f, 255f, 0f, 255f)
        ))
        debug.draw(CustomData.Rect(
                Vec2Float(unit.position.x.toFloat() - 0.1f, unit.position.y.toFloat() - 0.1f),
                Vec2Float(0.2f, 0.2f),
                ColorFloat(0f, 0f, 255f, 255f)
        ))

        return action
    }

//    private fun buildPathForNearestWeapon(unit: Unit, nearestWeapon: LootBox, game: Game) {
//
//
//
//        val dx = nearestWeapon.position.x - unit.position.x
//        val vel = if (abs(dx) > maxDXperTick) {
//            dx * 10
//        } else {
//            dx
//        }
//
//        var tick = 1
//        for (i in -1..1) {
//            for (j in -1..1) {
//                val act = model.UnitAction().apply {
//                    velocity = i * vel
//                    jump = j > 0
//                    jumpDown = j < 0
//                }
//
//
//
//                ProbablyAction(act,getPositionAfterAction(unit, act))
//            }
//        }
//    }


    private fun getPositionAfterAction(unit: Unit, act: UnitAction, game: Game): Vec2Double {
        val pos = Vec2Double(unit.position.x, unit.position.y)

        //Y prediction
        when {
            act.jump -> {
                if (unit.onGround)
                    pos.y += jumpPerTick
                if (unit.onLadder)
                    pos.y += jumpPerTick
            }
            act.jumpDown -> {

            }
            else -> {

            }
        }

        //X prediction
        val requiredDx = act.velocity / game.properties.ticksPerSecond

        pos.x += if (abs(requiredDx) <= maxDXPerTick) requiredDx else maxDXPerTick * sign(requiredDx)

        val checkingX = pos.x + sign(requiredDx) * unit.size.x / 2

        if (game.level.tiles[checkingX.toInt()][(unit.position.y).toInt()] == Tile.WALL ||
                game.level.tiles[checkingX.toInt()][(unit.position.y).toInt() + 1] == Tile.WALL
        )
            pos.x = checkingX.toInt() - sign(requiredDx) * ((if (sign(requiredDx) < 0) 1 else 0) + unit.size.x / 2)
        if (game.units.any {
                    it.id != unit.id &&
                            abs(it.position.x - pos.x) < unit.size.x &&
                            abs(it.position.x - pos.x) > unit.size.x / 2 &&
                            abs(it.position.y - pos.y) < unit.size.y
                }) {
            pos.x = unit.position.x
        }
        return pos
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
                            )) {
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

    data class ProbablyAction(
            val action: model.UnitAction,
            val probablyPositionAfterAction: Vec2Double,
            val currentTick: Int,
            val parentPA: ProbablyAction?) {
        var childs: ArrayList<ProbablyAction> = ArrayList()
    }
}
