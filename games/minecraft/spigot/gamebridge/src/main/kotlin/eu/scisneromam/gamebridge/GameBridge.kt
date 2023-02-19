package eu.scisneromam.gamebridge

import eu.scisneromam.gamebridge.json.*
import io.papermc.paper.event.block.BlockBreakBlockEvent
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.axay.kspigot.chat.KColors
import net.axay.kspigot.chat.literalText
import net.axay.kspigot.event.listen
import net.axay.kspigot.extensions.bukkit.plainText
import net.axay.kspigot.items.itemStack
import net.axay.kspigot.main.KSpigot
import net.axay.kspigot.main.KSpigotMainInstance
import net.axay.kspigot.runnables.sync
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.block.data.type.WallSign
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.inventory.Inventory
import org.bukkit.persistence.PersistentDataType
import org.eclipse.paho.client.mqttv3.*
import java.lang.Exception

class GameBridge : KSpigot()
{
    
    companion object
    {
        lateinit var itemTypeKey: NamespacedKey
        lateinit var chestTypeKey: NamespacedKey
        lateinit var chestIDKey: NamespacedKey
        lateinit var mqtt: MqttClient
        lateinit var json: Json
        lateinit var topics: Topics
        fun isTopicsInitialized() = ::topics.isInitialized
    }
    
    fun sender(): List<String> = config.get("sender_path") as List<String>
    
    override fun load()
    {
        GameBridge.itemTypeKey = NamespacedKey(KSpigotMainInstance, Constants.ITEM_TYPE)
        GameBridge.chestTypeKey = NamespacedKey(KSpigotMainInstance, Constants.CHEST_TYPE)
        GameBridge.chestIDKey = NamespacedKey(KSpigotMainInstance, Constants.CHEST_ID)
        GameBridge.json = Json {
            ignoreUnknownKeys = true
            prettyPrint = false
            encodeDefaults = true
        }
        reloadConfig()
        defaultConfig()
        saveConfig()
    }
    
    private fun defaultConfig()
    {
        config.addDefault("mqtt.server", "localhost")
        config.addDefault("mqtt.port", "1883")
        config.addDefault("mqtt.user", "")
        config.addDefault("mqtt.pass", "")
        config.addDefault("mqtt.global_control_topic", "gamebridge/control/")
        config.addDefault("sender_path", listOf("minecraft", "vanilla"))
        config.options().copyDefaults(true)
    }
    
    fun connectMqtt()
    {
        val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        var uri = "${config.get("mqtt.server")}"
        val port = config.get("mqtt.port") as String?
        if (!port.isNullOrBlank())
        {
            uri += ":$port"
        }
        if (!uri.startsWith("tcp://") && !uri.startsWith("ssl://"))
        {
            uri = "tcp://$uri"
        }
        mqtt = MqttClient(uri, "GameBridge-" + (1..16).map { charPool.random() }.joinToString(""))
        val mqttOptions = MqttConnectOptions()
        mqttOptions.isCleanSession = true
        mqttOptions.isAutomaticReconnect = true
        mqttOptions.keepAliveInterval = 20
        mqttOptions.connectionTimeout = 60
        val user = config.get("mqtt.user")
        if (user is String)
        {
            mqttOptions.userName = user
        }
        val password = config.get("mqtt.pass")
        if (password is String)
        {
            mqttOptions.password = password.toCharArray()
        }
        
        mqtt.connect(mqttOptions)
        mqtt.subscribe("${config.get("mqtt.global_control_topic")}receive") { topic, message ->
            val payload = message.payload.decodeToString()
            val baseMessage = try
            {
                GameBridge.json.decodeFromString<Base>(payload)
            }
            catch (_: Exception)
            {
                return@subscribe
            }
            KSpigotMainInstance.logger.info("Received message on $topic:$payload [$baseMessage]")
            when (baseMessage.type.trim().lowercase())
            {
                MessageTypes.TOPICS ->
                {
                    val topicsMessage = GameBridge.json.decodeFromString<TopicsMessage>(payload)
                    if (topicsMessage.target == sender())
                    {
                        //the message is for us
                        GameBridge.topics = topicsMessage.topics
                        mqtt.subscribe(GameBridge.topics.receive) { topic, message ->
                            val baseMessage = try
                            {
                                GameBridge.json.decodeFromString<Base>(payload)
                            }
                            catch (_: Exception)
                            {
                                return@subscribe
                            }
                            when (baseMessage.type.trim().lowercase())
                            {
                                MessageTypes.SEND ->
                                {
                                    val receiveMessage = GameBridge.json.decodeFromString<ReceiveItemsMessage>(message.payload.decodeToString())
                                    val chests = Database.chestById(receiveMessage.receiver)
                                    //TODO überflüssige items zurückschicken etc //blaclist etc
                                    for ((itemName, amount) in receiveMessage.items)
                                    {
                                        val mat = Material.getMaterial(itemName) ?: continue
                                        //TODO reject
                                        val item = itemStack(mat) {
                                            this.amount = amount / chests.size
                                        }
                                        for (chest in chests)
                                        {
                                            (chest.state as Chest).inventory.addItem(item)
                                        }
                                    }
                                    
                                }
                            }
                        }
                        mqtt.subscribe(GameBridge.topics.controlReceive) { topic, message ->
                        
                        }
                    }
                }
            }
        }
        KSpigotMainInstance.logger.info("Mqtt status ${mqtt.isConnected}")
        //val response = mqtt.subscribeWithResponse("#") { topic, message -> KSpigotMainInstance.logger.info("Received message on $topic:${message.payload.decodeToString()} ") }
        //KSpigotMainInstance.logger.info("${response} ${response.response} ${response.isComplete}")
        mqtt.publish("${config.get("mqtt.global_control_topic")}send", MqttMessage(GameBridge.json.encodeToString<LoginMessage>(LoginMessage(sender())).toByteArray(Charsets.UTF_8)))
    }
    
