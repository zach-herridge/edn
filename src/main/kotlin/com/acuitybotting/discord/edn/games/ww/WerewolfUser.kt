package com.acuitybotting.discord.edn.games.ww

import net.dv8tion.jda.api.entities.PrivateChannel
import net.dv8tion.jda.api.entities.User

class WerewolfUser(
    val id: Int,
    val user: User
) {

    var privateChannel: PrivateChannel? = null
        get() {
            if (field == null) field = user.openPrivateChannel().complete()
            return field
        }
    var role: WerewolfRoles =
        WerewolfRoles.VILLAGER
    var alive = true
    var specialActionsRemaining = 0
    val name = user.name

    fun sendMessage(message: String) {
        privateChannel?.sendMessage(message)?.queue()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WerewolfUser

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id
    }
}