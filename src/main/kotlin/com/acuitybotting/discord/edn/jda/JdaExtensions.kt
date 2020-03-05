package com.acuitybotting.discord.edn.jda

import club.minnced.jda.reactor.onMessage
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import reactor.core.publisher.toMono

suspend fun <T : Any> MessageChannel.getInput(userId: String? = null, block: (MessageReceivedEvent) -> T?): T? {
    return onMessage()
        .filter { !it.author.isBot }
        .filter { userId == null || it.author.id == userId }
        .map { block.invoke(it) }
        .doOnError { sendMessage("Invalid input: ${it.localizedMessage}").queue() }
        .retry(100)
        .toMono().awaitFirstOrNull()
}

object JdaExtensions {

    lateinit var jda: JDA
}