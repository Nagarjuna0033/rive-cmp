package com.arjun.core.rive

// ── All Rive file + asset configs live here ────────────────────────────
object RiveConfigs {

    // ── File resource names (.riv without extension) ───────────────────
    object Files {

        const val MATCHMAKING = "matchmaking.riv"

        const val CONTEST_NAV = "contest_nav.riv"

        const val KICKER = "kicker.riv"

        const val CONFETTI = "confetti.riv"

        const val STORE = "storeshade.riv"

        const val HOME_BOTTOM_NAV = "home_nav.riv"
        const val TOURNAMENT_BOTTOM_NAV = "contest_nav.riv"
        const val STORE_BOTTOM_NAV = "store_nav.riv"

        const val DEALS_BOTTOM_NAV = "deals_nav.riv"
    }

    // ── Asset IDs — must match IDs exported from Rive editor ──────────
    object AssetIds {
        const val MATCHMAKING_FONT = "Outfit-4229794"

        const val KICKER_GEM_IMAGE = "gemIcon-5704807.png"
        const val KICKER_COIN_IMAGE = "coinIcon-5704813.png"
        const val KICKER_FONT_1 = "Mochiy Pop One-4229287.ttf"
        const val KICKER_FONT_2 = "Do Hyeon-1495980.ttf"
        const val KICKER_FONT_3 = "Outfit-4229794.ttf"
        const val KICKER_FONT_4 = "Oxanium-4229807.ttf"
    }

    // ── Raw resource names (without extension) ─────────────────────────
    object ResourceNames {
        const val FONT_OUTFIT = "rive-outfit.ttf"
        const val KICKER_IC_GEM = "kicker-gem.png"
        const val KICKER_IC_COIN = "kicker-coin.png"
    }

    // ── Per-file configs ───────────────────────────────────────────────

    val matchMaking = RiveFileConfig(
        resourceName = Files.MATCHMAKING,
        assets = listOf(
            RiveAssetConfig(AssetIds.MATCHMAKING_FONT, ResourceNames.FONT_OUTFIT, RiveAssetType.FONT)
        )
    )

    val homeBottomNav = RiveFileConfig(
        resourceName = Files.HOME_BOTTOM_NAV,
        assets = emptyList()
    )

    val storeBottomNav = RiveFileConfig(
        resourceName = Files.STORE_BOTTOM_NAV,
        assets = emptyList()
    )
    val tournamentBottomNav = RiveFileConfig(
        resourceName = Files.TOURNAMENT_BOTTOM_NAV,
        assets = emptyList()
    )

    val dealsBottomNav = RiveFileConfig(
        resourceName = Files.DEALS_BOTTOM_NAV,
        assets = emptyList()
    )

    val kicker = RiveFileConfig(
        resourceName = Files.KICKER,
        assets = listOf(
            RiveAssetConfig(AssetIds.KICKER_FONT_1, ResourceNames.FONT_OUTFIT, RiveAssetType.FONT),
            RiveAssetConfig(AssetIds.KICKER_FONT_2, ResourceNames.FONT_OUTFIT, RiveAssetType.FONT),
            RiveAssetConfig(AssetIds.KICKER_FONT_3, ResourceNames.FONT_OUTFIT, RiveAssetType.FONT),
            RiveAssetConfig(AssetIds.KICKER_FONT_4, ResourceNames.FONT_OUTFIT, RiveAssetType.FONT),
            RiveAssetConfig(AssetIds.KICKER_GEM_IMAGE, ResourceNames.KICKER_IC_GEM, RiveAssetType.IMAGE),
            RiveAssetConfig(AssetIds.KICKER_COIN_IMAGE, ResourceNames.KICKER_IC_COIN, RiveAssetType.IMAGE)
        )
    )

    val confetti = RiveFileConfig(
        resourceName = Files.CONFETTI
    )

    val store = RiveFileConfig(
        resourceName = Files.STORE
    )

    // ── Preload everything at app start ────────────────────────────────
    val allConfigs = listOf(
        matchMaking,
        kicker,
        confetti,
        store,
        homeBottomNav,
        tournamentBottomNav,
        storeBottomNav,
        dealsBottomNav,

    )
}

// ── Pre-built RiveItemConfigs for reuse across features ────────────────

object RiveProps {


    object Common {
        const val VIEWMODEL_NAME = "ViewModel1"

        const val FIRE = "Fire"
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

    object ContestNav {
        const val VIEWMODEL_NAME = "ViewModel1"

        const val FIRE = "Fire"
    }

    object Confetti {
        const val VIEWMODEL_NAME = "ViewModel1"

        const val FIRE = "FIRE"
    }

    object Store {
        const val VIEWMODEL_NAME = "ViewModel1"

        const val FIRE = "Fire"
    }

    object Kicker {

        const val VIEWMODEL_NAME = "Rewards"

        // =========================
        // ROOT PROPERTIES
        // =========================
        const val ITEM_COUNT = "Item_count"
        const val COUNTER = "Counter"
        const val COUNTER_TO_TOTAL_NUMBER = "CounterToTotalNumber"

        const val PRICE_VALUE = "Price_Value"
        const val HEIGHT = "Height"
        const val WIDTH = "With"

        const val COLOR = "Color"

        // =========================
        // PARTICLES
        // =========================
        object Particle {
            const val MAX_X = "ParticlePositionMaximumXVariation"
            const val MAX_Y = "ParticlePositionMaximumYVariation"
            const val START_X = "ParticleStartX"
            const val START_Y = "ParticleStartY"
            const val END_X = "ParticleEndX"
            const val END_Y = "ParticleEndY"
            const val MAX_SCALE = "ParticleMaximumScaleVariance"
            const val MIN_SCALE = "ParticleMinimumScaleVariance"
        }

