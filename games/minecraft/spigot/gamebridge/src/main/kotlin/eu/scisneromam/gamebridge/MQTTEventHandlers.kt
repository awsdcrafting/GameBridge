package eu.scisneromam.gamebridge

import eu.scisneromam.gamebridge.db.Chests
import eu.scisneromam.gamebridge.json.*
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.axay.kspigot.items.itemStack
import net.axay.kspigot.main.KSpigotMainInstance
import net.axay.kspigot.runnables.sync
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Server.Spigot
import org.bukkit.block.Chest
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.eclipse.paho.client.mqttv3.IMqttMessageListener
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.lang.Exception
import javax.sound.sampled.spi.FormatConversionProvider


object MQTTEventHandlers {
    
    
    val onGlobalControl: IMqttMessageListener = IMqttMessageListener { topic, message ->
        val payload = message.payload.decodeToString()
        val baseMessage = try {
            GameBridge.json.decodeFromString<Base>(payload)
        }
        catch (_: Exception) {
            return@IMqttMessageListener
        }
        KSpigotMainInstance.getLogger().info("Received message on $topic:$payload [$baseMessage]")
        when (baseMessage.type.trim().lowercase()) {
            MessageTypes.BACKEND_BOOT_TYPE -> {
                GameBridge.backend_reboot = true
                val sub1 = GameBridge.mqtt.unsubscribe(GameBridge.topics.receive)
                val sub2 = GameBridge.mqtt.unsubscribe(GameBridge.topics.controlReceive)
                GameBridge.instance.backendLogin()
            }   
            MessageTypes.TOPICS -> {
                val topicsMessage = GameBridge.json.decodeFromString<TopicsMessage>(payload)
                if (topicsMessage.target == (KSpigotMainInstance as GameBridge).sender()) {
                    //the message is for us
                    GameBridge.topics = topicsMessage.topics
                    GameBridge.backend_reboot = false
                    val sub1 = GameBridge.mqtt.subscribeWithResponse(GameBridge.topics.receive, onReceive)
                    val sub2 = GameBridge.mqtt.subscribeWithResponse(GameBridge.topics.controlReceive, onControlReceive)
                    KSpigotMainInstance.logger.info("${sub1.response}")
                    KSpigotMainInstance.logger.info("${sub2.response}")
                    sync { 
                        Database.getChests().forEach {
                            val chest = it.state
                            if (chest !is Chest) {
                                Database.removeChest(it)
                                return@forEach
                            }
                            val type: String = chest.persistentDataContainer.get(GameBridge.chestTypeKey, PersistentDataType.STRING) ?: return@forEach
                            val id: String = chest.persistentDataContainer.get(GameBridge.chestIDKey, PersistentDataType.STRING) ?: return@forEach
                            if (type != Constants.CHEST_TYPE_RECEIVER)
                            {
                                return@forEach
                            }
                            GameBridge.mqtt.publish(GameBridge.topics.controlSend, MqttMessage(GameBridge.json.encodeToString<ChestMessage>(ChestMessage(id, GameBridge.instance.sender())).toByteArray()))
                        } 
                    }
                    KSpigotMainInstance.logger.info("Finished topics")
                }
            }
        }
    }
    
    val onReceive = IMqttMessageListener { topic, message ->
        val payload = message.payload.decodeToString()
        KSpigotMainInstance.getLogger().info("Received message on $topic:$payload")
        val baseMessage = try {
            GameBridge.json.decodeFromString<Base>(payload)
        }
        catch (_: Exception) {
            return@IMqttMessageListener
        }
        KSpigotMainInstance.getLogger().info("Received message on $topic:$payload [$baseMessage]")
        when (baseMessage.type.trim().lowercase()) {
            MessageTypes.SEND -> {
                val receiveMessage = GameBridge.json.decodeFromString<ReceiveItemsMessage>(message.payload.decodeToString())
                KSpigotMainInstance.getLogger().info("Parsed message on $topic:$payload [$receiveMessage]")
                println("Parsed message on $topic:$payload [$receiveMessage]")
                sync {                 
                    val chestBlocks = Database.chestById(receiveMessage.receiver)
                    val chests = chestBlocks.map { it.state as Chest }
                    KSpigotMainInstance.getLogger().info("Found $chests with id ${receiveMessage.receiver}")
                    //TODO überflüssige items zurückschicken etc //blaclist etc
                    
                    
                    
                    val items = receiveMessage.items.map { 
                        val mat = Material.matchMaterial(it.key) ?: return@map null
                        itemStack(mat) { this.amount = it.value / chests.size } 
                    }.filterNotNull()
                    var overflow: MutableList<ItemStack> = items.toMutableList()
                    var fillAble: MutableList<Chest> = chests.toMutableList()
                    
                    //TODO reject
                    while (overflow.isNotEmpty() && fillAble.isNotEmpty())
                    {
                        val newOverflow: MutableList<ItemStack> = arrayListOf()
                        val newFillable: MutableList<Chest> = arrayListOf()
                        for (chest in fillAble)
                        {
                            for (item in overflow)
                            {
                                val items = chest.blockInventory.addItem(item)
                                if (items.isNotEmpty()) {
                                    newOverflow.addAll(items.values)
                                }else
                                {
                                    newFillable.add(chest)
                                }
                            }
                        }
                        fillAble = newFillable
                        overflow = newOverflow
                    }
                    //items which cannot be split on all chests
                    var singleItems = receiveMessage.items.filter {
                        it.value < chests.size
                    }.map {
                        val mat = Material.matchMaterial(it.key) ?: return@map null
                        itemStack(mat) { this.amount = it.value }
                    }.filterNotNull().toTypedArray()
                    var aroverflow = singleItems
                    while (fillAble.isNotEmpty() && aroverflow.isNotEmpty())
                    {
                        val chest = fillAble.removeFirst()
                        aroverflow = chest.blockInventory.addItem(*singleItems).values.toTypedArray()
                    }
                }
            }
        }
    }
    
    val onControlReceive = IMqttMessageListener { topic, message ->
    
    }
    
    
}