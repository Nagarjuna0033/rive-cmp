package com.arjun.core.rive

import platform.UIKit.UIView

// Wraps a single Rive view instance — subclassed in Swift
open class IOSRiveHandle {
    open fun getUIView(): UIView = UIView()
    open fun setStringProperty(name: String, value: String) {}
    open fun setEnumProperty(name: String, value: String) {}
    open fun setBooleanProperty(name: String, value: Boolean) {}
    open fun setNumberProperty(name: String, value: Float) {}
    open fun fireTrigger(name: String) {}
    open fun destroy() {}
}

// Factory interface — implemented in Swift
interface IOSRiveBridge {
    fun preloadFiles(configs: List<RiveFileConfig>): Boolean
    fun createHandle(resourceName: String): IOSRiveHandle?
    fun isFileLoaded(resourceName: String): Boolean
    fun clearAll()
}

// iOS app registers its bridge here before Compose starts
object IOSRivePlatform {
    var bridge: IOSRiveBridge? = null
}
