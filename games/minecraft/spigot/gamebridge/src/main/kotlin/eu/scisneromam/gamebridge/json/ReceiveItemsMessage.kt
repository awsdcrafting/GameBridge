package eu.scisneromam.gamebridge.json

import kotlinx.serialization.Serializable

@Serializable
data class ReceiveItemsMessage(val type: String, val sender: List<String>, val target: List<String>, val receiver: String, val items: Map<String, Int>)
