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
        const val KICKER_GEM_IMAGE = "gemIcon-5704807"
        const val KICKER_COIN_IMAGE = "coinIcon-5704813"
        const val KICKER_FONT_1 = "Mochiy Pop One-4229287"
        const val KICKER_FONT_2 = "Outfit-4229794"
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

        const val ARTBOARD_HEIGHT = 2223f
        const val ARTBOARD_WIDTH = 1252f

        // ROOT PROPERTIES
        const val ITEM_COUNT = "Item Count"

        const val PRICE_VALUE = "Price Value"

        // PARTICLES
        object Particle {
            const val MAX_X = "Particle Position Maximum X Variation"
            const val MAX_Y = "Particle Position Maximum Y Variation"
            const val START_X = "Particle Start X"
            const val START_Y = "Particle Start Y"
            const val END_X = "Particle End X"
            const val END_Y = "Particle End Y"
            const val MAX_SCALE = "Particle Maximum Scale Variance"
            const val MIN_SCALE = "Particle Minimum Scale Variance"
        }

        // HUD
        object Hud {
            const val TEXT_X = "Hud Text X Offset"
            const val TEXT_Y = "Hud Text Y Offset"
            const val X = "Hud X Offset"
            const val Y = "Hud Y Offset"
        }

        // ITEM (Enum)
        object Item {

            const val VIEWMODEL_NAME = "ItemSelection"
            const val SELECTION = "Item Selection"

            object Values {
                const val COIN = "Coin"
                const val GEM = "Gem"
            }
        }

        // BUTTON
        object Button {

            const val VIEWMODEL_NAME = "Button"
            const val PRESSED = "Pressed"
        }

        // COIN
        object Coin {

            const val VIEWMODEL_NAME = "Coin"
            const val COIN_START_VALUE = "Coin Start Value"

        }

        // GEM
        object Gem {

            const val VIEWMODEL_NAME = "Gem"
            const val GEM_START_VALUE = "Coin Start Value"

        }

    }
}


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

}