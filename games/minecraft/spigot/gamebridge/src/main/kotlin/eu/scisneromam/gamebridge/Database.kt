package eu.scisneromam.gamebridge

import com.google.common.util.concurrent.ClosingFuture.ValueAndCloser
import eu.scisneromam.gamebridge.db.ChestObj
import eu.scisneromam.gamebridge.db.Chests
import jdk.incubator.foreign.CLinker.VaList
import net.axay.kspigot.main.KSpigotMainInstance
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.persistence.PersistentDataType
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.io.FilterReader
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
            commit()
        }
        db = ldb
    }
    
    fun addChest(block: Block) {
        val chest = block.state
        if (chest !is Chest) {
            println("Missing chest")
            return
        }
        val persistent = chest.persistentDataContainer
        val id = persistent.get(GameBridge.chestIDKey, PersistentDataType.STRING)
        if (id == null)
        {
            println("Missing id")
            return
        }
        val chestType = persistent.get(GameBridge.chestTypeKey, PersistentDataType.STRING)
        if (chestType == null)
        {
            println("Missing chestType")
            return
        }
        println("Adding to Database")
        transaction(db) {
            ChestObj.new {
                x = block.x
                y = block.y
                z = block.z
                chestId = id
                type = chestType
                world = block.world.uid
            }
            commit()
        }
        println("All chests: ${getChests()}")
    }
    
    fun removeChest(block: Block) {
        transaction(db) {
            ChestObj.find { (Chests.x eq block.x) and (Chests.y eq block.y) and (Chests.z eq block.z) and (Chests.world eq block.world.uid) }.forEach { it.delete() }
            commit()
        }
    }
    
    fun chestById(chestId: String): List<Block> {
        return transaction(db) {
            val list = ChestObj.find { Chests.chestId eq chestId }.map { Bukkit.getWorld(it.world)?.getBlockAt(it.x, it.y, it.z) }
                .filter { it != null && it.state is Chest && (it.state as Chest).persistentDataContainer.has(GameBridge.chestTypeKey) }.requireNoNulls().toList()
            list
        }
    }
    
    fun getChests(): List<Block> {
        return transaction(db) {
            val list = ChestObj.all().toList()
            val list2 = list.map { Bukkit.getWorld(it.world)?.getBlockAt(it.x, it.y, it.z) }
            val filter = list2.filter { 
                if (it == null || it.state !is Chest)
                {
                    return@filter false
                }
                val chest = it.state as Chest
                val res = chest.persistentDataContainer.has(GameBridge.chestTypeKey) 
                return@filter res
            }
            val nonNull = filter.filterNotNull()
            val asList = nonNull.toList()
            nonNull
        }
    }
}