package model

import util.StreamUtil

class Game {
    var currentTick: Int = 0
    lateinit var properties: Properties
    lateinit var level: Level
    lateinit var players: Array<Player>
    lateinit var units: Array<Unit>
    lateinit var bullets: Array<Bullet>
    lateinit var mines: Array<Mine>
    lateinit var lootBoxes: Array<LootBox>

    constructor()
    constructor(
        currentTick: Int,
        properties: Properties,
        level: Level,
        players: Array<Player>,
        units: Array<Unit>,
        bullets: Array<Bullet>,
        mines: Array<Mine>,
        lootBoxes: Array<LootBox>
    ) {
        this.currentTick = currentTick
        this.properties = properties
        this.level = level
        this.players = players
        this.units = units
        this.bullets = bullets
        this.mines = mines
        this.lootBoxes = lootBoxes
    }

    companion object {
        @Throws(java.io.IOException::class)
        fun readFrom(stream: java.io.InputStream): Game {
            val result = Game()
            result.currentTick = StreamUtil.readInt(stream)
            result.properties = Properties.readFrom(stream)
            result.level = Level.readFrom(stream)
            result.players = Array(StreamUtil.readInt(stream)) {
                val playersValue: Player = Player.readFrom(stream)
                playersValue
            }
            result.units = Array(StreamUtil.readInt(stream)) {
                val unitsValue: Unit = Unit.readFrom(stream)
                unitsValue
            }
            result.bullets = Array(StreamUtil.readInt(stream)) {
                val bulletsValue: Bullet = Bullet.readFrom(stream)
                bulletsValue
            }
            result.mines = Array(StreamUtil.readInt(stream)) {
                val minesValue: Mine = Mine.readFrom(stream)
                minesValue
            }
            result.lootBoxes = Array(StreamUtil.readInt(stream)) {
                val lootBoxesValue: LootBox = LootBox.readFrom(stream)
                lootBoxesValue
            }
            return result
        }
    }

    @Throws(java.io.IOException::class)
    fun writeTo(stream: java.io.OutputStream) {
        StreamUtil.writeInt(stream, currentTick)
        properties.writeTo(stream)
        level.writeTo(stream)
        StreamUtil.writeInt(stream, players.size)
        for (playersElement in players) {
            playersElement.writeTo(stream)
        }
        StreamUtil.writeInt(stream, units.size)
        for (unitsElement in units) {
            unitsElement.writeTo(stream)
        }
        StreamUtil.writeInt(stream, bullets.size)
        for (bulletsElement in bullets) {
            bulletsElement.writeTo(stream)
        }
        StreamUtil.writeInt(stream, mines.size)
        for (minesElement in mines) {
            minesElement.writeTo(stream)
        }
        StreamUtil.writeInt(stream, lootBoxes.size)
        for (lootBoxesElement in lootBoxes) {
            lootBoxesElement.writeTo(stream)
        }
    }
}
