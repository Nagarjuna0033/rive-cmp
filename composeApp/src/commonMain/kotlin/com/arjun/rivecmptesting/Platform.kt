package com.arjun.rivecmptesting

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform