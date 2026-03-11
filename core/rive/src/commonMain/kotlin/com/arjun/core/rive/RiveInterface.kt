package com.arjun.core.rive

// ── Controller ─────────────────────────────────────────────────────────
// Abstraction over platform VMI / RiveViewModel
interface RiveController {
    fun setString(propertyName: String, value: String)
    fun setEnum(propertyName: String, value: String)
    fun setBoolean(propertyName: String, value: Boolean)
    fun setNumber(propertyName: String, value: Float)
    fun fireTrigger(triggerName: String)

    fun applyConfig(config: RiveItemConfig) {
        config.strings.forEach  { (k, v) -> setString(k, v) }
        config.enums.forEach    { (k, v) -> setEnum(k, v) }
        config.booleans.forEach { (k, v) -> setBoolean(k, v) }
        config.numbers.forEach  { (k, v) -> setNumber(k, v) }
//        config.triggers.forEach { fireTrigger(it) }
    }

    fun destroy()
}

// ── Callbacks ──────────────────────────────────────────────────────────
interface RiveEventCallback {
    fun onRiveEventReceived(event: RiveEvent) {}
    fun onStateChanged(stateMachineName: String, stateName: String) {}
    fun onTriggerAnimation(animationName: String) {}
    fun onAnimationEnd(animationName: String) {}
    fun onError(error: String) {}
}

// ── File Manager ───────────────────────────────────────────────────────
interface RiveFileManager {
    suspend fun preloadFile(config: RiveFileConfig): RiveLoadState
    suspend fun preloadAll(configs: List<RiveFileConfig>): RiveLoadState
    fun isFileLoaded(resourceName: String): Boolean
    fun getLoadState(resourceName: String): RiveLoadState
    fun clearAll()
}