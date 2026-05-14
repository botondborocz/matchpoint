package org.ttproject.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.*

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun NativeImageActionMenu(
    isMine: Boolean,
    isTransitioning: Boolean,
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

    Box(modifier = modifier.size(40.dp)) {

        // 👇 1. ALWAYS keep the UIKitView in the tree so it doesn't have to be recreated!
        UIKitView<UIView>(
            factory = {
                val container = UIView().apply {
                    backgroundColor = UIColor.blackColor
                }

                val button = UIButton.buttonWithType(UIButtonTypeCustom).apply {
                    backgroundColor = UIColor(white = 0.15, alpha = 1.0)

                    val imageConfig = UIImageSymbolConfiguration.configurationWithPointSize(
                        pointSize = 18.0,
                        weight = UIImageSymbolWeightRegular
                    )
                    val icon = UIImage.systemImageNamed("ellipsis", withConfiguration = imageConfig)
                    setImage(icon, forState = UIControlStateNormal)
                    tintColor = UIColor.whiteColor

                    showsMenuAsPrimaryAction = true
                    translatesAutoresizingMaskIntoConstraints = false
                }

                container.addSubview(button)
                button.leadingAnchor.constraintEqualToAnchor(container.leadingAnchor).active = true
                button.trailingAnchor.constraintEqualToAnchor(container.trailingAnchor).active = true
                button.topAnchor.constraintEqualToAnchor(container.topAnchor).active = true
                button.bottomAnchor.constraintEqualToAnchor(container.bottomAnchor).active = true

                container
            },
            update = { container ->
                // 👇 2. Instantly hide/show the native view without destroying it
                container.hidden = isTransitioning

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
                            alert.addAction(UIAlertAction.actionWithTitle("Cancel", UIAlertActionStyleCancel, null))

                            val window = UIApplication.sharedApplication.windows.firstOrNull { (it as UIWindow).isKeyWindow() } as? UIWindow
                            window?.rootViewController?.presentViewController(alert, animated = true, completion = null)
                        }
                    )
                    actions.add(reportAction)
                }

                button.menu = UIMenu.menuWithTitle(title = "", children = actions)
            },
            modifier = Modifier.fillMaxSize().clip(CircleShape)
        )

        // 👇 3. Show the dummy pure-compose button ONLY when transitioning
        if (isTransitioning) {
            Surface(
                color = Color(0xFF262626),
                shape = CircleShape,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(3.2.dp).background(Color.White, CircleShape))
                        Box(modifier = Modifier.size(3.2.dp).background(Color.White, CircleShape))
                        Box(modifier = Modifier.size(3.2.dp).background(Color.White, CircleShape))
                    }
                }
            }
        }
    }
}