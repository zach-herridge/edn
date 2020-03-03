package com.acuitybotting.discord.edn.jda

import com.acuitybotting.discord.edn.DiscordBot
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

fun MessageChannel.firstMessage(messagePrefix: String? = null): Deferred<MessageReceivedEvent> = GlobalScope.async {
    DiscordBot.messages.asFlow().first {
        val contentDisplay = it.message.contentDisplay
        if (messagePrefix != null && !contentDisplay.startsWith(messagePrefix, true)) return@first false
        true
    }
}

fun <T> MessageChannel.firstMatch(messagePrefix: String? = null, predicate: (MessageReceivedEvent) -> T): Deferred<T> = GlobalScope.async {
    DiscordBot.messages.asFlow().first {
        val contentDisplay = it.message.contentDisplay
        if (messagePrefix != null && !contentDisplay.startsWith(messagePrefix, true)) return@first false
        predicate.invoke(it)
    }
}