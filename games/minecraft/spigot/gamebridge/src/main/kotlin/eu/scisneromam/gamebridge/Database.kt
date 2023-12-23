package eu.scisneromam.gamebridge

import eu.scisneromam.gamebridge.db.ChestObj
import eu.scisneromam.gamebridge.db.Chests
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.persistence.PersistentDataType
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.sql.Connection

object Database {
    lateinit var db: Database
    
    fun init(dataFolder: File) {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
        val ldb = Database.connect("jdbc:sqlite:${dataFolder.absolutePath}/data.db", "org.sqlite.JDBC", databaseConfig = DatabaseConfig { defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE })
        // For both: set SQLite compatible isolation level, see
        // https://github.com/JetBrains/Exposed/wiki/FAQ
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction(ldb) {
            SchemaUtils.create(Chests)
        }
        db = ldb
    }
    
    fun addChest(block: Block) {
        val chest = block.state
        if (chest !is Chest) {
            return
        }
        val persistent = chest.persistentDataContainer
        val id = persistent.get(GameBridge.chestIDKey, PersistentDataType.STRING) ?: return
        val chestType = persistent.get(GameBridge.chestTypeKey, PersistentDataType.STRING) ?: return
        transaction(db) {
            ChestObj.new {
                x = block.x
                y = block.y
                z = block.z
                chestId = id
                type = chestType
                world = block.world.uid
            }
        }
    }
    
    fun removeChest(block: Block) {
        transaction(db) {
            ChestObj.find { (Chests.x eq block.x) and (Chests.y eq block.y) and (Chests.z eq block.z) and (Chests.world eq block.world.uid) }.forEach { it.delete() }
        }
    }
    
    fun chestById(chestId: String): List<Block> {
        return transaction(db) {
            ChestObj.find { Chests.chestId eq chestId }.map { Bukkit.getWorld(it.world)?.getBlockAt(it.x, it.y, it.z) }
                .filter { it != null && it.state is Chest && (it.state as Chest).persistentDataContainer.has(GameBridge.chestTypeKey) }.requireNoNulls().toList()
        }
    }
    
    fun getChests(): List<Block> {
        return transaction(db) {
            ChestObj.all().map { Bukkit.getWorld(it.world)?.getBlockAt(it.x, it.y, it.z) }
                .filter { it != null && it.state is Chest && (it.state as Chest).persistentDataContainer.has(GameBridge.chestTypeKey) }.requireNoNulls().toList()
        }
    }
}