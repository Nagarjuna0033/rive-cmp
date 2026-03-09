package com.arjun.core.rive

class IOSRiveController(
    private val handle: IOSRiveHandle
) : RiveController {

    override fun setString(propertyName: String, value: String) {
        runCatching { handle.setStringProperty(propertyName, value) }
            .onFailure { println("[IOSRiveController] setString($propertyName) error: ${it.message}") }
    }

    override fun setEnum(propertyName: String, value: String) {
        runCatching { handle.setEnumProperty(propertyName, value) }
            .onFailure { println("[IOSRiveController] setEnum($propertyName) error: ${it.message}") }
    }

    override fun setBoolean(propertyName: String, value: Boolean) {
        runCatching { handle.setBooleanProperty(propertyName, value) }
            .onFailure { println("[IOSRiveController] setBoolean($propertyName) error: ${it.message}") }
    }

    override fun setNumber(propertyName: String, value: Float) {
        runCatching { handle.setNumberProperty(propertyName, value) }
            .onFailure { println("[IOSRiveController] setNumber($propertyName) error: ${it.message}") }
    }

    override fun fireTrigger(triggerName: String) {
        runCatching { handle.fireTrigger(triggerName) }
            .onFailure { println("[IOSRiveController] fireTrigger($triggerName) error: ${it.message}") }
    }

    override fun destroy() {
        handle.destroy()
    }
}
