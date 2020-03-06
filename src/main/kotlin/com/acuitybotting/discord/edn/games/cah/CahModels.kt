package com.acuitybotting.discord.edn.games.cah

import com.google.gson.Gson
import net.dv8tion.jda.api.entities.User
import java.io.File
import java.io.FileReader

data class CahDeck(val blackCards: Array<CahBlackCard>, val whiteCards: Array<String>) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CahDeck

        if (!blackCards.contentEquals(other.blackCards)) return false
        if (!whiteCards.contentEquals(other.whiteCards)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = blackCards.contentHashCode()
        result = 31 * result + whiteCards.contentHashCode()
        return result
    }

    companion object {

        fun read(file: File): CahDeck {
            return Gson().fromJson(FileReader(file), CahDeck::class.java)
        }

    }
}

data class CahBlackCard(val text: String, val pick: Int)

data class CahPlayer(val user: User, var points: Int = 0) {

    fun sendMessage(message: String) {
        user.openPrivateChannel().queue {
            it.sendMessage(message).queue()
        }
    }

    val hand = mutableListOf<String>()
}