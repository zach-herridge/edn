package com.acuitybotting.discord.edn.games.ww

import club.minnced.jda.reactor.on
import com.acuitybotting.discord.edn.jda.Discord
import com.acuitybotting.discord.edn.jda.getInput
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.collect
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
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
            broadcast("Starting the game now! Type '!ww join' to join as a player. Once everyone has joined the narrator should type '!ww narrator' to start the game.")

            val joinJob = launch(job) { listenForPlayers() }

            narrator = getNarrator()
            joinJob.cancel()
            broadcast("${narrator.name} became the narrator and started the game. Roles will be distributed now.")

            distributeRoles()

            while (isActive) {
                alivePlayers.forEach { it.sendMessage("It is night ${night}! If you have a role you will be sent instructions now!") }

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

                if (gameDecided()) break

                handleDay()

                if (gameDecided()) break

                night++
            }

            broadcast("The game is over thanks for playing!")
        }
    }

    private suspend fun getNarrator(): WerewolfUser {
        return Discord.on<MessageReceivedEvent>()
            .filter { it.message.contentDisplay.startsWith("!ww narrator", true) }
            .map { event ->
                players.removeIf { it.id == narrator.id }

                if (players.size < 4) {
                    broadcast("There must be at least four players for the game to begin. Once there are four players try again.")
                    return@map null
                }

                WerewolfUser(
                    -1,
                    event.author
                )
            }
            .filter { it != null }
            .awaitFirst()!!
    }

    private suspend fun listenForPlayers() {
        val userId = AtomicInteger(1)
        return Discord.on<MessageReceivedEvent>()
            .filter { it.message.contentDisplay.startsWith("!ww join", true) }
            .collect { event ->
                if (players.any { it.user.id == event.author.id }) {
                    broadcast("You've already joined the game.")
                } else {
                    players.add(WerewolfUser(userId.getAndIncrement(), event.author))
                    broadcast("${event.author.name} joined the game as a player!")
                }
            }
    }

    private fun gameDecided(): Boolean {
        if (alivePlayers.all { it.role == WerewolfRoles.WEREWOLF }) {
            players.forEach { it.sendMessage("Werewolfs win! The roles this game were:\n${getRoleDisplay()}") }
            return true
        }

        if (alivePlayers.none { it.role == WerewolfRoles.WEREWOLF }) {
            players.forEach { it.sendMessage("Villagers win! The roles this game were:\n${getRoleDisplay()}") }
            return true
        }

        return false
    }

    private suspend fun handleDay() {
        val validPlayers = alivePlayers
        validPlayers.forEach { it.sendMessage("The night is over listen to the narrator to find out what happened and discuss any votes you would like to start now.") }

        narrator.sendMessage(
            "Inform the town about what happened last night and hold a vote to hang someone. Select who was hung or type 'skip'. Here is a list of alive players ${alivePlayers.joinToString(
                separator = ", "
            ) { it.name }}."
        )
        val choice = narrator.privateChannel?.getInput {
            if (it.message.contentDisplay == "skip") return@getInput null
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

    private suspend fun getHeal(doctor: WerewolfUser): WerewolfUser? {
        val validPlayers = alivePlayers.filter { it != doctor || doctor.specialActionsRemaining > 0 }
        if (validPlayers.isEmpty()) return null
        doctor.sendMessage(validPlayers.toPrompt("Who would you like to heal? You have ${doctor.specialActionsRemaining} self heals left."))
        val choice =
            doctor.privateChannel?.getInput { validPlayers.first { user -> user.id == it.message.contentDisplay.toInt() } }!!
        if (choice == doctor) doctor.specialActionsRemaining--
        doctor.sendMessage("You choose to heal ${choice.name}.")
        return choice
    }

    private suspend fun handelInvestigate(investigator: WerewolfUser) {
        val validPlayers = alivePlayers.filter { it != investigator }
        if (validPlayers.isEmpty()) return
        investigator.sendMessage(validPlayers.toPrompt("Who would you like to investigate?"))
        val choice =
            investigator.privateChannel?.getInput { validPlayers.first { user -> user.id == it.message.contentDisplay.toInt() } }!!
        investigator.sendMessage("${choice.name}'s role is ${choice.role}!")
    }

    private fun Collection<WerewolfUser>.toPrompt(prompt: String): String {
        return joinToString(prefix = "${prompt}\n", separator = "\n") { "${it.id}: ${it.name}" }
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

    private fun broadcast(message: String){
        channel.sendMessage(message).queue()
    }

    private fun getRoleDisplay(): String {
        return players.joinToString("\n") { "${it.name}: ${it.role}" }
    }

    fun stop() {
        job.cancel()
    }
}