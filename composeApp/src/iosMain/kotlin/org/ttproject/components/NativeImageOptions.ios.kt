package org.ttproject.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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

                            // iPad anchor safety
                            alert.popoverPresentationController?.sourceView = button
                            alert.popoverPresentationController?.sourceRect = button.bounds

                            window?.rootViewController?.presentViewController(alert, animated = true, completion = null)
                        }
                    )
                    actions.add(reportAction)
                }

                button.menu = UIMenu.menuWithTitle(title = "", children = actions)
            },
            modifier = Modifier.fillMaxSize().clip(CircleShape)
        )

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

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun GalleryTopBar(
    modifier: Modifier, currentIndex: Int, totalImages: Int, isMine: Boolean,
    isTransitioning: Boolean, onClose: () -> Unit, onDelete: () -> Unit, onReport: (String) -> Unit
) {
    val reasons = listOf("Spam or misleading", "Inappropriate or sexually explicit", "Hate speech or bullying", "Violence or harmful behavior", "Other")

    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnReport by rememberUpdatedState(onReport)
    val currentIsMine by rememberUpdatedState(isMine)

    UIKitView<UIView>(
        factory = {
            val container = UIView().apply { backgroundColor = UIColor.clearColor }

            // 1. Close Button
            val closeBtn = UIButton.buttonWithType(UIButtonTypeCustom).apply {
                backgroundColor = UIColor(white = 0.2, alpha = 1.0)
                layer.cornerRadius = 20.0
                setImage(UIImage.systemImageNamed("xmark", UIImageSymbolConfiguration.configurationWithPointSize(16.0, UIImageSymbolWeightBold)), forState = UIControlStateNormal)
                tintColor = UIColor.whiteColor
                translatesAutoresizingMaskIntoConstraints = false
                addAction(UIAction.actionWithHandler { _ -> currentOnClose() }, forControlEvents = UIControlEventTouchUpInside)
            }

            // 2. Counter Pill
            val counterLabel = UILabel().apply {
                textColor = UIColor.whiteColor
                font = UIFont.boldSystemFontOfSize(15.0)
                textAlignment = NSTextAlignmentCenter
                tag = 99L
                translatesAutoresizingMaskIntoConstraints = false
            }
            val counterPill = UIView().apply {
                backgroundColor = UIColor(white = 0.2, alpha = 1.0)
                layer.cornerRadius = 16.0
                translatesAutoresizingMaskIntoConstraints = false
                addSubview(counterLabel)

                counterLabel.centerXAnchor.constraintEqualToAnchor(centerXAnchor).active = true
                counterLabel.centerYAnchor.constraintEqualToAnchor(centerYAnchor).active = true
                counterLabel.leadingAnchor.constraintEqualToAnchor(leadingAnchor, constant = 16.0).active = true
                counterLabel.trailingAnchor.constraintEqualToAnchor(trailingAnchor, constant = -16.0).active = true
            }

            // 3. More Menu (Native UIMenu)
            val moreBtn = UIButton.buttonWithType(UIButtonTypeCustom).apply {
                backgroundColor = UIColor(white = 0.2, alpha = 1.0)
                layer.cornerRadius = 20.0
                setImage(UIImage.systemImageNamed("ellipsis", UIImageSymbolConfiguration.configurationWithPointSize(18.0, UIImageSymbolWeightRegular)), forState = UIControlStateNormal)
                tintColor = UIColor.whiteColor
                showsMenuAsPrimaryAction = true
                translatesAutoresizingMaskIntoConstraints = false
            }

            // 👇 THE FIX: Instantly hide the sibling buttons the moment the 3 dots are touched
            moreBtn.addAction(UIAction.actionWithHandler { _ ->
                closeBtn.hidden = true
                counterPill.hidden = true
            }, forControlEvents = UIControlEventTouchDown)

            container.addSubview(closeBtn)
            container.addSubview(counterPill)
            container.addSubview(moreBtn)

            // Constraints
            closeBtn.leadingAnchor.constraintEqualToAnchor(container.leadingAnchor, constant = 16.0).active = true
            closeBtn.centerYAnchor.constraintEqualToAnchor(container.centerYAnchor).active = true
            closeBtn.widthAnchor.constraintEqualToConstant(40.0).active = true
            closeBtn.heightAnchor.constraintEqualToConstant(40.0).active = true

            counterPill.centerXAnchor.constraintEqualToAnchor(container.centerXAnchor).active = true
            counterPill.centerYAnchor.constraintEqualToAnchor(container.centerYAnchor).active = true
            counterPill.heightAnchor.constraintEqualToConstant(32.0).active = true

            moreBtn.trailingAnchor.constraintEqualToAnchor(container.trailingAnchor, constant = -16.0).active = true
            moreBtn.centerYAnchor.constraintEqualToAnchor(container.centerYAnchor).active = true
            moreBtn.widthAnchor.constraintEqualToConstant(40.0).active = true
            moreBtn.heightAnchor.constraintEqualToConstant(40.0).active = true

            container
        },
        update = { container ->
            container.hidden = isTransitioning

            // Update Label Text
            val counterLabel = container.viewWithTag(99L) as? UILabel
            counterLabel?.text = "$currentIndex of $totalImages"

            val moreBtn = container.subviews.lastOrNull() as? UIButton

            // Build the Native UIMenu
            val actions = mutableListOf<UIMenuElement>()
            if (currentIsMine) {
                val deleteAction = UIAction.actionWithTitle("Delete Photo", UIImage.systemImageNamed("trash"), null) { _ -> currentOnDelete() }
                deleteAction.attributes = UIMenuElementAttributesDestructive
                actions.add(deleteAction)
            } else {
                val reportAction = UIAction.actionWithTitle("Report Photo", UIImage.systemImageNamed("flag"), null) { _ ->
                    val alert = UIAlertController.alertControllerWithTitle("Report Photo", "Why are you reporting this photo?", UIAlertControllerStyleActionSheet)
                    reasons.forEach { reason ->
                        alert.addAction(UIAlertAction.actionWithTitle(reason, UIAlertActionStyleDefault) { currentOnReport(reason) })
                    }
                    alert.addAction(UIAlertAction.actionWithTitle("Cancel", UIAlertActionStyleCancel, null))
                    val window = UIApplication.sharedApplication.windows.firstOrNull { (it as UIWindow).isKeyWindow() } as? UIWindow

                    alert.popoverPresentationController?.sourceView = moreBtn
                    alert.popoverPresentationController?.sourceRect = moreBtn?.bounds ?: kotlin.cinterop.cValue()
                    window?.rootViewController?.presentViewController(alert, true, null)
                }
                actions.add(reportAction)
            }
            moreBtn?.menu = UIMenu.menuWithTitle("", actions)
        },
        // 👇 STATUS BAR FIX: Pushes the Top Bar below the iOS Dynamic Island/Notch
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(72.dp)
    )
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun GalleryBottomBar(modifier: Modifier, authorName: String, isTransitioning: Boolean) {
    UIKitView<UIView>(
        factory = {
            val container = UIView().apply { backgroundColor = UIColor.clearColor }

            val label = UILabel().apply {
                textColor = UIColor.whiteColor
                font = UIFont.systemFontOfSize(14.0, UIFontWeightMedium)
                tag = 99L
                translatesAutoresizingMaskIntoConstraints = false
            }

            val pill = UIView().apply {
                backgroundColor = UIColor(white = 0.2, alpha = 1.0)
                layer.cornerRadius = 20.0
                translatesAutoresizingMaskIntoConstraints = false
                addSubview(label)

                label.centerXAnchor.constraintEqualToAnchor(centerXAnchor).active = true
                label.centerYAnchor.constraintEqualToAnchor(centerYAnchor).active = true
                label.leadingAnchor.constraintEqualToAnchor(leadingAnchor, constant = 16.0).active = true
                label.trailingAnchor.constraintEqualToAnchor(trailingAnchor, constant = -16.0).active = true
            }

            container.addSubview(pill)
            pill.centerXAnchor.constraintEqualToAnchor(container.centerXAnchor).active = true
            pill.centerYAnchor.constraintEqualToAnchor(container.centerYAnchor).active = true
            pill.heightAnchor.constraintEqualToConstant(40.0).active = true

            container
        },
        update = { container ->
            container.hidden = isTransitioning
            val label = container.viewWithTag(99L) as? UILabel
            label?.text = "📸 Uploaded by $authorName"
        },
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(100.dp)
    )
}