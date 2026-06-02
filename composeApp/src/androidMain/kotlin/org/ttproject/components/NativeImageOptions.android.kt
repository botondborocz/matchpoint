package org.ttproject.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.ttproject.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun NativeImageActionMenu(
    isMine: Boolean,
    isTransitioning: Boolean,
    modifier: Modifier,
    onDelete: () -> Unit,
    onReport: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val reasons = listOf(
        "Spam or misleading",
        "Inappropriate or sexually explicit",
        "Hate speech or bullying",
        "Violence or harmful behavior",
        "Other"
    )

    Box(modifier = modifier) {
        Surface(color = Color(0xFF333333), shape = CircleShape, modifier = Modifier.size(40.dp)) {
            IconButton(onClick = { expanded = true }, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AppColors.SurfaceDark)
        ) {
            if (isMine) {
                DropdownMenuItem(
                    text = { Text("Delete Photo", color = Color(0xFFE57373)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE57373)) },
                    onClick = { expanded = false; onDelete() }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Report Photo", color = AppColors.TextPrimary) },
                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = AppColors.TextGray) },
                    onClick = {
                        expanded = false
                        showReportSheet = true
                    }
                )
            }
        }
    }

    if (showReportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showReportSheet = false },
            sheetState = sheetState,
            containerColor = AppColors.Background
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text(
                    text = "Why are you reporting this photo?",
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )

                HorizontalDivider(color = AppColors.TextGray.copy(alpha = 0.1f))

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(reasons) { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        showReportSheet = false
                                        onReport(reason) // Trigger the callback!
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = reason, color = AppColors.TextPrimary, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
actual fun GalleryTopBar(
    modifier: Modifier, currentIndex: Int, totalImages: Int, isMine: Boolean,
    isTransitioning: Boolean, onClose: () -> Unit, onDelete: () -> Unit, onReport: (String) -> Unit
) {
    val uiAlpha by animateFloatAsState(targetValue = if (isTransitioning) 0f else 1f, animationSpec = tween(200))
    var expanded by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = uiAlpha }
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(color = Color(0xFF333333), shape = CircleShape) {
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Surface(color = Color(0xFF333333), shape = CircleShape) {
            Text(text = "$currentIndex of $totalImages", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Box(modifier = Modifier) {
            Surface(color = Color(0xFF333333), shape = CircleShape, modifier = Modifier.size(40.dp)) {
                IconButton(onClick = { expanded = true }, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(AppColors.SurfaceDark)
            ) {
                if (isMine) {
                    DropdownMenuItem(
                        text = { Text("Delete Photo", color = Color(0xFFE57373)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE57373)) },
                        onClick = { expanded = false; onDelete() }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Report Photo", color = AppColors.TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = AppColors.TextGray) },
                        onClick = {
                            expanded = false
                            showReportSheet = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
actual fun GalleryBottomBar(modifier: Modifier, authorName: String, isTransitioning: Boolean) {
    val uiAlpha by animateFloatAsState(targetValue = if (isTransitioning) 0f else 1f, animationSpec = tween(200))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = uiAlpha }
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
            .padding(bottom = 24.dp, top = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(color = Color(0xFF333333), shape = RoundedCornerShape(20.dp)) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📸", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Uploaded by $authorName", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        }
    }
}