    fun sendItems(inventory: Inventory, id: String)
    {
        //TODO send on mqtt
        val toSend = inventory.filter { it?.itemMeta?.persistentDataContainer?.has(GameBridge.itemTypeKey) == true }
        val map = HashMap<String, Int>()
        for (itemStack in toSend)
        {
            map.compute(itemStack.type.name) { _, amount ->
                if (amount == null)
                {
                    itemStack.amount
                }
                else
                {
                    amount + itemStack.amount
                }
            }
        }
        if (isTopicsInitialized()) {
            mqtt.publish(topics.send, MqttMessage(GameBridge.json.encodeToString<SendItemsMessage>(SendItemsMessage(sender(), id, map)).toByteArray()))
        }
        inventory.removeAll { it?.itemMeta?.persistentDataContainer?.has(GameBridge.itemTypeKey) == true }
    }
    
    override fun startup()
    {
        dataFolder.mkdirs()
        Database.init(dataFolder)
        //confirm db, and push to mqtt
        try
        {
            connectMqtt()
        }
        catch (e: Exception)
        {
            KSpigotMainInstance.logger.warning("Failed to connect to mqtt ${e.stackTraceToString()}")
        }
        
        
        // Plugin startup logic
        
        
        listen<InventoryMoveItemEvent> {
            val sourceHolder = it.source.holder
            val destinationHolder = it.destination.holder
            if (sourceHolder !is Chest && destinationHolder !is Chest)
            {
                //we only care for chests
                return@listen
            }
            //our source inv is a chest
            if (sourceHolder is Chest)
            {
                if (sourceHolder.persistentDataContainer.get(GameBridge.chestTypeKey, PersistentDataType.STRING) == Constants.CHEST_TYPE_SENDER)
                {
                    //check if item has metadata
                    if (it.item.itemMeta.persistentDataContainer.has(GameBridge.itemTypeKey))
                    {
                        it.isCancelled = true
                        return@listen
                    }
                }
            }
            
            //the destination inventory is a chest
            if (destinationHolder is Chest)
            {
                when (destinationHolder.persistentDataContainer.get(GameBridge.chestTypeKey, PersistentDataType.STRING))
                {
                    //in case of a receiver chest
                    Constants.CHEST_TYPE_RECEIVER ->
                    {
                        //cancel event, you are not allowed to put items into a receiver chest
                        it.isCancelled = true
                        return@listen
                    }
                    
                    //in case of a sender chest
                    Constants.CHEST_TYPE_SENDER ->
                    {
                        //set item meta data
                        it.item.editMeta { it.persistentDataContainer.set(GameBridge.itemTypeKey, PersistentDataType.STRING, Constants.ITEM_TYPE_SEND) }
                        sync { sendItems(it.destination, destinationHolder.persistentDataContainer.get(GameBridge.chestIDKey, PersistentDataType.STRING)!!) }
                    }
                }
            }
        }
        
        listen<InventoryClickEvent> {
            val holder = it.inventory.holder
            if (holder !is Chest)
            {
                return@listen
            }
            
            val clickedItem = it.currentItem
            val cursorItem = it.cursor
            when (holder.persistentDataContainer.get(GameBridge.chestTypeKey, PersistentDataType.STRING))
            {
                //in case of a sender chest
                Constants.CHEST_TYPE_SENDER ->
                {
                    //check if item has metadata
                    if (clickedItem != null && clickedItem.itemMeta != null && clickedItem.itemMeta.persistentDataContainer.has(GameBridge.itemTypeKey))
                    {
                        it.isCancelled = true
                        return@listen
                    }
                    if (cursorItem != null && cursorItem.itemMeta != null && cursorItem.itemMeta.persistentDataContainer.has(GameBridge.itemTypeKey))
                    {
                        it.isCancelled = true
                        return@listen
                    }
                    if ((it.action == InventoryAction.HOTBAR_SWAP || it.action == InventoryAction.HOTBAR_MOVE_AND_READD) && it.clickedInventory == it.inventory)
                    {
                        it.isCancelled = true
                        return@listen
                    }
                    if (("PLACE" in it.action.name || it.action == InventoryAction.SWAP_WITH_CURSOR) && it.clickedInventory == it.inventory)
                    {
                        cursorItem?.editMeta { it.persistentDataContainer.set(GameBridge.itemTypeKey, PersistentDataType.STRING, Constants.ITEM_TYPE_SEND) }
                        if (cursorItem != null)
                        {
                            sync {
                                sendItems(it.inventory, holder.persistentDataContainer.get(GameBridge.chestIDKey, PersistentDataType.STRING)!!)
                            }
                        }
                    }
                    if (it.action == InventoryAction.MOVE_TO_OTHER_INVENTORY && it.clickedInventory != it.inventory)
                    {
                        clickedItem?.editMeta { it.persistentDataContainer.set(GameBridge.itemTypeKey, PersistentDataType.STRING, Constants.ITEM_TYPE_SEND) }
                        if (clickedItem != null)
                        {
                            sync {
                                sendItems(it.inventory, holder.persistentDataContainer.get(GameBridge.chestIDKey, PersistentDataType.STRING)!!)
                            }
                        }
                    }
                }
                
                Constants.CHEST_TYPE_RECEIVER ->
                {
                    val forbiddenActions = listOf<InventoryAction>(
                        InventoryAction.MOVE_TO_OTHER_INVENTORY,
                        InventoryAction.PLACE_ALL,
                        InventoryAction.PLACE_ONE,
                        InventoryAction.PLACE_SOME,
                        InventoryAction.SWAP_WITH_CURSOR,
                        InventoryAction.HOTBAR_SWAP,
                        InventoryAction.HOTBAR_MOVE_AND_READD
                    )
                    if (it.action in forbiddenActions && it.clickedInventory != it.inventory)
                    {
                        it.isCancelled = true
                        return@listen
                    }
                }
            }
        }
        
        listen<InventoryDragEvent> {
            val holder = it.inventory.holder
            if (holder !is Chest)
            {
                return@listen
            }
            when (holder.persistentDataContainer.get(GameBridge.chestTypeKey, PersistentDataType.STRING))
            {
                Constants.CHEST_TYPE_RECEIVER, Constants.CHEST_TYPE_SENDER -> it.isCancelled = true
            }
        }
        
        
        listen<SignChangeEvent> {
            val lines = it.lines()
            val sign = it.block.blockData
            if (sign !is WallSign)
            {
                KSpigotMainInstance.logger.info("No WallSign")
                return@listen
            }
            val placedAgainst = it.block.getRelative(sign.facing.oppositeFace)
            val chest = placedAgainst.state
            if (chest !is Chest)
            {
                return@listen
            }
            if (lines.size < 3)
            {
                return@listen
            }
            if (lines[0].plainText().trim().lowercase() != "[gamebridge]")
            {
                return@listen
            }
            val oldType = chest.persistentDataContainer.get(GameBridge.chestTypeKey, PersistentDataType.STRING)
            val oldId = chest.persistentDataContainer.get(GameBridge.chestIDKey, PersistentDataType.STRING)
            if ((oldType == Constants.CHEST_TYPE_SENDER || oldType == Constants.CHEST_TYPE_RECEIVER) && oldId != null)
            {
                it.player.sendMessage(literalText("[") {
                    color = KColors.GRAY
                    text("GameBridge") {
                        color = KColors.AQUA
                    }
                    text("]") {
                        color = KColors.GRAY
                    }
                    text(": This chest already has the type ")
                    text(oldType) {
                        color = KColors.GREEN
                        bold = true
                        underline = true
                    }
                    text(" and id ")
                    text(oldId) {
                        color = KColors.BLUE
                        bold = true
                        underline = true
                    }
                    text(". In order to change the type or id remove the signs first.")
                })
                return@listen
            }
            val id = lines[2].plainText().trim()
            if (id.isBlank())
            {
                //send message?
                return@listen
            }
            when (lines[1].plainText().trim().lowercase())
            {
                Constants.CHEST_TYPE_SENDER ->
                {
                    chest.persistentDataContainer.set(GameBridge.chestTypeKey, PersistentDataType.STRING, Constants.CHEST_TYPE_SENDER)
                    chest.persistentDataContainer.set(GameBridge.chestIDKey, PersistentDataType.STRING, id)
                    KSpigotMainInstance.logger.info("$chest is now a sender chest with id $id")
                }
                
                Constants.CHEST_TYPE_RECEIVER ->
                {
                    chest.persistentDataContainer.set(GameBridge.chestTypeKey, PersistentDataType.STRING, Constants.CHEST_TYPE_RECEIVER)
                    chest.persistentDataContainer.set(GameBridge.chestIDKey, PersistentDataType.STRING, id)
                    KSpigotMainInstance.logger.info("$chest is now a receiver chest with id $id")
                    Database.addChest(placedAgainst)
                    if (isTopicsInitialized())
                    {
                        mqtt.publish(GameBridge.topics.controlSend, MqttMessage(GameBridge.json.encodeToString<ChestMessage>(ChestMessage(id, sender())).toByteArray()))
                    }
                }
            }
            chest.update()
        }
        
        //maybe extend to multi listener?
        listen<BlockBreakBlockEvent> {
            blockBreak(it.block)
        }
        listen<BlockBreakEvent> {
            blockBreak(it.block)
        }
        
    }
    
