package com.familyboard.app.ui.bucket

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsGolf
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TempleBuddhist
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 버킷 항목 꾸미기용 MDI(Material Design Icons) 세트.
 * 키(String)만 Firestore에 저장하고, 화면에서 ImageVector 로 매핑한다.
 */
object BucketIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "flight" to Icons.Filled.Flight,
        "beach" to Icons.Filled.BeachAccess,
        "sailing" to Icons.Filled.Sailing,
        "boat" to Icons.Filled.DirectionsBoat,
        "anchor" to Icons.Filled.Anchor,
        "train" to Icons.Filled.Train,
        "car" to Icons.Filled.DirectionsCar,
        "motorcycle" to Icons.Filled.TwoWheeler,
        "bike" to Icons.Filled.DirectionsBike,
        "map" to Icons.Filled.Map,
        "globe" to Icons.Filled.Public,
        "hiking" to Icons.Filled.Hiking,
        "mountain" to Icons.Filled.Terrain,
        "landscape" to Icons.Filled.Landscape,
        "park" to Icons.Filled.Park,
        "forest" to Icons.Filled.Forest,
        "flower" to Icons.Filled.LocalFlorist,
        "run" to Icons.Filled.DirectionsRun,
        "fitness" to Icons.Filled.FitnessCenter,
        "soccer" to Icons.Filled.SportsSoccer,
        "golf" to Icons.Filled.SportsGolf,
        "pool" to Icons.Filled.Pool,
        "game" to Icons.Filled.SportsEsports,
        "restaurant" to Icons.Filled.Restaurant,
        "cafe" to Icons.Filled.LocalCafe,
        "cake" to Icons.Filled.Cake,
        "nightlife" to Icons.Filled.Nightlife,
        "music" to Icons.Filled.MusicNote,
        "theater" to Icons.Filled.TheaterComedy,
        "palette" to Icons.Filled.Palette,
        "camera" to Icons.Filled.CameraAlt,
        "book" to Icons.Filled.MenuBook,
        "school" to Icons.Filled.School,
        "work" to Icons.Filled.Work,
        "spa" to Icons.Filled.Spa,
        "meditation" to Icons.Filled.SelfImprovement,
        "church" to Icons.Filled.Church,
        "temple" to Icons.Filled.TempleBuddhist,
        "home" to Icons.Filled.Home,
        "pets" to Icons.Filled.Pets,
        "gift" to Icons.Filled.CardGiftcard,
        "celebration" to Icons.Filled.Celebration,
        "trophy" to Icons.Filled.EmojiEvents,
        "diamond" to Icons.Filled.Diamond,
        "savings" to Icons.Filled.Savings,
        "sun" to Icons.Filled.WbSunny,
        "heart" to Icons.Filled.Favorite,
        "star" to Icons.Filled.Star,
    )

    private val byKey: Map<String, ImageVector> = all.toMap()

    fun of(key: String?): ImageVector? = if (key.isNullOrBlank()) null else byKey[key]
}
