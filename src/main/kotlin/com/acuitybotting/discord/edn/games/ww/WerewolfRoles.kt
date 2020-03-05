package com.acuitybotting.discord.edn.games.ww

enum class WerewolfRoles {
    WEREWOLF,
    VILLAGER,
    INVESTIGATOR,
    DOCTOR;

    override fun toString(): String {
        return super.toString().toLowerCase()
    }
}