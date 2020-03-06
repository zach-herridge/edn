package com.acuitybotting.discord.edn.games.cah

import club.minnced.jda.reactor.on
import com.acuitybotting.discord.edn.jda.Discord
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

object Cah {

    private val games = mutableMapOf<String, CahGame>()

    fun add() {
        Discord.on<MessageReceivedEvent>()
            .filter { it.message.contentDisplay.startsWith("!cah new game") }
            .subscribe {
                games[it.channel.id]?.stop()
                games[it.channel.id] = CahGame(it.channel).apply { start() }
            }
    }

}