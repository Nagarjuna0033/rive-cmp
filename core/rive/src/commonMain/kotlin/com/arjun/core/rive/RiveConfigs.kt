package com.arjun.core.rive

// ── All Rive file + asset configs live here ────────────────────────────
object RiveConfigs {

    // ── File resource names (.riv without extension) ───────────────────
    object Files {
        const val CONTEST_BUTTON    = "primary_button.riv"
    }

    // ── Asset IDs — must match IDs exported from Rive editor ──────────
    object AssetIds {
        const val FONT_OUTFIT       = "Outfit-4229794"
        const val BUTTON_CLICK      = "Button Click-1342551"
        const val IMAGE_COIN        = "Coin-5293203"
        const val IMAGE_CASH        = "Cash-5293204"
        const val IMAGE_LOCK        = "Lock-5293216"
    }

    // ── Raw resource names (without extension) ─────────────────────────
    object ResourceNames {
        const val FONT_OUTFIT = "rive-outfit.ttf"
        const val IMAGE_IC_COIN     = "rive-coin.webp"
        const val IMAGE_IC_CASH     = "rive-cash.webp"
        const val IMAGE_IC_LOCK      = "rive-lock.webp"
        const val AUDIO = "rive-button-audio.flac"
    }

    // ── Per-file configs ───────────────────────────────────────────────
    val contestButton = RiveFileConfig(
        resourceName = Files.CONTEST_BUTTON,
        assets = listOf(
            RiveAssetConfig(AssetIds.FONT_OUTFIT,   ResourceNames.FONT_OUTFIT, RiveAssetType.FONT),
            RiveAssetConfig(AssetIds.IMAGE_COIN,    ResourceNames.IMAGE_IC_COIN, RiveAssetType.IMAGE),
            RiveAssetConfig(AssetIds.IMAGE_CASH,    ResourceNames.IMAGE_IC_CASH, RiveAssetType.IMAGE),
            RiveAssetConfig(AssetIds.IMAGE_LOCK,     ResourceNames.IMAGE_IC_LOCK,  RiveAssetType.IMAGE),
            RiveAssetConfig(AssetIds.BUTTON_CLICK, ResourceNames.AUDIO, RiveAssetType.AUDIO)
        )
    )


    // ── Preload everything at app start ────────────────────────────────
    val allConfigs = listOf(
        contestButton,
    )
}

// ── Pre-built RiveItemConfigs for reuse across features ────────────────

object RiveProps {

    object PrimaryButton {

        const val VIEW_MODEL_NAME = "Button"
        // numbers
        const val BUTTON_WIDTH = "buttonWidth"
        const val CURRENCY = "currency"

        // strings
        const val BUTTON_TEXT = "buttonText"

        // enums
        const val RIGHT_CASH = "rightCash"
        const val RIGHT_COIN = "rightCoin"
        const val SHOW_LOCK_ICON = "showLockIcon"
        const val SHOW_AD_ICON = "showAdIcon"
        const val SHOW_CURRENCY_TEXT = "showCurrencyText"

        // colors
        const val BG_COLOR = "bgColor"
        const val SHADOW_COLOR = "shadowColor"

        // triggers
        const val PRESS = "Press"
        const val LOADING = "Loading"
        const val IDLE = "Idle"
        const val DISABLED = "Disabled"
        const val WIGGLE = "Wiggle"
    }

}


data class PrimaryButtonParams(
    val text: String,
    val currency: Int = 0,
    val width: Float = 186f,

    val showCash: Boolean = false,
    val showCoin: Boolean = false,
    val showLock: Boolean = false,
    val showAd: Boolean = false,
    val showCurrencyText: Boolean = false,

    val bgColor: Int = 0xFFBF0F,
    val shadowColor: Int = 0x000000
)
object RiveItemConfigs {

    fun primaryButton(params: PrimaryButtonParams) = RiveItemConfig(

        strings = mapOf(
            RiveProps.PrimaryButton.BUTTON_TEXT to params.text
        ),

        numbers = mapOf(
            RiveProps.PrimaryButton.BUTTON_WIDTH to params.width,
            RiveProps.PrimaryButton.CURRENCY to params.currency.toFloat()
        ),

        enums = mapOf(
            RiveProps.PrimaryButton.RIGHT_CASH to if (params.showCash) "Show" else "Hide",
            RiveProps.PrimaryButton.RIGHT_COIN to if (params.showCoin) "Show" else "Hide",
            RiveProps.PrimaryButton.SHOW_LOCK_ICON to if (params.showLock) "Show" else "Hide",
            RiveProps.PrimaryButton.SHOW_AD_ICON to if (params.showAd) "Show" else "Hide",
            RiveProps.PrimaryButton.SHOW_CURRENCY_TEXT to if (params.showCurrencyText) "Show" else "Hide"
        ),

        colors = mapOf(
            RiveProps.PrimaryButton.BG_COLOR to params.bgColor,
            RiveProps.PrimaryButton.SHADOW_COLOR to params.shadowColor
        ),

        triggers = listOf(
            RiveProps.PrimaryButton.PRESS
        )
    )
}