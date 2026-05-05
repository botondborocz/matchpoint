import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.ttproject.AppColors
import kotlin.math.sqrt

// --- 1. Adatmodellek (Data Models) ---
data class BadgeTier(
    val title: String,
    val description: String
)

data class BadgeData(
    val name: String,
    val icon: ImageVector,
    val currentLevel: Int, // 0 = Not completed, 1 = White, 2 = Cyber, 3 = Neon, 4 = Plasma
    val tiers: List<BadgeTier>
)

// Helpler function to get the correct color based on level status
fun getBadgeColor(level: Int, isCompleted: Boolean): Color {
    if (!isCompleted) return Color(0xFF444444) // Gray for not completed
    return when (level) {
        1 -> Color.White           // Level 1: White
        2 -> Color(0xFF00E5FF)     // Level 2: Cyber Blue
        3 -> Color(0xFFFF6B35)     // Level 3: Neon Orange
        4 -> Color(0xFFD500F9)     // Level 4: Plasma
        else -> Color(0xFF444444)
    }
}

// --- 2. Újrafelhasználható Hatszög (Reusable Hexagon) ---
@Composable
fun HexagonCanvas(color: Color, modifier: Modifier = Modifier, strokeWidth: Float = 2f) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(size.width, size.height) / 2f
        val hexWidth = radius * sqrt(3f)

        val path = Path().apply {
            moveTo(cx, cy - radius)
            lineTo(cx + hexWidth / 2f, cy - radius / 2f)
            lineTo(cx + hexWidth / 2f, cy + radius / 2f)
            lineTo(cx, cy + radius)
            lineTo(cx - hexWidth / 2f, cy + radius / 2f)
            lineTo(cx - hexWidth / 2f, cy - radius / 2f)
            close()
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth.dp.toPx())
        )
    }
}

// --- 3. A Kitűző Komponens (Badge Item) ---
@Composable
fun BadgeItem(badge: BadgeData, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val isCompleted = badge.currentLevel > 0
    val badgeColor = getBadgeColor(badge.currentLevel, isCompleted)

    val infiniteTransition = rememberInfiniteTransition(label = "plasmaPulse")
    val scaleMultiplier by if (badge.currentLevel == 4) {
        infiniteTransition.animateFloat(
            initialValue = 1f, targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse"
        )
    } else {
        androidx.compose.runtime.mutableStateOf(1f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .scale(scaleMultiplier),
            contentAlignment = Alignment.Center
        ) {
            HexagonCanvas(color = badgeColor)

            Icon(
                imageVector = badge.icon,
                contentDescription = badge.name,
                tint = badgeColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = badge.name,
            color = if (isCompleted) AppColors.TextPrimary else AppColors.TextGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 11.sp,
            minLines = 2,
            maxLines = 2
        )
    }
}

// --- 4. A Kitűző Sáv (Badges Section) ---
@Composable
fun BadgesSection(onBadgeClick: (BadgeData) -> Unit) {
    // Standard Tiers for demonstration
    val tableTiers = listOf(
        BadgeTier("Acél (Steel)", "Add 1 new table to the map."),
        BadgeTier("Cyber Blue", "Add 5 new tables to the map."),
        BadgeTier("Neon Orange", "Add 20 new tables to the map."),
        BadgeTier("Plazma (God Tier)", "Add 50+ new tables to the map.")
    )

    val myBadges = listOf(
        BadgeData("Alapító Tag", Icons.Default.Star, 4, tableTiers),
        BadgeData("Asztalfelderítő", Icons.Default.Place, 2, tableTiers),
        BadgeData("Helyszínelő", Icons.Default.CameraAlt, 0, tableTiers), // 👈 Level 0 = Gray
        BadgeData("Helyi Kritikus", Icons.Default.RateReview, 3, tableTiers),
        BadgeData("Jégtörő", Icons.Default.Bolt, 1, tableTiers),          // 👈 Level 1 = White
        BadgeData("A Pálya Ördöge", Icons.Default.SportsKabaddi, 3, tableTiers),
        BadgeData("Sportdiplomata", Icons.Default.EventAvailable, 2, tableTiers),
        BadgeData("Okos Vágó", Icons.Default.ContentCut, 1, tableTiers),
        BadgeData("Elemzés Függő", Icons.Default.Psychology, 0, tableTiers),
        BadgeData("Sebességkirály", Icons.Default.Speed, 3, tableTiers)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = AppColors.TextGray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("BADGES", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        val chunkedBadges = myBadges.chunked(5)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            chunkedBadges.forEach { rowBadges ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowBadges.forEach { badge ->
                        BadgeItem(
                            badge = badge,
                            modifier = Modifier.weight(1f),
                            onClick = { onBadgeClick(badge) }
                        )
                    }
                }
            }
        }
    }
}

// --- 5. A Popup Dialog (Badge Details) ---
@Composable
fun BadgeDetailsDialog(
    badge: BadgeData,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.SurfaceDark)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Icon
            val headerColor = getBadgeColor(badge.currentLevel, badge.currentLevel > 0)
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                HexagonCanvas(color = headerColor, strokeWidth = 3f)
                Icon(badge.icon, contentDescription = null, tint = headerColor, modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(badge.name, color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(24.dp))

            // The 4 Level Rows
            badge.tiers.forEachIndexed { index, tier ->
                val rowLevel = index + 1
                val isRowCompleted = rowLevel <= badge.currentLevel
                val rowColor = getBadgeColor(rowLevel, isRowCompleted)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small Hexagon Indicator
                    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        HexagonCanvas(color = rowColor, strokeWidth = 2f)
                        Icon(badge.icon, contentDescription = null, tint = rowColor, modifier = Modifier.size(12.dp))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Text Column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tier.title.uppercase(),
                            color = rowColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tier.description,
                            color = if (isRowCompleted) AppColors.TextPrimary else AppColors.TextGray,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Close Button
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange),
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Király!", color = Color.White, fontWeight = FontWeight.Bold) // "Awesome!"
            }
        }
    }
}