package com.arjun.core.rive

class IOSRiveFileManager(
    private val bridge: IOSRiveBridge
) : RiveFileManager {

    override suspend fun preloadFile(config: RiveFileConfig): RiveLoadState {
        return try {
            val success = bridge.preloadFiles(listOf(config))
            if (success) RiveLoadState.Success
            else RiveLoadState.Error("Failed to preload ${config.resourceName}")
        } catch (e: Exception) {
            RiveLoadState.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun preloadAll(configs: List<RiveFileConfig>): RiveLoadState {
        return try {
            val success = bridge.preloadFiles(configs)
            if (success) RiveLoadState.Success
            else RiveLoadState.Error("Failed to preload files")
        } catch (e: Exception) {
            RiveLoadState.Error(e.message ?: "Unknown error")
        }
    }

    override fun isFileLoaded(resourceName: String): Boolean =
        bridge.isFileLoaded(resourceName)

    override fun getLoadState(resourceName: String): RiveLoadState =
        if (bridge.isFileLoaded(resourceName)) RiveLoadState.Success
        else RiveLoadState.Idle

    override fun clearAll() = bridge.clearAll()
}
