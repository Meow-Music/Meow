package com.meow.music.ui.theme

import android.content.Context
import com.meow.music.R
import com.meow.music.data.prefs.AppIconType
import com.meow.music.data.prefs.IconManager

/** The 4 app icons to pick from: each is a launcher activity-alias in the manifest. */
enum class AppIcon(val label: String, val type: AppIconType, val iconResId: Int) {
    MEOW_LOGO("Meow Logo", AppIconType.MEOW_LOGO, R.drawable.app_icon_meow_logo),
    COOL_LOGO("Cool Edition", AppIconType.COOL_LOGO, R.drawable.app_icon_cool_logo),
    HEADPHONE("Headphones", AppIconType.HEADPHONE, R.drawable.app_icon_headphone),
    POOKIE("Pookie Edition", AppIconType.POOKIE, R.drawable.app_icon_pookie_app),
}

/**
 * Switches the launcher icon using IconManager.
 */
object AppIcons {
    fun current(context: Context): AppIcon {
        val active = IconManager.getActivePresetIcon(context)
        return AppIcon.entries.firstOrNull { it.type == active } ?: AppIcon.MEOW_LOGO
    }

    fun set(context: Context, icon: AppIcon) {
        IconManager.setPresetIcon(context, icon.type)
    }
}
