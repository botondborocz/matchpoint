package org.ttproject.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.*

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun NativeImageActionMenu(
    isMine: Boolean,
    modifier: Modifier,
    onDelete: () -> Unit,
    onReport: () -> Unit
) {
    UIKitView<UIButton>(
        factory = {
            val button = UIButton.buttonWithType(UIButtonTypeCustom)

            button.apply {
                // 👇 FIX 1: OPAQUE background.
                // Translucency allows the hole-punch to reveal the Map underneath.
                // This dark gray closely matches the translucent black of the other Compose buttons.
                backgroundColor = UIColor(white = 0.15, alpha = 1.0)
                layer.cornerRadius = 20.0
                clipsToBounds = true

                val imageConfig = UIImageSymbolConfiguration.configurationWithPointSize(
                    pointSize = 18.0,
                    weight = UIImageSymbolWeightRegular
                )
                val icon = UIImage.systemImageNamed("ellipsis", withConfiguration = imageConfig)
                setImage(icon, forState = UIControlStateNormal)
                tintColor = UIColor.whiteColor

                showsMenuAsPrimaryAction = true
            }

            button
        },
        update = { button ->
            val actions = mutableListOf<UIMenuElement>()

            if (isMine) {
                val deleteAction = UIAction.actionWithTitle(
                    title = "Delete Photo",
                    image = UIImage.systemImageNamed("trash"),
                    identifier = null,
                    handler = { _ -> onDelete() }
                )
                // This makes the text and icon natively RED in iOS!
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

            button.menu = UIMenu.menuWithTitle(title = "", children = actions)
        },
        // 👇 FIX 2: Apply clip(CircleShape).
        // This forces Compose to cut a perfectly circular hole instead of a square one!
        modifier = modifier.size(40.dp).clip(CircleShape)
    )
}