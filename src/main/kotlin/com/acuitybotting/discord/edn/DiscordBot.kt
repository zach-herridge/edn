package com.acuitybotting.discord.edn

import com.acuitybotting.discord.edn.games.ww.Werewolf
import com.acuitybotting.discord.edn.jda.KChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.*
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter


@FlowPreview
@ExperimentalCoroutinesApi
object DiscordBot {

    lateinit var jda: JDA

    var messages = BroadcastChannel<MessageReceivedEvent>(1)

    fun start() {
        jda.addEventListener(MessageCollector())
    }

    fun channel() = KChannel()

    suspend fun <T> getInputAsync(
        channel: MessageChannel,
        prompt: String,
        userId: String? = null,
        block: (MessageReceivedEvent) -> T?
    ): T? {

        channel.sendMessage(prompt).queue()

        return messages.asFlow()
            .filter { it.channel.id == channel.id && !it.author.isBot && (userId == null || userId == it.author.id) }
            .map { return@map block.invoke(it) }
            .retryWhen { cause, attempt ->
                channel.sendMessage("Invalid input please try again. ${cause.message}").queue()
                true
            }.first()
    }

    private class MessageCollector : ListenerAdapter() {
        override fun onMessageReceived(event: MessageReceivedEvent) {
            GlobalScope.launch { messages.send(event) }
        }
    }
}

fun main(args: Array<String>) {
    DiscordBot.jda = JDABuilder(args[0]).build().awaitReady()
    DiscordBot.start()
    Werewolf.start()
}