        // =========================
        // HUD
        // =========================
        object Hud {
            const val TEXT_X = "HudTextXOffset"
            const val TEXT_Y = "HudTextYOffset"
            const val X = "HudXOffset"
            const val Y = "HudYOffset"
        }

        // =========================
        // BONE
        // =========================
        object Bone {
            const val TOP = "BoneTopDistance"
            const val BOTTOM_LEFT = "BoneBottomLeftDistance"
        }

        // =========================
        // ITEM (Enum)
        // =========================
        object Item {

            const val VIEWMODEL_NAME = "Item"
            const val SELECTION = "Item_Selection"

            object Values {
                const val COIN = "Coin"
                const val GEM = "Gem"
            }
        }

        // =========================
        // BUTTON
        // =========================
        object Button {

            const val VIEWMODEL_NAME = "Button"
            const val TEXT = "Item_Text"
            const val STATE = "State_1"
            const val PRESSED = "Pressed"
        }

        // =========================
        // COIN
        // =========================
        object Coin {

            const val VIEWMODEL_NAME = "Coin"

            const val SELECTION = "Item_Selection"
            const val GEM_START = "gemStartValue"
            const val COIN_START = "coinStartValue"

            const val ICON_REACT = "Icon_React"
        }

        // =========================
        // GEM
        // =========================
        object Gem {

            const val VIEWMODEL_NAME = "Gem"

            const val SELECTION = "Item_Selection"
            const val GEM_START = "gemStartValue"
            const val COIN_START = "coinStartValue"

            const val ICON_REACT = "Icon_React"
        }

    }
}


data class RewardsParams(
    val price: Float,
    val itemType: String, // COIN / GEM
    val itemCount: Float,
    val coinStart: Float,
    val gemStart: Float,
    val particleMaxX: Float,
    val particleMaxY: Float,
    val particleStartX: Float,
    val particleStartY: Float,
    val particleEndX: Float,
    val particleEndY: Float,
    val particleMaxScale: Float,
    val particleMinScale: Float,

    val hudTextX: Float,
    val hudTextY: Float,
    val hudX: Float,
    val hudY: Float,
)

data class MatchMakingParams(
    val currentUserName: String,
    val opponentUserName: String,

    val isMatchFound: Boolean = false,
)
object RiveItemConfigs {



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

    fun contestNav() = RiveItemConfig(
        triggers = listOf(
            RiveProps.ContestNav.FIRE
        )
    )

    fun confettiConfig() = RiveItemConfig(
        triggers = listOf(
            RiveProps.Confetti.FIRE
        )
    )

    fun homeBottomBarConfig() = RiveItemConfig(
        triggers = listOf(
            RiveProps.Store.FIRE
        )
    )

    fun storeBottomBarConfig() = RiveItemConfig(
        triggers = listOf(
            RiveProps.Common.FIRE
        ),
    )

    fun contestBottomBarConfig() = RiveItemConfig(
        triggers = listOf(
            RiveProps.Common.FIRE
        ),
    )

    fun dealsBottomBarConfig() = RiveItemConfig(
        triggers = listOf(
            RiveProps.Common.FIRE
        )
    )

    fun rewards(params: RewardsParams) = RiveItemConfig(

        // =========================
        // STRINGS
        // =========================

        // =========================
        // NUMBERS
        // =========================
        numbers = mapOf(

            // Rewards (root VM)
            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.PRICE_VALUE}" to params.price,
            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.ITEM_COUNT}" to params.itemCount,

            // =========================
            // PARTICLES
            // =========================
            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.MAX_X}" to params.particleMaxX,
            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.MAX_Y}" to params.particleMaxY,
            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.START_X}" to params.particleStartX,
            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.START_Y}" to params.particleStartY,
            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.END_X}" to params.particleEndX,
            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.END_Y}" to params.particleEndY,
            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.MAX_SCALE}" to params.particleMaxScale,
            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Particle.MIN_SCALE}" to params.particleMinScale,

            // =========================
            // HUD
            // =========================
            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Hud.TEXT_X}" to params.hudTextX,
            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Hud.TEXT_Y}" to params.hudTextY,
            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Hud.X}" to params.hudX,
            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.Hud.Y}" to params.hudY,

            // Coin
            "${RiveProps.Kicker.Coin.VIEWMODEL_NAME}/${RiveProps.Kicker.Coin.COIN_START}" to params.coinStart,

            // Gem
            "${RiveProps.Kicker.Gem.VIEWMODEL_NAME}/${RiveProps.Kicker.Gem.GEM_START}" to params.gemStart,
        ),

        // =========================
        // ENUMS
        // =========================
        enums = mapOf(

            // THIS IS THE CORRECT FIX
            "${RiveProps.Kicker.Item.VIEWMODEL_NAME}/${RiveProps.Kicker.Item.SELECTION}" to params.itemType
        ),

        // =========================
        // TRIGGERS
        // =========================
        triggers = listOf(

            // Coin animation
            "${RiveProps.Kicker.Coin.VIEWMODEL_NAME}/${RiveProps.Kicker.Coin.ICON_REACT}",

            // Gem animation
            "${RiveProps.Kicker.Gem.VIEWMODEL_NAME}/${RiveProps.Kicker.Gem.ICON_REACT}",

            // Button click
            "${RiveProps.Kicker.Button.VIEWMODEL_NAME}/${RiveProps.Kicker.Button.PRESSED}"
        )
    )

}