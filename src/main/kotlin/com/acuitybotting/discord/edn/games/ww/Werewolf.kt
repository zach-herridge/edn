package com.acuitybotting.discord.edn.games.ww

import com.acuitybotting.discord.edn.DiscordBot
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object Werewolf {

    private val games = mutableMapOf<String, WerewolfGame>()


    fun start() {
        GlobalScope.launch {
            while (true) {
                val event = async { DiscordBot.messages.asFlow().first { it.message.contentDisplay.startsWith("!werewolf new game") } }.await()
                games[event.channel.id]?.stop()
                games[event.channel.id] = WerewolfGame(event.channel).apply { start() }
            }
        }
    }
}
