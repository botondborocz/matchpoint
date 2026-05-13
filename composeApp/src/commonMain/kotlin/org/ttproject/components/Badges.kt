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
import org.ttproject.data.BadgeData
import org.ttproject.data.BadgeTierDefinition
import org.ttproject.data.UserBadgeMetricsDto
import kotlin.math.sqrt


fun mapMetricsToBadges(metrics: UserBadgeMetricsDto?): List<BadgeData> {
    val safeMetrics = metrics ?: UserBadgeMetricsDto()

    return listOf(
        BadgeData(
            "Asztalfelderítő", "Új pingpong asztalok és edzőtermek sikeres hozzáadása a közösségi térképhez.",
            Icons.Default.Place, safeMetrics.addedTables, listOf(
                BadgeTierDefinition(1, "asztal"), BadgeTierDefinition(5, "asztal"), BadgeTierDefinition(15, "asztal"), BadgeTierDefinition(50, "asztal")
            )),
        BadgeData(
            "Helyszínelő", "Saját fotók feltöltése a térképen szereplő asztalokhoz, hogy mások is lássák a minőséget.",
            Icons.Default.CameraAlt, safeMetrics.uploadedPhotos, listOf(
                BadgeTierDefinition(1, "fotó"), BadgeTierDefinition(10, "fotó"), BadgeTierDefinition(50, "fotó"), BadgeTierDefinition(150, "fotó")
            )),
        BadgeData(
            "Helyi Szakértő", "Szöveges értékelések és vélemények írása az alkalmazásban található helyszínekről.",
            Icons.Default.RateReview, safeMetrics.writtenReviews, listOf(
                BadgeTierDefinition(1, "értékelés"), BadgeTierDefinition(5, "értékelés"), BadgeTierDefinition(20, "értékelés"), BadgeTierDefinition(50, "értékelés")
            )),
        BadgeData(
            "Jégtörő", "Sikeres 'Match' (párba állás) létrejötte más játékosokkal a környékeden.",
            Icons.Default.Bolt, safeMetrics.successfulMatches, listOf(
                BadgeTierDefinition(1, "Match"), BadgeTierDefinition(10, "Match"), BadgeTierDefinition(50, "Match"), BadgeTierDefinition(200, "Match")
            )),
        BadgeData(
            "Hálózatépítő", "Elküldött chat üzenetek száma. Építsd a közösséget és szervezz meccseket!",
            Icons.Default.Message, safeMetrics.sentMessages, listOf(
                BadgeTierDefinition(10, "üzenet"), BadgeTierDefinition(100, "üzenet"), BadgeTierDefinition(500, "üzenet"), BadgeTierDefinition(2000, "üzenet")
            )),
        BadgeData(
            "Radar", "Játékoskártyák értékelése. Minden jobbra vagy balra húzás számít!",
            Icons.Default.Radar, safeMetrics.profileSwipes, listOf(
                BadgeTierDefinition(50, "húzás"), BadgeTierDefinition(500, "húzás"), BadgeTierDefinition(2000, "húzás"), BadgeTierDefinition(10000, "húzás")
            )),
        BadgeData(
            "Okos Vágó", "Az AI által sikeresen megvágott és kielemezett edzésvideóid száma.",
            Icons.Default.ContentCut, safeMetrics.trimmedVideos, listOf(
                BadgeTierDefinition(1, "videó"), BadgeTierDefinition(10, "videó"), BadgeTierDefinition(50, "videó"), BadgeTierDefinition(200, "videó")
            )),
        BadgeData(
            "Kíváncsi Elme", "Az AI edzőnek feltett szakmai kérdések és indított beszélgetések száma.",
            Icons.Default.Psychology, safeMetrics.aiQuestions, listOf(
                BadgeTierDefinition(3, "kérdés"), BadgeTierDefinition(20, "kérdés"), BadgeTierDefinition(100, "kérdés"), BadgeTierDefinition(500, "kérdés")
            )),
        BadgeData(
            "Vasakarat", "Egymást követő napok száma (Streak), amikor megnyitottad és használtad az alkalmazást.",
            Icons.Default.LocalFireDepartment, safeMetrics.currentStreak, listOf(
                BadgeTierDefinition(3, "nap"), BadgeTierDefinition(7, "nap"), BadgeTierDefinition(30, "nap"), BadgeTierDefinition(100, "nap")
            )),
        BadgeData(
            "Nagykövet", "Saját meghívó kódoddal regisztrált új játékosok száma. Hívd meg a barátaidat!",
            Icons.Default.Campaign, safeMetrics.invitedFriends, listOf(
                BadgeTierDefinition(1, "barát"), BadgeTierDefinition(5, "barát"), BadgeTierDefinition(15, "barát"), BadgeTierDefinition(50, "barát")
            ))
    )
}


