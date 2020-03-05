package com.acuitybotting.discord.edn.games.ww

import club.minnced.jda.reactor.on
import com.acuitybotting.discord.edn.jda.Discord
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

object Werewolf {

    private val games = mutableMapOf<String, WerewolfGame>()

    fun add() {
        Discord.on<MessageReceivedEvent>()
            .filter { it.message.contentDisplay.startsWith("!ww new game") }
            .subscribe {
                games[it.channel.id]?.stop()
                games[it.channel.id] = WerewolfGame(it.channel).apply { start() }
            }
    }
}
