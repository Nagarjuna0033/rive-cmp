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

        const val KICKER_GEM_IMAGE = "gemlcon-5704807.png"
        const val KICKER_COIN_IMAGE = "coinlcon-5704813.png"
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

    // =========================
    // Rewards ViewModel
    // =========================
    object Kicker {

        const val VIEWMODEL_NAME = "Rewards"

        // Numbers
        const val SPAWN_AMOUNT = "SpawnAmount"
        const val NUMBER_OF_COINS_OR_GEMS = "NumberOfCoinsOrGems"
        const val COUNTER_TO_TOTAL_NUMBER = "CounterToTotalNumber"
        const val COUNTER = "Counter"
        const val COIN_ITEM_COUNT = "Coin_Item_Count"
        const val GEM_ICON_COUNT = "Gem_Icon_Count"

        const val BONE_TOP_DISTANCE = "BoneTopDistance"
        const val BONE_BOTTOM_LEFT_DISTANCE = "BoneBottomLeftDistance"
        const val COIN_BONE_TOP_LEFT_DISTANCE = "CoinBoneTopLeftDistance"
        const val GEM_BONE_TOP_LEFT_DISTANCE = "GemBoneTopLeftDistance"

        const val PRICE_VALUE = "Price_Value"
        const val HEIGHT = "Height"
        const val WIDTH = "With"

        // Colors
        const val COLOR = "Color"
        const val BAR_COLOR = "Bar_Color"

        // Energy
        const val LIVES = "Lives"
        const val ENERGY_BAR = "Energy_Bar"

        // Enum
        const val ITEM_SELECTION = "Item_Selection"

        object ItemSelectionValues {
            const val VIEWMODEL_NAME = "Item"
            const val COIN = "Coin"
            const val GEM = "Gem"
        }
    }

    // =========================
    // Button ViewModel
    // =========================
    object Button {

        const val VIEWMODEL_NAME = "Button"
        const val TEXT = "Item_Text"

        const val PRESSED = "Pressed"
    }

    // =========================
    // Coin ViewModel
    // =========================
    object Coin {

        const val VIEWMODEL_NAME = "Coin"

        const val ITEM_SELECTION = "Item_Selection"
        const val GEM_START_VALUE = "gemStartValue"
        const val COIN_START_VALUE = "coinStartValue"

        const val ICON_REACT = "Icon_React"
    }

    // =========================
    // Gem ViewModel
    // =========================
    object Gem {

        const val VIEWMODEL_NAME = "Gem"

        const val ITEM_SELECTION = "Item_Selection"
        const val GEM_START_VALUE = "gemStartValue"
        const val COIN_START_VALUE = "coinStartValue"
        const val ICON_REACT = "Icon_React"
    }

    // =========================
    // Energy Bar ViewModel
    // =========================
    object EnergyBar {

        const val VIEWMODEL_NAME = "Energy_Bar"

        const val BAR_COLOR = "Bar_Color"
        const val LIVES = "Lives"
        const val ENERGY_BAR = "Energy_Bar"
    }
}


data class RewardsParams(
    val price: Float,
    val itemType: String, // COIN / GEM
    val buttonText: String,
    val lives: Float,
    val energy: Float,
    val coinStart: Float,
    val gemStart: Float,
//    val itemCount: Float?,
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
        // Strings
        // =========================
        strings = mapOf(
            // Button VM
            "${RiveProps.Button.VIEWMODEL_NAME}/${RiveProps.Button.TEXT}" to params.buttonText
        ),

        // =========================
        // Numbers
        // =========================
        numbers = mapOf(

//            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.COIN_ITEM_COUNT}" to params.itemCount,

            // Rewards VM
            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.PRICE_VALUE}" to params.price,

            // Energy VM
            "${RiveProps.EnergyBar.VIEWMODEL_NAME}/${RiveProps.EnergyBar.LIVES}" to params.lives,
            "${RiveProps.EnergyBar.VIEWMODEL_NAME}/${RiveProps.EnergyBar.ENERGY_BAR}" to params.energy,

            // Coin VM
            "${RiveProps.Coin.VIEWMODEL_NAME}/${RiveProps.Coin.COIN_START_VALUE}" to params.coinStart,

            // Gem VM
            "${RiveProps.Gem.VIEWMODEL_NAME}/${RiveProps.Gem.GEM_START_VALUE}" to params.gemStart,



        ),

        // =========================
        // Enums / Strings
        // =========================
        enums = mapOf(

            "${RiveProps.Kicker.VIEWMODEL_NAME}/${RiveProps.Kicker.ItemSelectionValues.VIEWMODEL_NAME}" to params.itemType

        ),

        triggers = listOf(

            // Coin VM
            "${RiveProps.Coin.VIEWMODEL_NAME}/${RiveProps.Coin.ICON_REACT}",

            // Gem VM
            "${RiveProps.Gem.VIEWMODEL_NAME}/${RiveProps.Gem.ICON_REACT}",

            // Item VM
            "${RiveProps.Button.VIEWMODEL_NAME}/${RiveProps.Button.PRESSED}",

        )

    )

}