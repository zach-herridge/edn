package com.acuitybotting.discord.edn.games.ww

import com.acuitybotting.discord.edn.DiscordBot
import kotlinx.coroutines.*
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.PrivateChannel
import net.dv8tion.jda.api.entities.User
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
    private lateinit var narrator: WerewolfUser
    private var night = 1
    private var userId = AtomicInteger(0)

    fun start() {
        job = GlobalScope.launch {
            channel.sendMessage("Starting the game now! Type '!werewolf join' to join as a player. Once everyone has join the narrator should type '!werewolf narrator' to start the game.")
                .queue()

            val joinJob = launch {
                while (isActive) {
                    val event = DiscordBot.channel()
                        .filter { it.message.contentDisplay.startsWith("!werewolf join", true) }
                        .firstAsync().await()

                    event.author.openPrivateChannel().queue {
                        players.add(WerewolfUser(userId.getAndIncrement(), event.author, it))
                        channel.sendMessage("${event.author.name} joined the game as a player!").queue()
                    }
                }
            }

            narrator = DiscordBot.channel()
                .filter { it.message.contentDisplay.startsWith("!werewolf narrator", true) }
                .mapAsync {
                    WerewolfUser(
                        userId.getAndIncrement(),
                        it.author,
                        it.author.openPrivateChannel().complete()
                    )
                }.await()

            joinJob.cancel()
            channel.sendMessage("${narrator.user.name} became the narrator and started the game. Roles will be distributed now.")
                .queue()

            distributeRoles()

            while (isActive) {
                channel.sendMessage("It is night ${night}! If you have a role you will be pmed instructions now!")
                    .queue()

                val handelInvest = handelInvestAsync()
                val handleWw = handelWWAsync()

                handleWw.await()
                handelInvest.await()

                if (checkWinConditions()) break

                channel.sendMessage("When discussion and voting are done the narrator can move to the next night by typing '!werewolf hang' or '!werewolf skip'. ")
                    .queue()

                val nChoice = DiscordBot.channel()
                    .filter {
                        it.author.id == narrator.user.id && (it.message.contentDisplay.startsWith(
                            "!werewolf hang",
                            true
                        ) || it.message.contentDisplay.startsWith(
                            "!werewolf skip",
                            true
                        ))
                    }
                    .firstAsync().await()

                if (nChoice.message.contentDisplay.contains("hang")) handleVoteAsync().await()

                if (checkWinConditions()) break

                night++
            }
        }
    }

    private fun checkWinConditions(): Boolean {
        if (players.count { it.alive && it.role == WerewolfRoles.VILLAGER } <= players.count { it.alive && it.role == WerewolfRoles.WEREWOLF }){
            channel.sendMessage("The werewolf's have won! The roles this game were:\n${getRoleDisplay()}").queue()
            return true
        }

        if (players.count { it.alive && it.role == WerewolfRoles.WEREWOLF } == 0){
            channel.sendMessage("The villagers's have won! The roles this game were:\n${getRoleDisplay()}").queue()
            return true
        }

        return false
    }

    private fun handleVoteAsync() = GlobalScope.async {
        val display = players.filter { it.alive }.joinToString(
            prefix = "Who was hung?\n",
            separator = "\n"
        ) { "${it.id}: ${it.user.name}" }
        channel.sendMessage(display).queue()
        val choice = DiscordBot.channel()
            .mapAsync { players.first { user -> user.id == it.message.contentDisplay.toInt() } }.await()
        choice.alive = false
    }

    private fun handelWWAsync() = GlobalScope.async {
        val user = players.first { it.role == WerewolfRoles.WEREWOLF }

        val filter = players.filter { it.id != user.id && it.alive }
        val display = filter.joinToString(
            prefix = "Who would you like to kill?\n",
            separator = "\n"
        ) { "${it.id}: ${it.user.name}" }
        user.privateChannel.sendMessage(display).queue()
        val choice = DiscordBot.channel()
            .filter { it.channel.id == user.privateChannel.id && !it.author.isBot }
            .mapAsync { filter.first { user -> user.id == it.message.contentDisplay.toInt() } }.await()
        choice.alive = false
        narrator.privateChannel.sendMessage("${choice.user.name} was killed last night.").queue()
    }

    private fun handelInvestAsync() = GlobalScope.async {
        val user = players.first { it.role == WerewolfRoles.INVESTIGATOR }
        val filter = players.filter { it.id != user.id && it.alive }
        val display = filter.joinToString(
            prefix = "Who would you like to investigate?\n",
            separator = "\n"
        ) { "${it.id}: ${it.user.name}" }
        user.privateChannel.sendMessage(display).queue()
        val choice = DiscordBot.channel()
            .filter { it.channel.id == user.privateChannel.id && !it.author.isBot }
            .mapAsync { filter.first { user -> user.id == it.message.contentDisplay.toInt() } }.await()
        user.privateChannel.sendMessage("${choice.user.name}'s role is ${choice.role.toString().toLowerCase()}!")
            .queue()
    }

    private fun distributeRoles() {
        val shuffled = players.shuffled().listIterator()
        shuffled.next().apply {
            role = WerewolfRoles.WEREWOLF
            privateChannel.sendMessage("You are the werewolf!").queue()
        }
        shuffled.next().apply {
            role = WerewolfRoles.INVESTIGATOR
            privateChannel.sendMessage("You are the investigator!").queue()
        }
        shuffled.forEachRemaining { it.privateChannel.sendMessage("You are a villager.").queue() }

        narrator.privateChannel.sendMessage("The roles for this game are:\n${getRoleDisplay()}").queue()
    }

    private fun getRoleDisplay(){
        return  players.joinToString("\n") { "${it.user.name}: ${it.role.toString().toLowerCase()}" }
    }

    fun stop() {
        job.cancel()
    }
}

class WerewolfUser(
    val id: Int,
    val user: User,
    val privateChannel: PrivateChannel
) {
    var role: WerewolfRoles = WerewolfRoles.VILLAGER
    var alive = true
}
