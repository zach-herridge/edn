package com.acuitybotting.discord.edn

import com.acuitybotting.discord.edn.games.ww.Werewolf
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter


object DiscordBot {

    lateinit var jda: JDA

    var messages = BroadcastChannel<MessageReceivedEvent>(1)


    fun start() {
        jda.addEventListener(MessageCollector())
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