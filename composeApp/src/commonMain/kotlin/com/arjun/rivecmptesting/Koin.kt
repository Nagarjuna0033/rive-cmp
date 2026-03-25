package com.arjun.rivecmptesting

import com.arjun.core.rive.riveImageModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.koinApplication

fun koinConfiguration() = koinApplication {
    modules(riveImageModule())
}

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(riveImageModule())
    }
}