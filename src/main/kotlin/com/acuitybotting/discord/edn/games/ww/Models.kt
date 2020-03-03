package com.acuitybotting.discord.edn.games.ww

import com.acuitybotting.discord.edn.jda.firstMessage
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.PrivateChannel
import net.dv8tion.jda.api.entities.User
import java.util.concurrent.TimeUnit

object WerewolfGameState {

    const val STARTUP = 0
    const val PLAY = 1
}

enum class WerewolfRoles {

    WEREWOLF,
    VILLAGER,
    INVESTIGATOR

}

data class WerewolfGame(val channel: MessageChannel) {

    private lateinit var job: Job
    private val players = mutableListOf<WerewolfUser>()
    private lateinit var narratorChannel: PrivateChannel
    private var night = 0

    fun start() {
        job = GlobalScope.launch {
            channel.sendMessage("Game starting! Type '!werewolf join' to join as a player. When all players have joined the narrator should type '!werewolf begin' to start the game.")
                .queue()

            val joinJob = launch {
                while (isActive) {
                    val event = channel.firstMessage("!werewolf join").await()
                    event.author.openPrivateChannel().queue {
                        players.add(WerewolfUser(event.author, it))
                        channel.sendMessage("${event.author.name} has joined as a player!").queue()
                    }
                }
            }

            while (isActive) {
                val event = channel.firstMessage("!werewolf begin").await()

                players.removeIf { it.user.id == event.author.id }

                if (players.size < 4) {
                    channel.sendMessage("Sorry the game cannot begin with less than four players.").queue()
                    continue
                }

                val privateChannel = event.author.openPrivateChannel().completeAfter(10, TimeUnit.SECONDS)
                if (privateChannel != null) {
                    narratorChannel = privateChannel
                    channel.sendMessage("${event.author.name} has became the narrator and has started the game! Roles will be distributed now.")
                        .queue()
                    joinJob.cancel()
                    break
                }
            }

            distributeRoles()

            while (isActive) {
                channel.sendMessage("Starting night $night. If you have a role you will be sent a message with instructions!").queue()

                val werewolfChoice =
                    players.first { it.role == WerewolfRoles.WEREWOLF }.privateChannel
                        .firstMessage("!kill ")
                        .await()

                println()

            }
        }
    }

    private fun messsageWerewolfs(){

    }

    private fun distributeRoles() {
        val listIterator = players.shuffled().listIterator()

        listIterator.next().apply {
            role = WerewolfRoles.WEREWOLF
            privateChannel.sendMessage("You are the werewolf!").queue()
        }

        listIterator.next().apply {
            role = WerewolfRoles.INVESTIGATOR
            privateChannel.sendMessage("You are the investigator!").queue()
        }

        listIterator.forEachRemaining { it.privateChannel.sendMessage("You are a villager.").queue() }

        val rolesDisplay = players.joinToString("\n") { "${it.user.name}: ${it.role.toString().toLowerCase()}" }
        narratorChannel.sendMessage("Roles for this game:\n${rolesDisplay}").queue()
    }

    fun stop() {
        job.cancel()
    }
}

class WerewolfUser(val user: User, val privateChannel: PrivateChannel) {
    var role: WerewolfRoles = WerewolfRoles.VILLAGER
}