package com.arjun.core.rive


import app.rive.ViewModelInstance

// ── Wraps Rive Android SDK ViewModelInstance ───────────────────────────
class AndroidRiveController(
    private val vmi: ViewModelInstance
) : RiveController {

    override fun setString(propertyName: String, value: String) {
        runCatching { vmi.setString(propertyName, value) }
            .onFailure { logError("setString($propertyName)", it) }
    }

    override fun setEnum(propertyName: String, value: String) {
        runCatching { vmi.setEnum(propertyName, value) }
            .onFailure { logError("setEnum($propertyName)", it) }
    }

    override fun setBoolean(propertyName: String, value: Boolean) {
        runCatching { vmi.setBoolean(propertyName, value) }
            .onFailure { logError("setBoolean($propertyName)", it) }
    }

    override fun setNumber(propertyName: String, value: Float) {
        runCatching { vmi.setNumber(propertyName, value) }
            .onFailure { logError("setNumber($propertyName)", it) }
    }

    override fun fireTrigger(triggerName: String) {
        runCatching { vmi.fireTrigger(triggerName) }
            .onFailure { logError("fireTrigger($triggerName)", it) }
    }

    override fun destroy() {
        runCatching { vmi.release() }
            .onFailure { logError("destroy", it) }
    }

    // Expose raw VMI for advanced use
    fun getRawVmi(): ViewModelInstance = vmi

    private fun logError(fn: String, e: Throwable) {
        println("[RiveController] Error in $fn: ${e.message}")
    }
}