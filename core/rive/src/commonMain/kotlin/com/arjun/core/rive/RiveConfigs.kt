package com.arjun.core.rive

// ── All Rive file + asset configs live here ────────────────────────────
object RiveConfigs {

    // ── File resource names (.riv without extension) ───────────────────
    object Files {
        const val PRIMARY_BUTTON    = "primary_button_v1.2.riv"

        const val DOWNLOAD_BUTTON = "download_button.riv"

        const val MATCHMAKING = "matchmaking.riv"

        const val SWORD_NAV = "sword_nav.riv"
    }

    // ── Asset IDs — must match IDs exported from Rive editor ──────────
    object AssetIds {
        const val FONT_OUTFIT       = "Outfit-4229794"
        const val BUTTON_CLICK      = "Button Click-1342551"
        const val IMAGE_COIN        = "Coin Icon-5293203"
        const val IMAGE_CASH        = "Cash Icon-5293204"
        const val IMAGE_LOCK        = "Lock Icon-5293216"

        const val IMAGE_AD = "Ad Icon-5654378.webp"

        const val FONT = "Inter-995149"

        const val MATCHMAKING_FONT = "Outfit-4229794"
    }

    // ── Raw resource names (without extension) ─────────────────────────
    object ResourceNames {
        const val FONT_OUTFIT = "rive-outfit.ttf"
        const val IMAGE_IC_COIN     = "rive-cash.webp"
        const val IMAGE_IC_CASH     = "rive-coin.webp"
        const val IMAGE_IC_LOCK      = "rive-lock.webp"
        const val AUDIO = "rive-button-audio.flac"

        const val IMAGE_IC_AD = "rive-lock.webp"
    }

    // ── Per-file configs ───────────────────────────────────────────────
    val contestButton = RiveFileConfig(
        resourceName = Files.PRIMARY_BUTTON,
        assets = listOf(
            RiveAssetConfig(AssetIds.FONT_OUTFIT,   ResourceNames.FONT_OUTFIT, RiveAssetType.FONT),
            RiveAssetConfig(AssetIds.IMAGE_COIN,    ResourceNames.IMAGE_IC_COIN, RiveAssetType.IMAGE),
            RiveAssetConfig(AssetIds.IMAGE_CASH,    ResourceNames.IMAGE_IC_CASH, RiveAssetType.IMAGE),
            RiveAssetConfig(AssetIds.IMAGE_LOCK,     ResourceNames.IMAGE_IC_LOCK,  RiveAssetType.IMAGE),
            RiveAssetConfig(AssetIds.IMAGE_AD, ResourceNames.IMAGE_IC_AD, RiveAssetType.IMAGE),
            RiveAssetConfig(AssetIds.BUTTON_CLICK, ResourceNames.AUDIO, RiveAssetType.AUDIO)
        )
    )

    val downloadButton = RiveFileConfig(
        resourceName = Files.DOWNLOAD_BUTTON,
        assets = listOf(
            RiveAssetConfig(AssetIds.FONT, ResourceNames.FONT_OUTFIT, RiveAssetType.FONT)
        )
    )

    val matchMaking = RiveFileConfig(
        resourceName = Files.MATCHMAKING,
        assets = listOf(
            RiveAssetConfig(AssetIds.MATCHMAKING_FONT, ResourceNames.FONT_OUTFIT, RiveAssetType.FONT)
        )
    )

    val sword = RiveFileConfig(
        resourceName = Files.SWORD_NAV
    )

    // ── Preload everything at app start ────────────────────────────────
    val allConfigs = listOf(
        contestButton,
//        downloadButton,
        matchMaking,
//        sword
    )
}

// ── Pre-built RiveItemConfigs for reuse across features ────────────────

object RiveProps {

    object PrimaryButton {

        const val VIEW_MODEL_NAME = "Button"

        // numbers
        const val BUTTON_WIDTH = "Button Width"
        const val CURRENCY_TEXT = "Currency Text"

