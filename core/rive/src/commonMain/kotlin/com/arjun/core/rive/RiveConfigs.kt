package com.arjun.core.rive

// ── All Rive file + asset configs live here ────────────────────────────
object RiveConfigs {

    // ── File resource names (.riv without extension) ───────────────────
    object Files {
        const val CONTEST_BUTTON    = "primary_button.riv"
        const val HOME_BANNER       = "home_banner.riv"
        const val PRIZE_TRACK       = "prize_track.riv"
        const val CHEST             = "chest.riv"
    }

    // ── Asset IDs — must match IDs exported from Rive editor ──────────
    object AssetIds {
        const val FONT_OUTFIT       = "Outfit-4229794"
        const val IMAGE_COIN        = "coinIcon-5293203"
        const val IMAGE_CASH        = "cashIcon-5293204"
        const val IMAGE_LOCK        = "lockIcon-5293216"
    }

    // ── Raw resource names (without extension) ─────────────────────────
    object ResourceNames {
        const val FONT_OUTFIT = "outfit.ttf"
        const val IMAGE_IC_COIN     = "coin.webp"
        const val IMAGE_IC_CASH     = "cash.webp"
        const val IMAGE_IC_LOCK      = "lock.webp"
    }

    // ── Per-file configs ───────────────────────────────────────────────
    val contestButton = RiveFileConfig(
        resourceName = Files.CONTEST_BUTTON,
        assets = listOf(
            RiveAssetConfig(AssetIds.FONT_OUTFIT,   ResourceNames.FONT_OUTFIT, RiveAssetType.FONT),
            RiveAssetConfig(AssetIds.IMAGE_COIN,    ResourceNames.IMAGE_IC_COIN, RiveAssetType.IMAGE),
            RiveAssetConfig(AssetIds.IMAGE_CASH,    ResourceNames.IMAGE_IC_CASH, RiveAssetType.IMAGE),
            RiveAssetConfig(AssetIds.IMAGE_LOCK,     ResourceNames.IMAGE_IC_LOCK,  RiveAssetType.IMAGE),
        )
    )

    val homeBanner = RiveFileConfig(
        resourceName = Files.HOME_BANNER,
        assets = listOf(
            RiveAssetConfig(AssetIds.FONT_OUTFIT,   ResourceNames.FONT_OUTFIT, RiveAssetType.FONT),
        )
    )

    val prizeTrack = RiveFileConfig(
        resourceName = Files.PRIZE_TRACK,
        assets = listOf(
            RiveAssetConfig(AssetIds.FONT_OUTFIT,   ResourceNames.FONT_OUTFIT, RiveAssetType.FONT),
            RiveAssetConfig(AssetIds.IMAGE_COIN,    ResourceNames.IMAGE_IC_COIN, RiveAssetType.IMAGE),
        )
    )

    val chest = RiveFileConfig(
        resourceName = Files.CHEST,
        assets = listOf(
            RiveAssetConfig(AssetIds.FONT_OUTFIT,   ResourceNames.FONT_OUTFIT, RiveAssetType.FONT),
        )
    )

    // ── Preload everything at app start ────────────────────────────────
    val allConfigs = listOf(
        contestButton,
//        homeBanner,
//        prizeTrack,
//        chest,
    )
}

// ── Pre-built RiveItemConfigs for reuse across features ────────────────

object RiveProps {

    object ContestButton {

        const val BUTTON_TEXT = "Button Text"
        const val TRAILING_TEXT = "Trailing Text"

        const val RIGHT_CASH = "Right Cash"
        const val RIGHT_COIN = "Right Coin"
        const val SHOW_LOCK_ICON = "Show Lock Icon"
        const val NEW_TAG = "New Tag"

        const val BUTTON_VARIANT = "Button Variant"

        const val IS_LOADING = "Is Loading"
        const val IS_ENABLED = "Is Enabled"

        const val BUTTON_WIDTH = "buttonWidth"

        const val PRESS_TRIGGER = "Press"
    }

}

object RiveItemConfigs {

    // Contest button — dynamic
    fun contestButton(
        buttonText: String,
        showCash: Boolean = false,
        showCoin: Boolean = false,
        showLock: Boolean = false,
        isNew: Boolean = false,
    ) = RiveItemConfig(
        strings = mapOf(
            RiveProps.ContestButton.BUTTON_TEXT to buttonText
        ),
        enums = mapOf(
            RiveProps.ContestButton.RIGHT_CASH to if (showCash) "Show" else "Hide",
            RiveProps.ContestButton.RIGHT_COIN to if (showCoin) "Show" else "Hide",
            RiveProps.ContestButton.SHOW_LOCK_ICON to if (showLock) "Show" else "Hide",
            RiveProps.ContestButton.NEW_TAG to if (isNew) "Show" else "Hide",
        ),
        numbers = mapOf(
            RiveProps.ContestButton.BUTTON_WIDTH to 150f
        ),
        triggers = listOf(
            RiveProps.ContestButton.PRESS_TRIGGER
        )
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