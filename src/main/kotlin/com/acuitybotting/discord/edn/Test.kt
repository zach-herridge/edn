package com.acuitybotting.discord.edn

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch



suspend fun testStuff(msg: String){
    println("sup $msg")
    delay(2000)
    println("done $msg")
}

fun main() {
    GlobalScope.launch {
        async {  }

    }

    while (true){

    }
}