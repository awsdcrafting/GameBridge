package eu.scisneromam.gamebridge.json

import eu.scisneromam.gamebridge.Constants
import eu.scisneromam.gamebridge.MessageTypes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChestMessage(@SerialName("chest_id") val chestID: String, val sender: List<String>) {
    val type: String = MessageTypes.CHEST
}