    private fun blockBreak(block: Block)
    {
        val targetBlock: Block = when (block.blockData)
        {
            is WallSign ->
            {
                block.getRelative((block.blockData as WallSign).facing.oppositeFace)
            }
            is org.bukkit.block.data.type.Chest -> block
            else -> null
        } ?: return
        val chest = targetBlock.state
        if (chest !is Chest)
        {
            return
        }
        val type = chest.persistentDataContainer.get(GameBridge.chestTypeKey, PersistentDataType.STRING) ?: return
        if (type != Constants.CHEST_TYPE_RECEIVER) //we do not care for sender chests
        {
            return
        }
        val id = chest.persistentDataContainer.get(GameBridge.chestIDKey, PersistentDataType.STRING) ?: return
        Database.removeChest(targetBlock)
        if (isTopicsInitialized())
        {
            GameBridge.mqtt.publish(GameBridge.topics.controlSend, MqttMessage(GameBridge.json.encodeToString<RemoveChestMessage>(RemoveChestMessage(id, sender())).toByteArray()))
        }
        KSpigotMainInstance.logger.info("chest ${targetBlock}[$chest] got destroyed")
    }
    
    override fun shutdown()
    {
        GameBridge.mqtt.publish(
            "${config.get("mqtt.global_control_topic")}send", MqttMessage(GameBridge.json.encodeToString<LogoutMessage>(LogoutMessage(sender())).toByteArray())
        )
        // Plugin shutdown logic
    }
}