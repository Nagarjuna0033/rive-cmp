package com.arjun.core.rive

// ── All Rive file + asset configs live here ────────────────────────────
object RiveConfigs {

    // ── File resource names (.riv without extension) ───────────────────
    object Files {
        const val CONTEST_BUTTON    = "testing"
        const val HOME_BANNER       = "home_banner"
        const val PRIZE_TRACK       = "prize_track"
        const val CHEST             = "chest"
    }

    // ── Asset IDs — must match IDs exported from Rive editor ──────────
    object AssetIds {
        const val FONT_OUTFIT       = "Outfit-4229794"
        const val IMAGE_COIN        = "1-5293216"
        const val IMAGE_CASH        = "2-5293204"
        const val IMAGE_GEM         = "3-5293203"
    }

    // ── Raw resource names (without extension) ─────────────────────────
    object ResourceNames {
        const val FONT_RAJDHANI = "outfit"
        const val IMAGE_IC_COIN     = "coin"
        const val IMAGE_IC_CASH     = "cash"
        const val IMAGE_IC_GEM      = "lock"
    }

    // ── Per-file configs ───────────────────────────────────────────────
    val contestButton = RiveFileConfig(
        resourceName = Files.CONTEST_BUTTON,
        assets = listOf(
            RiveAssetConfig(AssetIds.FONT_OUTFIT,   ResourceNames.FONT_RAJDHANI, RiveAssetType.FONT),
            RiveAssetConfig(AssetIds.IMAGE_COIN,    ResourceNames.IMAGE_IC_COIN, RiveAssetType.IMAGE),
            RiveAssetConfig(AssetIds.IMAGE_CASH,    ResourceNames.IMAGE_IC_CASH, RiveAssetType.IMAGE),
            RiveAssetConfig(AssetIds.IMAGE_GEM,     ResourceNames.IMAGE_IC_GEM,  RiveAssetType.IMAGE),
        )
    )

    val homeBanner = RiveFileConfig(
        resourceName = Files.HOME_BANNER,
        assets = listOf(
            RiveAssetConfig(AssetIds.FONT_OUTFIT,   ResourceNames.FONT_RAJDHANI, RiveAssetType.FONT),
        )
    )

    val prizeTrack = RiveFileConfig(
        resourceName = Files.PRIZE_TRACK,
        assets = listOf(
            RiveAssetConfig(AssetIds.FONT_OUTFIT,   ResourceNames.FONT_RAJDHANI, RiveAssetType.FONT),
            RiveAssetConfig(AssetIds.IMAGE_COIN,    ResourceNames.IMAGE_IC_COIN, RiveAssetType.IMAGE),
        )
    )

    val chest = RiveFileConfig(
        resourceName = Files.CHEST,
        assets = listOf(
            RiveAssetConfig(AssetIds.FONT_OUTFIT,   ResourceNames.FONT_RAJDHANI, RiveAssetType.FONT),
        )
    )

    // ── Preload everything at app start ────────────────────────────────
    val allConfigs = listOf(
        contestButton,
        homeBanner,
        prizeTrack,
        chest,
    )
}

// ── Pre-built RiveItemConfigs for reuse across features ────────────────
object RiveItemConfigs {

    // Contest button — dynamic
    fun contestButton(
        buttonText: String,
        showCash: Boolean = false,
        showCoin: Boolean = false,
        showLock: Boolean = false,
        isNew: Boolean = false,
    ) = RiveItemConfig(
        strings = mapOf("Button Text" to buttonText),
        enums = buildMap {
            put("Right Cash",      if (showCash) "Show" else "Hide")
            put("Right Coin",      if (showCoin) "Show" else "Hide")
            put("Show Lock Icon",  if (showLock) "Show" else "Hide")
            put("New Tag",         if (isNew)    "Show" else "Hide")
        }
    )

    // Presets
    val pvpCashButton = RiveItemConfig(
        strings = mapOf("Button Text" to "PvP"),
        enums = mapOf("Right Cash" to "Show")
    )

    val pvpCoinButton = RiveItemConfig(
        strings = mapOf("Button Text" to "PvP"),
        enums = mapOf("Right Coin" to "Show")
    )

    val practiceButton = RiveItemConfig(
        strings = mapOf("Button Text" to "Practice"),
        enums = mapOf("Right Coin" to "Show")
    )

    val lockedButton = RiveItemConfig(
        strings = mapOf("Button Text" to "Locked"),
        enums = mapOf("Show Lock Icon" to "Show")
    )
}