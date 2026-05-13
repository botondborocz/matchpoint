package org.ttproject.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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