// Prefixek a popup soraihoz
fun getTierPrefix(levelIndex: Int): String {
    return when (levelIndex) {
        0 -> "Kezdő"
        1 -> "Haladó"
        2 -> "Profi"
        3 -> "Legenda"
        else -> ""
    }
}

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
                contentDescription = badge.baseName,
                tint = badgeColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = badge.baseName,
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
fun BadgesSection(
    metrics: UserBadgeMetricsDto?, // 👈 Pass the real metrics here
    onBadgeClick: (BadgeData) -> Unit
) {
    // If metrics are null (still loading or failed), fallback to 0s to avoid crashes
//    val safeMetrics = metrics ?: UserBadgeMetricsDto()

    // Map the real backend data to the currentValue!
    val myBadges = mapMetricsToBadges(metrics)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = AppColors.TextGray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("KITŰZŐK", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        val chunkedBadges = myBadges.chunked(5)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            chunkedBadges.forEach { rowBadges ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
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

// --- 5. A Frissített Popup Dialog (Badge Details) Animált Progress Bar-ral ---
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
            val isBadgeStarted = badge.currentLevel > 0
            val headerColor = getBadgeColor(badge.currentLevel, isBadgeStarted)

            // Fejléc Ikon
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                HexagonCanvas(color = headerColor, strokeWidth = 3f)
                Icon(badge.icon, contentDescription = null, tint = headerColor, modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dinamikus Teljes Név
            Text(
                text = badge.fullName,
                color = AppColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            // 👇 ÚJ LOGIKA: Progress Bar és Százalék számítás
            val nextTier = badge.thresholds.firstOrNull { badge.currentValue < it.requirement }

            // Cél százalék (0.0f és 1.0f között)
            val progressTarget = if (nextTier != null) {
                (badge.currentValue.toFloat() / nextTier.requirement.toFloat()).coerceIn(0f, 1f)
            } else {
                1f // Ha már max szinten van, akkor 100%
            }

            // Szép animáció, ahogy betölt a sáv
            val animatedProgress by animateFloatAsState(
                targetValue = progressTarget,
                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                label = "progressAnimation"
            )

            val percentString = (progressTarget * 100).toInt()

            val progressText = if (nextTier != null) {
                "Jelenlegi állás: ${badge.currentValue} / ${nextTier.requirement} ($percentString%)"
            } else {
                "Jelenlegi állás: ${badge.currentValue} (Max szint elérve!)"
            }

            // Szöveges állás
            Text(
                text = progressText,
                color = AppColors.AccentOrange,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 👇 ÚJ: A Vizuális Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.4f)), // Háttérsáv
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress) // Az animált értékkel tölti ki
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(AppColors.AccentOrange) // Kitöltött rész
                )
            }

            // Hosszabb, magyarázó leírás a kitűzőről
            Text(
                text = badge.description,
                color = AppColors.TextGray,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // A 4 Szint sorai
            badge.thresholds.forEachIndexed { index, tierDef ->
                val rowLevel = index + 1
                val isRowCompleted = badge.currentValue >= tierDef.requirement
                val rowColor = getBadgeColor(rowLevel, isRowCompleted)
                val tierName = getTierPrefix(index)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Kis hatszög
                    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        HexagonCanvas(color = rowColor, strokeWidth = 2f)
                        Icon(badge.icon, contentDescription = null, tint = rowColor, modifier = Modifier.size(12.dp))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Szöveg
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tierName.uppercase(),
                            color = rowColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${tierDef.requirement} ${tierDef.suffixText}",
                            color = if (isRowCompleted) AppColors.TextPrimary else AppColors.TextGray,
                            fontSize = 13.sp
                        )
                    }

                    // Pipa, ha kész van
                    if (isRowCompleted) {
                        Icon(Icons.Default.Check, contentDescription = "Kész", tint = AppColors.AccentOrange, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentOrange),
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Király!", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MiniBadge(badge: BadgeData) {
    // Only display completed badges
    if (badge.currentLevel > 0) {
        val badgeColor = getBadgeColor(badge.currentLevel, true) // Reusing your existing color function

        Box(
            modifier = Modifier.size(26.dp), // Tiny, elegant size
            contentAlignment = Alignment.Center
        ) {
            HexagonCanvas(color = badgeColor, strokeWidth = 1.5f)

            Icon(
                imageVector = badge.icon,
                contentDescription = badge.baseName,
                tint = badgeColor,
                modifier = Modifier.size(12.dp) // Scaled down icon
            )
        }
    }
}