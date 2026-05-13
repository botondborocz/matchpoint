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

    UIKitView<UIButton>(
        factory = {
            val button = UIButton.buttonWithType(UIButtonTypeCustom)

            button.apply {
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
                deleteAction.attributes = UIMenuElementAttributesDestructive
                actions.add(deleteAction)
            } else {
                val reportAction = UIAction.actionWithTitle(
                    title = "Report Photo",
                    image = UIImage.systemImageNamed("flag"),
                    identifier = null,
                    handler = { _ ->
                        // 👇 Create the Native Action Sheet for the reasons!
                        val alert = UIAlertController.alertControllerWithTitle(
                            title = "Report Photo",
                            message = "Why are you reporting this photo?",
                            preferredStyle = UIAlertControllerStyleActionSheet
                        )

                        reasons.forEach { reason ->
                            alert.addAction(
                                UIAlertAction.actionWithTitle(reason, UIAlertActionStyleDefault) {
                                    onReport(reason) // Trigger the callback!
                                }
                            )
                        }

                        alert.addAction(
                            UIAlertAction.actionWithTitle("Cancel", UIAlertActionStyleCancel, null)
                        )

                        // Present the alert over the current view hierarchy
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