package eu.scisneromam.gamebridge.json

import eu.scisneromam.gamebridge.MessageTypes
import kotlinx.serialization.Serializable

@Serializable
data class SendItemsMessage(val sender: List<String>, val receiver: String, val items: Map<String, Int>) {
    val type : String = MessageTypes.SEND
}
