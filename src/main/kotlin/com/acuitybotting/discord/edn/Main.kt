package com.acuitybotting.discord.edn

import club.minnced.jda.reactor.asMono
import club.minnced.jda.reactor.createManager
import club.minnced.jda.reactor.on
import com.acuitybotting.discord.edn.games.cah.Cah
import com.acuitybotting.discord.edn.games.ww.Werewolf
import com.acuitybotting.discord.edn.jda.JdaExtensions
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import reactor.core.scheduler.Schedulers
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import kotlin.concurrent.thread

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

    JdaExtensions.jda = JDABuilder(args[0])
        .setEventManager(manager)
        .setActivity(Activity.listening("for commands"))
        .setStatus(OnlineStatus.DO_NOT_DISTURB)
        .setDisabledCacheFlags(EnumSet.allOf(CacheFlag::class.java))
        .setRateLimitPool(executor)
        .setGatewayPool(executor)
        .build()

    Werewolf.add()
    Cah.add()
}