package eu.scisneromam.gamebridge

import eu.scisneromam.gamebridge.json.Base
import eu.scisneromam.gamebridge.json.ChestMessage
import eu.scisneromam.gamebridge.json.ReceiveItemsMessage
import eu.scisneromam.gamebridge.json.TopicsMessage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.axay.kspigot.items.itemStack
import net.axay.kspigot.main.KSpigotMainInstance
import org.bukkit.Material
import org.bukkit.block.Chest
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.eclipse.paho.client.mqttv3.IMqttMessageListener
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.lang.Exception


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
            MessageTypes.TOPICS -> {
                val topicsMessage = GameBridge.json.decodeFromString<TopicsMessage>(payload)
                if (topicsMessage.target == (KSpigotMainInstance as GameBridge).sender()) {
                    //the message is for us
                    GameBridge.topics = topicsMessage.topics
                    GameBridge.mqtt.subscribe(GameBridge.topics.receive, onReceive)
                    GameBridge.mqtt.subscribe(GameBridge.topics.controlReceive, onControlReceive)
                    
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
                val chestBlocks = Database.chestById(receiveMessage.receiver)
                val chests = chestBlocks.map { it.state as Chest }
                //TODO überflüssige items zurückschicken etc //blaclist etc
                for ((itemName, amount) in receiveMessage.items) {
                    val mat = Material.matchMaterial(itemName) ?: continue
                    //TODO reject
                    val item = itemStack(mat) {
                        this.amount = amount / chests.size
                    }
                    val rejectAmount = amount % chests.size
                    val overflow: MutableList<ItemStack> = arrayListOf()
                    val fillAble : MutableList<Chest> = arrayListOf()
                    fillAble.addAll(chests)
                    for (chest in chests) {
                        val items = chest.inventory.addItem(item)
                        if (items.isNotEmpty()) {
                            fillAble.remove(chest)
                            overflow.addAll(items.values)
                        }
                    }
                    while(overflow.isNotEmpty() && fillAble.isNotEmpty()) {
                        break
                    }
                }
                chests.forEach { it.update() }
                
            }
        }
    }
    
    val onControlReceive = IMqttMessageListener { topic, message ->
    
    }
    
    
}