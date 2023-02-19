package eu.scisneromam.gamebridge.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TopicsMessage(val target: List<String>, val type: String, val topics: Topics)

@Serializable
data class Topics(@SerialName("control_send") val controlSend: String, @SerialName("control_receive") val controlReceive: String, val send: String, val receive: String)
