package com.acuitybotting.discord.edn.games.ww

import club.minnced.jda.reactor.on
import com.acuitybotting.discord.edn.jda.Discord
import com.acuitybotting.discord.edn.jda.getInput
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirst
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import reactor.core.Disposable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

data class WerewolfGame(val channel: MessageChannel) {

    private lateinit var job: Job

    private val players = mutableListOf<WerewolfUser>()
    private val alivePlayers: List<WerewolfUser>
        get() = players.filter { it.alive }

    private lateinit var narrator: WerewolfUser

    private var night = 1

    fun start() {
        job = GlobalScope.launch {
            channel.sendMessage("Starting the game now! Type '!ww join' to join as a player. Once everyone has joined the narrator should type '!ww narrator' to start the game.")
                .queue()

            val joinJob = listenForPlayers()

            narrator = getNarrator()
            joinJob.dispose()
            channel.sendMessage("${narrator.name} became the narrator and started the game. Roles will be distributed now.")
                .queue()

            distributeRoles()

            while (isActive) {
                channel.sendMessage("It is night ${night}! If you have a role you will be pmed instructions now!")
                    .queue()

                val werewolfJobs = alivePlayers
                    .filter { it.role == WerewolfRoles.WEREWOLF }
                    .map { async { getWerewolfVote(it) } }

                val doctorJobs = alivePlayers
                    .filter { it.role == WerewolfRoles.DOCTOR }
                    .map { async { getHeal(it) } }

                alivePlayers
                    .filter { it.role == WerewolfRoles.INVESTIGATOR }
                    .map { async { handelInvestigate(it) } }
                    .forEach { it.await() }

                val wwAttackedUser = werewolfJobs
                    .mapNotNull { it.await() }
                    .groupBy { it }
                    .maxBy { it.value.size }
                    .let { it?.key }

                val heals = doctorJobs.mapNotNull { it.await() }

                if (wwAttackedUser != null) {
                    if (heals.contains(wwAttackedUser)) {
                        narrator.sendMessage("${wwAttackedUser.name} was attacked by a werewolf but was healed!")
                    } else {
                        wwAttackedUser.alive = false
                        narrator.sendMessage("${wwAttackedUser.name} was attacked by a werewolf and died.")
                    }
                }

                if (checkWinConditions()) break
                channel.sendMessage("The night is over listen to the narrator to find out what happened and discuss any votes you would like to start now.")
                    .queue()
                handleDay()
                if (checkWinConditions()) break

                night++
            }
        }
    }

    private suspend fun getNarrator(): WerewolfUser {
        return Discord.on<MessageReceivedEvent>()
            .filter { it.message.contentDisplay.startsWith("!ww narrator", true) }
            .map { event ->
                players.removeIf { it.id == narrator.id }

                if (players.size < 4) {
                    channel.sendMessage("There must be at least four players for the game to begin. Once there are four players try again.")
                        .queue()
                    return@map null
                }

                WerewolfUser(
                    -1,
                    event.author
                )
            }.filter { it != null }.awaitFirst()!!
    }

    private fun listenForPlayers(): Disposable {
        val userId = AtomicInteger(1)
        return Discord.on<MessageReceivedEvent>()
            .filter { it.message.contentDisplay.startsWith("!ww join", true) }
            .subscribe { event ->
                if (players.any { it.user.id == event.author.id }) {
                    channel.sendMessage("You've already joined the game.").queue()
                } else {
                    players.add(WerewolfUser(userId.getAndIncrement(), event.author))
                    channel.sendMessage("${event.author.name} joined the game as a player!").queue()
                }
            }
    }

    private fun checkWinConditions(): Boolean {
        if (alivePlayers.all { it.role == WerewolfRoles.WEREWOLF }) {
            channel.sendMessage("Werewolfs win! The roles this game were:\n${getRoleDisplay()}").queue()
            return true
        }

        if (alivePlayers.none { it.role == WerewolfRoles.WEREWOLF }) {
            channel.sendMessage("Villagers win! The roles this game were:\n${getRoleDisplay()}").queue()
            return true
        }

        return false
    }

    private suspend fun handleDay() {
        val validPlayers = alivePlayers

        narrator.sendMessage("Who was hung? (0 to skip)")
        val choice = narrator.privateChannel?.getInput {
            if (it.message.contentDisplay == "0") return@getInput null
            validPlayers.first { user -> user.id == it.message.contentDisplay.toInt() }
        }

        choice?.alive = false
        channel.sendMessage("${choice?.user?.name ?: "No one"} was hung.").queue()
    }

    private suspend fun getWerewolfVote(user: WerewolfUser): WerewolfUser? {
        val validPlayers = alivePlayers.filter { it.role != WerewolfRoles.WEREWOLF }
        if (validPlayers.isEmpty()) return null
        user.sendMessage(validPlayers.toPrompt("Who would you like to attack?"))
        val choice =
            user.privateChannel?.getInput { validPlayers.first { user -> user.id == it.message.contentDisplay.toInt() } }!!
        user.sendMessage("You choose to attack ${choice.name}.")
        return choice
    }

    private suspend fun getHeal(user: WerewolfUser): WerewolfUser? {
        val validPlayers = alivePlayers.filter { it != user || user.specialActionsRemaining > 0 }
        if (validPlayers.isEmpty()) return null
        user.sendMessage(validPlayers.toPrompt("Who would you like to heal? You have ${user.specialActionsRemaining} self heals left."))
        val choice =
            user.privateChannel?.getInput { validPlayers.first { user -> user.id == it.message.contentDisplay.toInt() } }!!
        if (choice == user) user.specialActionsRemaining--
        user.sendMessage("You choose to heal ${choice.name}.")
        return choice
    }

    private suspend fun handelInvestigate(user: WerewolfUser) {
        val validPlayers = alivePlayers.filter { it != user }
        if (validPlayers.isEmpty()) return
        user.sendMessage(validPlayers.toPrompt("Who would you like to investigate?"))
        val choice =
            user.privateChannel?.getInput { validPlayers.first { user -> user.id == it.message.contentDisplay.toInt() } }!!
        narrator.sendMessage("${choice.name} was investigated.")
        user.sendMessage("${choice.name}'s role is ${choice.role.toString().toLowerCase()}!")
    }

    private fun Collection<WerewolfUser>.toPrompt(question: String): String {
        return joinToString(
            prefix = "${question}\n",
            separator = "\n"
        ) { "${it.id}: ${it.name}" }
    }

    private fun distributeRoles() {
        val shuffled = players.shuffled().listIterator()

        shuffled.next().apply {
            role = WerewolfRoles.WEREWOLF
            sendMessage("You are a werewolf!")
        }

        if (Random.nextBoolean()) {
            shuffled.next().apply {
                role = WerewolfRoles.INVESTIGATOR
                sendMessage("You are a investigator!")
            }
        } else {
            shuffled.next().apply {
                role = WerewolfRoles.DOCTOR
                specialActionsRemaining = 1
                sendMessage("You are a doctor!")
            }
        }

        shuffled.forEachRemaining { it.sendMessage("You are a villager.") }

        narrator.sendMessage("The roles for this game are:\n${getRoleDisplay()}")
    }

    private fun getRoleDisplay(): String {
        return players.joinToString("\n") { "${it.name}: ${it.role.toString().toLowerCase()}" }
    }

    fun stop() {
        job.cancel()
    }
}

