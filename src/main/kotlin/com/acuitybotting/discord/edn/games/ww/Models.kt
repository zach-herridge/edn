package com.acuitybotting.discord.edn.games.ww

import com.acuitybotting.discord.edn.DiscordBot
import kotlinx.coroutines.*
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.PrivateChannel
import net.dv8tion.jda.api.entities.User
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

enum class WerewolfRoles {
    WEREWOLF,
    VILLAGER,
    INVESTIGATOR,
    DOCTOR
}

@ExperimentalCoroutinesApi
@FlowPreview
data class WerewolfGame(val channel: MessageChannel) {

    private lateinit var job: Job
    private val players = mutableListOf<WerewolfUser>()
    private val alivePlayers: List<WerewolfUser>
        get() = players.filter { it.alive }

    private lateinit var narrator: WerewolfUser
    private var night = 1
    private var userId = AtomicInteger(1)

    fun start() {
        job = GlobalScope.launch {
            channel.sendMessage("Starting the game now! Type '!ww join' to join as a player. Once everyone has joined the narrator should type '!ww narrator' to start the game.")
                .queue()

            val joinJob = launch(job) {
                while (isActive) {
                    val event = DiscordBot.channel()
                        .filter { it.message.contentDisplay.startsWith("!ww join", true) }
                        .firstAsync().await()

                    if (players.any { it.user.id == event.author.id }){
                        channel.sendMessage("You've already joined the game.").queue()
                        continue
                    }

                    event.author.openPrivateChannel().queue {
                        players.add(WerewolfUser(userId.getAndIncrement(), event.author, it))
                        channel.sendMessage("${event.author.name} joined the game as a player!").queue()
                    }
                }
            }

            while (isActive) {
                narrator = DiscordBot.channel()
                    .filter { it.message.contentDisplay.startsWith("!ww narrator", true) }
                    .mapAsync {
                        WerewolfUser(
                            userId.getAndIncrement(),
                            it.author,
                            it.author.openPrivateChannel().complete()
                        )
                    }.await()!!

                players.removeIf { it.id == narrator.id }

                if (players.size >= 4) {
                    joinJob.cancel()
                    channel.sendMessage("${narrator.user.name} became the narrator and started the game. Roles will be distributed now.")
                        .queue()
                    break
                } else {
                    channel.sendMessage("There must be at least four players for the game to begin. Once there are four players try again.").queue()
                }
            }


            distributeRoles()

            while (isActive) {
                channel.sendMessage("It is night ${night}! If you have a role you will be pmed instructions now!")
                    .queue()

                val handelInvest = handelInvestAsync()


                val wwVotes = alivePlayers.filter { it.role == WerewolfRoles.WEREWOLF }.map { handelWWAsync(it) }
                val healVotes = alivePlayers.filter { it.role == WerewolfRoles.DOCTOR }.map { handelDoctorAsync(it) }

                handelInvest.await()
                val wwAttackedUser = wwVotes.map { it.await() }.groupBy { it.id }.maxBy { it.value.size }
                    ?.let { alivePlayers.firstOrNull { player -> it.key == player.id } }
                val heals = healVotes.map { it.await() }

                if (wwAttackedUser != null) {
                    if (heals.any { it.id == wwAttackedUser.id }) {
                        narrator.sendMessage("${wwAttackedUser.user.name} was attacked by the werewolf's but was healed!")
                    } else {
                        wwAttackedUser.alive = false
                        narrator.sendMessage("${wwAttackedUser.user.name} was attacked by the werewolf's and died.")
                    }
                }

                if (checkWinConditions()) break

                channel.sendMessage("The night is over listen to the narrator to find out what happened and discuss any votes you would like to start now.")
                    .queue()

                handleVoteAsync().await()
                if (checkWinConditions()) break

                night++
            }
        }
    }

    private fun checkWinConditions(): Boolean {
        if (alivePlayers.count { it.role == WerewolfRoles.WEREWOLF } == alivePlayers.size) {
            channel.sendMessage("The werewolf's have won! The roles this game were:\n${getRoleDisplay()}").queue()
            return true
        }

        if (alivePlayers.count { it.role == WerewolfRoles.WEREWOLF } == 0) {
            channel.sendMessage("The villagers's have won! The roles this game were:\n${getRoleDisplay()}").queue()
            return true
        }

        return false
    }

    private fun handleVoteAsync() = GlobalScope.async {
        val validPlayers = alivePlayers

        val choice = DiscordBot.getInputAsync(
            narrator.privateChannel,
            validPlayers.toPrompt("Who was hung? (0 to skip)")
        ) {
            if (it.message.contentDisplay == "0") return@getInputAsync null
            validPlayers.first { user -> user.id == it.message.contentDisplay.toInt() }
        }

        choice?.alive = false
        channel.sendMessage("${choice?.user?.name ?: "No one"} was hung.").queue()
    }

    private fun handelWWAsync(user: WerewolfUser) = GlobalScope.async {
        val validPlayers = alivePlayers.filter { it.role != WerewolfRoles.WEREWOLF }
        val choice = DiscordBot.getInputAsync(
            user.privateChannel,
            validPlayers.toPrompt("Who would you like to attack?")
        ) { validPlayers.first { user -> user.id == it.message.contentDisplay.toInt() } }!!

        user.sendMessage("You choose to attack ${choice.user.name}.")

        return@async choice
    }

    private fun handelDoctorAsync(user: WerewolfUser) = GlobalScope.async {
        val validPlayers = alivePlayers.filter { it.id != user.id || user.specialActionsRemaining > 0 }

        val choice = DiscordBot.getInputAsync(
            user.privateChannel,
            validPlayers.toPrompt("Who would you like to heal? You have ${user.specialActionsRemaining} self heals left.")
        ) { validPlayers.first { user -> user.id == it.message.contentDisplay.toInt() } }!!

        if (choice.id == user.id) user.specialActionsRemaining--

        user.sendMessage("You choose to heal ${choice.user.name}.")

        return@async choice
    }

    private fun handelInvestAsync() = GlobalScope.async {
        for (user in alivePlayers.filter { it.role == WerewolfRoles.INVESTIGATOR }) {
            val validPlayers = alivePlayers.filter { it.id != user.id }

            launch {
                val choice = DiscordBot.getInputAsync(
                    user.privateChannel,
                    validPlayers.toPrompt("Who would you like to investigate?")
                ) { validPlayers.first { user -> user.id == it.message.contentDisplay.toInt() } }!!

                narrator.sendMessage("${choice.user.name} was investigated.")
                user.sendMessage("${choice.user.name}'s role is ${choice.role.toString().toLowerCase()}!")
            }
        }
    }

    private fun Collection<WerewolfUser>.toPrompt(question: String): String {
        return joinToString(
            prefix = "${question}\n",
            separator = "\n"
        ) { "${it.id}: ${it.user.name}" }
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
        return players.joinToString("\n") { "${it.user.name}: ${it.role.toString().toLowerCase()}" }
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
    var specialActionsRemaining = 0

    fun sendMessage(message: String) {
        privateChannel.sendMessage(message).queue()
    }
}
