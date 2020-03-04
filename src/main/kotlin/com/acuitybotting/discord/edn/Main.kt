package com.acuitybotting.discord.edn

import club.minnced.jda.reactor.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import reactor.core.publisher.toMono
import reactor.core.scheduler.Schedulers
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import kotlin.concurrent.thread

suspend fun <T : Any> MessageChannel.getInput(userId: String, block: (MessageReceivedEvent) -> T): T? {
    try {
        return onMessage()
            .filter { it.author.id == userId }
            .map { block.invoke(it) }
            .doOnError { sendMessage("invalid input").queue() }
            .retry(3)
            .toMono()
            .asFlow()
            .singleOrNull()
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return null
}

fun main(args: Array<String>) {
    var count = 0
    val executor = Executors.newScheduledThreadPool(ForkJoinPool.getCommonPoolParallelism()) {
        thread(start = false, block = it::run, name = "jda-thread-${count++}", isDaemon = true)
    }

    val schedulerWrap = Schedulers.fromExecutor(executor)
    val manager = createManager {
        scheduler = schedulerWrap
        isDispose = false
    }

    manager.on<ReadyEvent>()
        .next()
        .map { it.jda }
        .doOnSuccess { it.presence.setStatus(OnlineStatus.ONLINE) }
        .flatMap { it.retrieveApplicationInfo().asMono() }
        .map { it.owner.idLong }
        .flatMapMany { ownerId ->
            manager.on<MessageReceivedEvent>()
                .filter { it.author.idLong == ownerId }
        }
        .filter { it.message.contentRaw == "!shutdown" }
        .map(MessageReceivedEvent::getJDA)
        .subscribe {
            it.shutdown()
            it.httpClient.connectionPool().evictAll()
        }

    val jda = JDABuilder(args[0])
        .setEventManager(manager)
        .setActivity(Activity.listening("for commands"))
        .setStatus(OnlineStatus.DO_NOT_DISTURB)
        .setDisabledCacheFlags(EnumSet.allOf(CacheFlag::class.java))
        .setRateLimitPool(executor)
        .setGatewayPool(executor)
        .build()

    GlobalScope.launch {
        jda.on<MessageReceivedEvent>()
            .asFlow()
            .filter { it.message.contentDisplay.startsWith("!purge") }
            .collect { event ->
                event.channel.iterableHistory.asFlux()
                    .takeWhile { it.timeCreated.until(OffsetDateTime.now(), ChronoUnit.DAYS) < 30 }
                    .buffer(100)
                    .flatMap { event.channel.purgeMessages(it).asFlux() }
                    .awaitLast()
            }
    }

    GlobalScope.launch {
        jda.on<MessageReceivedEvent>()
            .asFlow()
            .filter { it.message.contentDisplay.startsWith("!hi") }
            .collect { event ->
                GlobalScope.launch {
                    val num =
                        async { withTimeoutOrNull(10000) { event.channel.getInput(event.author.id) { it.message.contentDisplay.toInt() } } }
                    event.channel.sendMessage("pick a number:").queue()
                    event.channel.sendMessage("you picked ${num.await()}").queue()
                }
            }
    }
}