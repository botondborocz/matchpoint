package org.ttproject.icon

import platform.UIKit.UIApplication
import platform.UIKit.alternateIconName
import platform.UIKit.setAlternateIconName
import platform.UIKit.supportsAlternateIcons

class IosAppIconManager : AppIconManager {

    override fun changeIcon(icon: PremiumAppIcon) {
        val application = UIApplication.sharedApplication

        if (application.supportsAlternateIcons) {
            // Null resets to the primary CFBundlePrimaryIcon
            val iconName = if (icon == PremiumAppIcon.DEFAULT) null else icon.alias

            application.setAlternateIconName(iconName) { error ->
                if (error != null) {
                    println("Error changing app icon: ${error.localizedDescription}")
                }
            }
        }
    }

    override fun getCurrentIconAlias(): String? {
        return UIApplication.sharedApplication.alternateIconName
    }
}