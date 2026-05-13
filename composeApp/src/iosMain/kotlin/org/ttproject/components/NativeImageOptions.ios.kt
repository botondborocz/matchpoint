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
    onReport: (String) -> Unit
) {
    val reasons = listOf(
        "Spam or misleading",
        "Inappropriate or sexually explicit",
        "Hate speech or bullying",
        "Violence or harmful behavior",
        "Other"
    )

    // 👇 Change 1: We use a generic UIView as the root to act as a solid background shield
    UIKitView<UIView>(
        factory = {
            // The Solid Black Shield Container
            val container = UIView().apply {
                backgroundColor = UIColor.blackColor
                layer.cornerRadius = 20.0
                clipsToBounds = true
            }

            // The Native Menu Button
            val button = UIButton.buttonWithType(UIButtonTypeCustom).apply {
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
                translatesAutoresizingMaskIntoConstraints = false // Needed for constraints below
            }

            // Put the button inside the shield container
            container.addSubview(button)

            // Pin the button to fill the container perfectly
            button.leadingAnchor.constraintEqualToAnchor(container.leadingAnchor).active = true
            button.trailingAnchor.constraintEqualToAnchor(container.trailingAnchor).active = true
            button.topAnchor.constraintEqualToAnchor(container.topAnchor).active = true
            button.bottomAnchor.constraintEqualToAnchor(container.bottomAnchor).active = true

            container
        },
        update = { container ->
            // 👇 Change 2: Extract the button from the container to update its menu
            val button = container.subviews.firstOrNull() as? UIButton ?: return@UIKitView

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
                    handler = { _ ->
                        val alert = UIAlertController.alertControllerWithTitle(
                            title = "Report Photo",
                            message = "Why are you reporting this photo?",
                            preferredStyle = UIAlertControllerStyleActionSheet
                        )

                        reasons.forEach { reason ->
                            alert.addAction(
                                UIAlertAction.actionWithTitle(reason, UIAlertActionStyleDefault) {
                                    onReport(reason)
                                }
                            )
                        }

                        alert.addAction(
                            UIAlertAction.actionWithTitle("Cancel", UIAlertActionStyleCancel, null)
                        )

                        val window = UIApplication.sharedApplication.windows.firstOrNull {
                            (it as UIWindow).isKeyWindow()
                        } as? UIWindow

                        window?.rootViewController?.presentViewController(alert, animated = true, completion = null)
                    }
                )
                actions.add(reportAction)
            }

            button.menu = UIMenu.menuWithTitle(title = "", children = actions)
        },
        modifier = modifier.size(40.dp).clip(CircleShape)
    )
}