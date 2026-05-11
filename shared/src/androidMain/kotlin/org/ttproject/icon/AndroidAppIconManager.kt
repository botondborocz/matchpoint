package org.ttproject

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import org.ttproject.icon.AppIconManager
import org.ttproject.icon.PremiumAppIcon

class AndroidAppIconManager(private val context: Context) : AppIconManager {

    override fun changeIcon(icon: PremiumAppIcon) {
        val packageManager = context.packageManager
        val packageName = context.packageName

        PremiumAppIcon.entries.forEach { appIcon ->
            // The ComponentName matches the android:name in the manifest
            val componentName = ComponentName(packageName, "$packageName.${appIcon.alias}")

            val state = if (appIcon == icon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            // DONT_KILL_APP helps prevent the app from crashing to the home screen instantly,
            // though some custom Android launchers might still force a restart.
            packageManager.setComponentEnabledSetting(
                componentName,
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    override fun getCurrentIconAlias(): String? {
        // Optional: Implement if you ever need to fallback to OS source-of-truth
        return null
    }
}