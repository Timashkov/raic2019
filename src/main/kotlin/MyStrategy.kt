import model.*
import java.awt.Color
import kotlin.math.ceil
import kotlin.math.floor

class MyStrategy {

    companion object {
        internal fun distanceSqr(a: Vec2Double, b: Vec2Double): Double {
            return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
        }
    }

    fun getAction(unit: model.Unit, game: Game, debug: Debug): UnitAction {
        var nearestEnemy: model.Unit? = null
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
            if (item is Item.Weapon && item.weaponType == WeaponType.ASSAULT_RIFLE) {
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
        val targetPos = when {
            unit.health != game.properties.unitMaxHealth && nearestHealthPack != null -> nearestHealthPack.position
            unit.weapon == null && nearestWeapon != null -> nearestWeapon.position
            nearestEnemy != null -> {
                unit.weapon?.let {
                    debug.draw(CustomData.Log("Weapon params: $it"))
                }
                nearestEnemy.position
            }
            else -> unit.position
        }


        debug.draw(CustomData.Log("Target pos: $targetPos"))
        var aim = Vec2Double(0.0, 0.0)
        if (nearestEnemy != null) {
            aim = Vec2Double(
                nearestEnemy.position.x - unit.position.x,
                nearestEnemy.position.y - unit.position.y
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

        if (nearestEnemy == null || unit.weapon == null) {
            action.shoot = false
        } else {
            action.shoot = shootAllowed(unit, nearestEnemy, game, debug)
        }
        action.swapWeapon = false
        action.plantMine = false
        return action
    }

    private fun shootAllowed(unit: model.Unit, nearestEnemy: model.Unit, game: Game, debug: Debug): Boolean {
        var xl = 0.0
        var xr = 0.0
        if (unit.position.x > nearestEnemy.position.x) {
            xl = nearestEnemy.position.x
            xr = unit.position.x
        } else {
            xl = unit.position.x
            xr = nearestEnemy.position.x
        }

        var yt = 0.0
        var yb = 0.0
        if (unit.position.y > nearestEnemy.position.y) {
            yb = nearestEnemy.position.y + 0.5 * nearestEnemy.size.y
            yt = unit.position.y + 0.5 * unit.size.y
        } else {
            yb = unit.position.y + 0.5 * unit.size.y
            yt = nearestEnemy.position.y + 0.5 * nearestEnemy.size.y
        }

        val indexLeft = xl.toInt()
        val indexRight = xr.toInt()
        val indexBottom = yb.toInt()
        val indexTop = yt.toInt()

        /*debug.draw(
                CustomData.Rect(
                    Vec2Float(
                        tileXIndex.toFloat(),
                        tileYIndex.toFloat()
                    ),
                    Vec2Float(
                        1f,
                        1f
                    ),
                    ColorFloat(255f, 0f, 0f, 110f)
                )
            )*/

        for (i in indexLeft..indexRight) {
            for (j in indexBottom..indexTop) {
                if (game.level.tiles[i][j].discriminant == Tile.WALL.discriminant) {
                    if (xl.toInt() == xr.toInt() || yt.toInt() == yb.toInt()) {
                        debug.draw(CustomData.Log("me: ${unit.position} enemy:${nearestEnemy.position}"))
                        debug.draw(CustomData.Log("${xl.toInt()} == ${xr.toInt()}  ${yt.toInt()} == ${yb.toInt()}"))

                        return false
                    } else {
                        if (directrixTileCollision(
                                i,
                                j,
                                xl,
                                xr,
                                yb,
                                yt,
                                debug
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
                                    ColorFloat(0f, 0f, 255f, 110f)
                                )
                            )
                            debug.draw(CustomData.Log("me: ${unit.position} enemy:${nearestEnemy.position}"))
                            debug.draw(CustomData.Log("${xl.toInt()} == ${xr.toInt()}  ${yt.toInt()} == ${yb.toInt()}"))

                            return false
                        }
                    }
                }
            }
        }
        return true
    }

    private fun directrixTileCollision(
        tileXIndex: Int,
        tileYIndex: Int,
        xl: Double,
        xr: Double,
        yb: Double,
        yt: Double,
        debug: Debug
    ):
            Boolean {


        val y0 = (tileXIndex * (yb - yt) + yt * xl - yb * xr) / (xl - xr)
        if (y0 < tileYIndex + 1 && y0 > tileYIndex) {
            debug.draw(CustomData.Log("== $xl : $xr : $yb : $yt"))
            debug.draw(CustomData.Log(">>>${tileXIndex} : ${y0}"))
            return true
        }

        val y1 = ((tileXIndex + 1) * (yb - yt) + yt * xl - yb * xr) / (xl - xr)
        if (y1 < tileYIndex + 1 && y1 > tileYIndex) {
            debug.draw(CustomData.Log("== $xl : $xr : $yb : $yt"))
            debug.draw(CustomData.Log("<<<${tileXIndex + 1} : ${y1}"))

            return true
        }
        return false
    }

}
