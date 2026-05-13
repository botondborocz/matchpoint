package org.ttproject.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.LocalUIViewController
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.*

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun NativeImageActionMenu(
    isMine: Boolean,
    modifier: Modifier,
    onDelete: () -> Unit,
    onReport: () -> Unit
) {
    // 1. Capture the exact screen coordinates of the Compose button
    var buttonBounds by remember { mutableStateOf<Rect?>(null) }

    // Get the native iOS View Controller hosting our Compose UI
    val viewController = LocalUIViewController.current

    // 2. Draw the beautiful, translucent Compose button (No Hole Punching!)
    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            buttonBounds = coordinates.boundsInWindow()
        }
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = CircleShape,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "More Options",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // 3. Inject a completely transparent native iOS button over it to handle the tap!
    if (buttonBounds != null) {
        DisposableEffect(buttonBounds, isMine) {
            val button = UIButton.buttonWithType(UIButtonTypeCustom).apply {
                // Completely invisible so the Compose button shows through!
                backgroundColor = UIColor.clearColor
                showsMenuAsPrimaryAction = true

                val actions = mutableListOf<UIMenuElement>()

                if (isMine) {
                    val deleteAction = UIAction.actionWithTitle(
                        title = "Delete Photo",
                        image = UIImage.systemImageNamed("trash"),
                        identifier = null,
                        handler = { _ -> onDelete() }
                    )
                    // Makes the text and icon natively RED
                    deleteAction.attributes = UIMenuElementAttributesDestructive
                    actions.add(deleteAction)
                } else {
                    val reportAction = UIAction.actionWithTitle(
                        title = "Report Photo",
                        image = UIImage.systemImageNamed("flag"),
                        identifier = null,
                        handler = { _ -> onReport() }
                    )
                    actions.add(reportAction)
                }

                menu = UIMenu.menuWithTitle(title = "", children = actions)
            }

            // Apply the layout bounds from Compose directly to the Native iOS button
            val bounds = buttonBounds!!
            button.setFrame(
                CGRectMake(
                    x = bounds.left.toDouble(),
                    y = bounds.top.toDouble(),
                    width = bounds.width.toDouble(),
                    height = bounds.height.toDouble()
                )
            )

            // Add the invisible button to the highest native view level
            viewController.view.addSubview(button)

            // Cleanup when the Composable leaves the screen (e.g., gallery is closed)
            onDispose {
                button.removeFromSuperview()
            }
        }
    }
}