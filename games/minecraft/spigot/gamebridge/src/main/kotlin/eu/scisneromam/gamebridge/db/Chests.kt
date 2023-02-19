package eu.scisneromam.gamebridge.db

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import java.util.UUID

object Chests : IntIdTable() {
    val x: Column<Int> = integer("x")
    val y: Column<Int> = integer("y")
    val z: Column<Int> = integer("z")
    val type: Column<String> = varchar("type", 20)
    val chestId: Column<String> = varchar("chest-id", 50)
    val world: Column<UUID> = uuid("world")
}

class ChestObj(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ChestObj>(Chests)
    var x by Chests.x
    var y by Chests.y
    var z by Chests.z
    var type     by Chests.type
    var chestId by Chests.chestId
    var world by Chests.world
}