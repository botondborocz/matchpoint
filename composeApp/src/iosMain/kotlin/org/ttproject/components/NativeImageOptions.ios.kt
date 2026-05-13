package org.ttproject.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            // 👇 FIX: Use the Kotlin/Native factory method instead of a constructor!
            val button = UIButton.buttonWithType(UIButtonTypeCustom)

            button.apply {
                backgroundColor = UIColor(white = 0.0, alpha = 0.5)
                layer.cornerRadius = 20.0

                val imageConfig = UIImageSymbolConfiguration.configurationWithPointSize(
                    pointSize = 18.0,
                    weight = UIImageSymbolWeightRegular
                )
                val icon = UIImage.systemImageNamed("ellipsis", withConfiguration = imageConfig)
                setImage(icon, forState = UIControlStateNormal)
                tintColor = UIColor.whiteColor

                showsMenuAsPrimaryAction = true
            }

            // Return the button from the factory
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
        modifier = modifier.size(40.dp)
    )
}