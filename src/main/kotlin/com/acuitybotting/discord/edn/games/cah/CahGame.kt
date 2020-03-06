package com.acuitybotting.discord.edn.games.cah

import club.minnced.jda.reactor.onMessage
import com.acuitybotting.discord.edn.jda.getInput
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.collect
import net.dv8tion.jda.api.entities.MessageChannel
import java.io.File

class CahGame(private val baseChannel: MessageChannel) {

    private lateinit var job: Job

    private val deck =
        CahDeck.read(File("/Users/zh072121/Documents/projects/discord_acuity/src/main/resources/cah_base.json"))

    private val blackCards = deck.blackCards.toMutableList()
    private val whiteCards = deck.whiteCards.toMutableList()
    private val players = mutableListOf<CahPlayer>()

    private val handCount = 7
    private var czarIndex = 0

    fun start() {
        blackCards.shuffle()
        whiteCards.shuffle()
        job = GlobalScope.launch {
            baseChannel.sendMessage("Type '!cah join' to join. Once everyone has joined type '!cah start' to begin the game!")
                .queue()

            val joinJob = launch {
                baseChannel.onMessage()
                    .filter { it.message.contentDisplay.startsWith("!cah join") }
                    .collect {
                        if (false /*players.any { player -> it.author.id == player.user.id }*/) {
                            baseChannel.sendMessage("You've already joined.").queue()
                        } else {
                            players.add(CahPlayer(it.author))
                            baseChannel.sendMessage("${it.author.name} joined the game!").queue()
                        }
                    }
            }

            baseChannel.onMessage()
                .filter { it.message.contentDisplay.startsWith("!cah start") }
                .filter {
                    val playerCountMet = players.size >= 0
                    if (!playerCountMet) baseChannel.sendMessage("There must be at least three players for the game to begin.")
                        .queue()
                    playerCountMet
                }.awaitFirst()

            joinJob.cancel()

            baseChannel.sendMessage("Starting the game!").queue()

            while (isActive) {
                deal()
                val blackCard = blackCards.removeAt(0)

                players.forEach { it.sendMessage("Black card:\n${blackCard.text}") }
                val czar = players[czarIndex]

                czar.sendMessage("You are the czar! Wait for the players to pick their cards.")

                val roundPlayers = players.filterIndexed { index, _ -> index != czarIndex }

                val cardJobs = roundPlayers.map { player ->
                    player to async {
                        val hand =
                            player.hand.mapIndexed { index, text -> "${index}: $text" }.joinToString(separator = "\n")
                        player.sendMessage("Pick ${blackCard.pick} card(s) from the list below by typing their numbers separated by commas IE '1' or '2,5'\n${hand}")
                        player.user.openPrivateChannel().complete()
                            .getInput { event ->
                                event.message.contentDisplay.replace(" ", "").split(",").map { player.hand[it.toInt()] }
                            }!!
                    }
                }

                val submissions = cardJobs.map { it.first to it.second.await() }

                for (submission in submissions) {
                    submission.second.forEach { submission.first.hand.remove(it) }
                }

                val submissionDisplay =
                    submissions.mapIndexed { index, submission -> "${index}: ${submission.second.joinToString(separator = ", ")}" }
                        .joinToString(separator = "\n")
                czar.sendMessage("Pick your favorite submission from below by typing its number:\n${submissionDisplay}")
                val input = czar.user.openPrivateChannel().complete()
                    .getInput { submissions[it.message.contentDisplay.toInt()] }!!

                input.first.points++

                roundPlayers.forEach { it.sendMessage("${input.first.user.name} won the round with '${input.second.joinToString(separator = ", ")}'! Here are the current point totals:\n${players.joinToString(separator = "\n") { p -> "${p.user.name}: ${p.points}" }}") }

                if (++czarIndex == players.lastIndex) czarIndex = 0
            }
        }
    }

    private fun deal() {
        players.forEach {
            while (it.hand.size < handCount) it.hand.add(whiteCards.removeAt(0))
        }
    }

    fun stop() {
        job.cancel()
    }
}