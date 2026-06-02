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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
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

// 👇 1. Shared Compose state to hide UI when ANY native menu is open
internal var isNativeMenuOpen by mutableStateOf(false)

// 👇 2. Custom Native Button that intercepts the UIMenu presentation lifecycle!
@OptIn(ExperimentalForeignApi::class)
private class MenuObservableButton : UIButton(frame = platform.CoreGraphics.CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    var onMenuStateChanged: ((Boolean) -> Unit)? = null

    override fun setHighlighted(highlighted: Boolean) {
        super.setHighlighted(highlighted)
        // iOS keeps the button 'highlighted' exactly for the duration the UIMenu is open
        onMenuStateChanged?.invoke(highlighted)
    }
}

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

                // 👇 Use our custom lifecycle-aware button
                val button = MenuObservableButton().apply {
                    backgroundColor = UIColor(white = 0.15, alpha = 1.0)
                    val imageConfig = UIImageSymbolConfiguration.configurationWithPointSize(
                        pointSize = 18.0,
                        weight = UIImageSymbolWeightRegular
                    )
                    setImage(UIImage.systemImageNamed("ellipsis", withConfiguration = imageConfig), forState = UIControlStateNormal)
                    tintColor = UIColor.whiteColor
                    showsMenuAsPrimaryAction = true
                    translatesAutoresizingMaskIntoConstraints = false

                    onMenuStateChanged = { isOpen ->
                        isNativeMenuOpen = isOpen
                    }
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

                val button = container.subviews.firstOrNull() as? MenuObservableButton ?: return@UIKitView
                val actions = mutableListOf<UIMenuElement>()

                if (isMine) {
                    val deleteAction = UIAction.actionWithTitle(
                        title = "Delete Photo", image = UIImage.systemImageNamed("trash"), identifier = null,
                        handler = { _ -> onDelete() }
                    )
                    deleteAction.attributes = UIMenuElementAttributesDestructive
                    actions.add(deleteAction)
                } else {
                    val reportAction = UIAction.actionWithTitle(
                        title = "Report Photo", image = UIImage.systemImageNamed("flag"), identifier = null,
                        handler = { _ ->
                            val alert = UIAlertController.alertControllerWithTitle(
                                title = "Report Photo", message = "Why are you reporting this photo?", preferredStyle = UIAlertControllerStyleActionSheet
                            )
                            reasons.forEach { reason ->
                                alert.addAction(UIAlertAction.actionWithTitle(reason, UIAlertActionStyleDefault) { onReport(reason) })
                            }
                            alert.addAction(UIAlertAction.actionWithTitle("Cancel", UIAlertActionStyleCancel, null))

                            val window = UIApplication.sharedApplication.windows.firstOrNull { (it as UIWindow).isKeyWindow() } as? UIWindow
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
            Surface(color = Color(0xFF262626), shape = CircleShape, modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.2.dp), verticalAlignment = Alignment.CenterVertically) {
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

    // 👇 Changed to a Box. Box allows absolute positioning so the 3 dots
    // stay exactly on the right even when the left items are removed!
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .height(72.dp)
    ) {
        // 👇 Only render these if the menu is CLOSED.
        // When true, Compose completely removes their transparent holes!
        if (!isNativeMenuOpen) {
            // 1. Close Button
            UIKitView<UIButton>(
                factory = {
                    UIButton.buttonWithType(UIButtonTypeCustom).apply {
                        backgroundColor = UIColor(white = 0.2, alpha = 1.0)
                        layer.cornerRadius = 20.0
                        setImage(UIImage.systemImageNamed("xmark", UIImageSymbolConfiguration.configurationWithPointSize(16.0, UIImageSymbolWeightBold)), forState = UIControlStateNormal)
                        tintColor = UIColor.whiteColor
                        addAction(UIAction.actionWithHandler { _ -> currentOnClose() }, forControlEvents = UIControlEventTouchUpInside)
                    }
                },
                update = { btn -> btn.hidden = isTransitioning },
                modifier = Modifier.align(Alignment.CenterStart).size(40.dp)
            )

            // 2. Counter Pill
            UIKitView<UIView>(
                factory = {
                    val pill = UIView().apply {
                        backgroundColor = UIColor(white = 0.2, alpha = 1.0)
                        layer.cornerRadius = 16.0
                    }
                    val label = UILabel().apply {
                        textColor = UIColor.whiteColor
                        font = UIFont.boldSystemFontOfSize(15.0)
                        textAlignment = NSTextAlignmentCenter
                        tag = 99L
                        translatesAutoresizingMaskIntoConstraints = false
                    }
                    pill.addSubview(label)
                    label.centerXAnchor.constraintEqualToAnchor(pill.centerXAnchor).active = true
                    label.centerYAnchor.constraintEqualToAnchor(pill.centerYAnchor).active = true
                    label.leadingAnchor.constraintEqualToAnchor(pill.leadingAnchor, constant = 16.0).active = true
                    label.trailingAnchor.constraintEqualToAnchor(pill.trailingAnchor, constant = -16.0).active = true
                    pill
                },
                update = { pill ->
                    pill.hidden = isTransitioning
                    val label = pill.viewWithTag(99L) as? UILabel
                    label?.text = "$currentIndex of $totalImages"
                },
                modifier = Modifier.align(Alignment.Center).width(90.dp).height(32.dp)
            )
        }

        // 3. More Menu
        UIKitView<MenuObservableButton>(
            factory = {
                // 👇 Use our custom lifecycle-aware button
                MenuObservableButton().apply {
                    backgroundColor = UIColor(white = 0.2, alpha = 1.0)
                    layer.cornerRadius = 20.0
                    setImage(UIImage.systemImageNamed("ellipsis", UIImageSymbolConfiguration.configurationWithPointSize(18.0, UIImageSymbolWeightRegular)), forState = UIControlStateNormal)
                    tintColor = UIColor.whiteColor
                    showsMenuAsPrimaryAction = true

                    onMenuStateChanged = { isOpen ->
                        isNativeMenuOpen = isOpen
                    }
                }
            },
            update = { moreBtn ->
                moreBtn.hidden = isTransitioning
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
                        alert.popoverPresentationController?.sourceRect = moreBtn.bounds
                        window?.rootViewController?.presentViewController(alert, true, null)
                    }
                    actions.add(reportAction)
                }
                moreBtn.menu = UIMenu.menuWithTitle("", actions)
            },
            modifier = Modifier.align(Alignment.CenterEnd).size(40.dp)
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun GalleryBottomBar(modifier: Modifier, authorName: String, isTransitioning: Boolean) {
    // 👇 Instantly removes the bottom bar (and its hole!) when the menu opens
    if (isNativeMenuOpen) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(100.dp),
        contentAlignment = Alignment.Center
    ) {
        UIKitView<UIView>(
            factory = {
                val pill = UIView().apply {
                    backgroundColor = UIColor(white = 0.2, alpha = 1.0)
                    layer.cornerRadius = 20.0
                }
                val label = UILabel().apply {
                    textColor = UIColor.whiteColor
                    font = UIFont.systemFontOfSize(14.0, UIFontWeightMedium)
                    tag = 99L
                    translatesAutoresizingMaskIntoConstraints = false
                }
                pill.addSubview(label)
                label.centerXAnchor.constraintEqualToAnchor(pill.centerXAnchor).active = true
                label.centerYAnchor.constraintEqualToAnchor(pill.centerYAnchor).active = true
                label.leadingAnchor.constraintEqualToAnchor(pill.leadingAnchor, constant = 16.0).active = true
                label.trailingAnchor.constraintEqualToAnchor(pill.trailingAnchor, constant = -16.0).active = true
                pill
            },
            update = { pill ->
                pill.hidden = isTransitioning
                val label = pill.viewWithTag(99L) as? UILabel
                label?.text = "📸 Uploaded by $authorName"
            },
            modifier = Modifier.width(220.dp).height(40.dp)
        )
    }
}