        // strings
        const val BUTTON_TEXT = "Button Text"

        // enums

        const val BUTTON_COLOR = "Button Color"

        // booleans
        const val IS_LOADING = "Loading"

        const val IS_ENABLED = "Enabled"
        const val RIGHT_CASH_DISPLAY = "Right Cash Display"
        const val RIGHT_COIN_DISPLAY = "Right Coin Display"
        const val LOCK_ICON_DISPLAY = "Lock Icon Display"
        const val AD_ICON_DISPLAY = "Ad Icon Display"
        const val CURRENCY_TEXT_DISPLAY = "Currency Text Display"

        // triggers
        const val PRESS = "Press"
        const val LOADING = "Loading"
        const val WIGGLE = "Wiggle"
    }

    object MatchMaking {

        const val VIEWMODEL_NAME = "ViewModel1"
        const val CURRENT_USER_NAME = "Current User Name"
        const val OPPONENT_USER_NAME = "Opponent User Name"
        const val CURRENT_USER_PICTURE = "Current User Picture"
        const val OPPONENT_USER_PICTURE = "Opponent User Picture"

        const val MATCH_FOUND = "Match Found"

        const val INTRO = "Intro"
        const val OUTRO = "Outro"
    }
}


data class PrimaryButtonParams(
    val text: String,

    val currency: String = "",
    val width: Float = 0f,
    val buttonColor: String,

    val showCash: Boolean = false,
    val showCoin: Boolean = false,
    val showLock: Boolean = false,
    val showAd: Boolean = false,
    val showCurrencyText: Boolean = false,

    val isLoading: Boolean = false,
    val isEnabled: Boolean = false,
)


data class MatchMakingParams(
    val currentUserName: String,
    val opponentUserName: String,

    val isMatchFound: Boolean = false,
)
object RiveItemConfigs {

    fun primaryButton(params: PrimaryButtonParams) = RiveItemConfig(

        strings = mapOf(
            RiveProps.PrimaryButton.BUTTON_TEXT to params.text,
            RiveProps.PrimaryButton.CURRENCY_TEXT to params.currency
        ),

//        numbers = mapOf(
//            RiveProps.PrimaryButton.BUTTON_WIDTH to params.width,
//        ),

        booleans = mapOf(
            RiveProps.PrimaryButton.IS_LOADING to params.isLoading,
            RiveProps.PrimaryButton.IS_ENABLED to params.isEnabled
        ),

        enums = mapOf(
            RiveProps.PrimaryButton.RIGHT_CASH_DISPLAY to if (params.showCash) "Show" else "Hide",
            RiveProps.PrimaryButton.RIGHT_COIN_DISPLAY to if (params.showCoin) "Show" else "Hide",
            RiveProps.PrimaryButton.LOCK_ICON_DISPLAY to if (params.showLock) "Show" else "Hide",
            RiveProps.PrimaryButton.AD_ICON_DISPLAY to if (params.showAd) "Show" else "Hide",

            RiveProps.PrimaryButton.CURRENCY_TEXT_DISPLAY to if (params.showCurrencyText) "Show" else "Hide",
            RiveProps.PrimaryButton.BUTTON_COLOR to params.buttonColor
        ),

        triggers = listOf(
            RiveProps.PrimaryButton.PRESS,
            RiveProps.PrimaryButton.LOADING,
            RiveProps.PrimaryButton.WIGGLE,
        )
    )

    fun matchMaking(params: MatchMakingParams) = RiveItemConfig(
        strings = mapOf(
            RiveProps.MatchMaking.CURRENT_USER_NAME to params.currentUserName,
            RiveProps.MatchMaking.OPPONENT_USER_NAME to params.opponentUserName
        ),

        booleans = mapOf(
            RiveProps.MatchMaking.MATCH_FOUND to params.isMatchFound
        ),

        triggers = listOf(
            RiveProps.MatchMaking.INTRO,
            RiveProps.MatchMaking.OUTRO
        )
    )
}