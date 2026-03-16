package com.arjun.core.rive

// ── Asset Types ────────────────────────────────────────────────────────
enum class RiveAssetType { IMAGE, FONT, AUDIO }

// ── Asset Config ───────────────────────────────────────────────────────
data class RiveAssetConfig(
    val assetId: String,          // ID exported from Rive editor e.g. "1-5293216"
    val resourceName: String,     // raw file name without extension e.g. "ic_coin1"
    val type: RiveAssetType
)

// ── File Config ────────────────────────────────────────────────────────
data class RiveFileConfig(
    val resourceName: String,     // .riv file name without extension
    val assets: List<RiveAssetConfig> = emptyList()
)

// ── Item Config ────────────────────────────────────────────────────────
data class RiveItemConfig(
    val strings: Map<String, String> = emptyMap(),
    val enums: Map<String, String> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val numbers: Map<String, Float> = emptyMap(),
    val triggers: List<String> = emptyList(),
    val colors: Map<String, Int> = emptyMap()
)

// ── Load State ─────────────────────────────────────────────────────────
sealed class RiveLoadState {
    data object Idle : RiveLoadState()
    data object Loading : RiveLoadState()
    data object Success : RiveLoadState()
    data class Error(val message: String) : RiveLoadState()
}

// ── Rive Event ─────────────────────────────────────────────────────────
data class RiveEvent(
    val name: String,
    val properties: Map<String, Any> = emptyMap()
)