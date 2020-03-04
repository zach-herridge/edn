package com.acuitybotting.discord.edn.jda

import com.acuitybotting.discord.edn.DiscordBot
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent


class KChannel {

    val filters = mutableListOf<(MessageReceivedEvent) -> Boolean>()

    fun filter(predicate: (MessageReceivedEvent) -> Boolean): KChannel {
        filters.add(predicate)
        return this
    }

    fun firstAsync() = GlobalScope.async {
        first()
    }

    fun <T> mapAsync(transformer: (MessageReceivedEvent) -> T?) = GlobalScope.async<T?> {
        while (isActive) {
            try {
                return@async transformer.invoke(first())
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        throw IllegalStateException()
    }

    private suspend fun first(): MessageReceivedEvent {
        return DiscordBot.messages.asFlow().first { filters.all { filter -> filter.invoke(it) } }
    }
}
