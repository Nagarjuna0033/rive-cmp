//package com.arjun.core.rive
//
//import com.apps.common.assets.AssetKey
//import com.apps.common.utils.extractZipFile
//import com.apps.domain.repository.assets.AssetStorageManager
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.IO
//import kotlinx.coroutines.flow.first
//import kotlinx.coroutines.withContext
//import okio.FileSystem
//import okio.Path.Companion.toPath
//import okio.SYSTEM
//
//
//interface AssetStorageManager {
//    suspend fun saveAssetZip(data: ByteArray, version: String): Result<Unit>
//    suspend fun extractAssets(version: String): Result<Unit>
//    suspend fun getAllAssetPaths(): Map<AssetKey, String>
//    suspend fun getCurrentVersion(): String?
//    suspend fun saveVersion(version: Stringring)
//    suspend fun clearOldAssets()
//}
//
//class AssetStorageManagerImpl(
//    private val internalStoragePath: String,
//) : AssetStorageManager {
//
//    private val fileSystem = FileSystem.SYSTEM
//    private val assetsDir = "$internalStoragePath/app_assets".toPath()
//
//    init {
//        if (!fileSystem.exists(assetsDir)) {
//            fileSystem.createDirectories(assetsDir)
//        }
//    }
//
//    override suspend fun saveAssetZip(data: ByteArray, version: String): Result<Unit> =
//        withContext(Dispatchers.IO) {
//            try {
//                val zipPath = "$internalStoragePath/assets_$version.zip".toPath()
//                fileSystem.write(zipPath) {
//                    write(data)
//                }
//                Result.success(Unit)
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//
//    override suspend fun extractAssets(version: String): Result<Unit> =
//        withContext(Dispatchers.IO) {
//            try {
//                val zipPath = "$internalStoragePath/assets_$version.zip".toPath()
//
//                if (!fileSystem.exists(zipPath)) {
//                    return@withContext Result.failure(Exception("Zip file not found"))
//                }
//
//                fileSystem.read(zipPath) {
//                    val zipBytes = readByteArray()
//                    extractZipFile(zipBytes, assetsDir.toString())
//                }
//
//                fileSystem.delete(zipPath)
//
//                Result.success(Unit)
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//
//    override suspend fun getAllAssetPaths(): Map<AssetKey, String> {
//        val pathMap = mutableMapOf<AssetKey, String>()
//
//        AssetKey.entries.forEach { assetKey ->
//            val path = assetsDir / assetKey.path
//            if (fileSystem.exists(path)) {
//                pathMap[assetKey] = path.toString()
//            }
//        }
//
//        return pathMap
//    }
//
//    override suspend fun getCurrentVersion(): String? {
//        return datastoreRepository.getString(ASSET_ID).first()
//    }
//
//    override suspend fun saveVersion(version: String) {
//        datastoreRepository.setString(ASSET_ID, version)
//    }
//
//    override suspend fun clearOldAssets(): Unit = withContext(Dispatchers.IO) {
//        if (fileSystem.exists(assetsDir)) {
//            fileSystem.deleteRecursively(assetsDir)
//            fileSystem.createDirectories(assetsDir)
//        }
//    }
//}