package org.ttproject.icon

import platform.UIKit.UIApplication
import platform.UIKit.alternateIconName
import platform.UIKit.setAlternateIconName
import platform.UIKit.supportsAlternateIcons
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

class IosAppIconManager : AppIconManager {

    override fun changeIcon(icon: PremiumAppIcon) {
        // 👇 CRITICAL: Force execution onto the iOS Main UI Thread
        dispatch_async(dispatch_get_main_queue()) {
            val application = UIApplication.sharedApplication

            if (application.supportsAlternateIcons) {
                // Determine the alias (null resets it to the primary icon)
                val aliasName = if (icon == PremiumAppIcon.DEFAULT) null else icon.alias

                // If iOS thinks it is already set to this icon, it ignores the command.
                if (application.alternateIconName == aliasName) {
                    println("iOS App Icon is already set to $aliasName")
                    return@dispatch_async
                }

                application.setAlternateIconName(aliasName) { error ->
                    if (error != null) {
                        // If this prints in your Xcode console, it means the name in your Enum
                        // doesn't match the name in Xcode's Asset Catalog/Info.plist!
                        println("CRITICAL iOS Icon Error: ${error.localizedDescription}")
                    } else {
                        println("iOS App Icon changed successfully to $aliasName")
                    }
                }
            }
        }
    }

    override fun getCurrentIconAlias(): String? {
        return UIApplication.sharedApplication.alternateIconName
    }
}