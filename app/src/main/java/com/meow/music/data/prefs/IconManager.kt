package com.meow.music.data.prefs

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.meow.music.MainActivity
import com.meow.music.R
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

enum class AppIconType(val label: String, val aliasName: String, val iconResId: Int) {
    MEOW_LOGO("Meow Logo", "com.meow.music.MainActivity", R.drawable.app_icon_meow_logo),
    COOL_LOGO("Cool Edition", "com.meow.music.MainActivityCoolLogo", R.drawable.app_icon_cool_logo),
    HEADPHONE("Headphones", "com.meow.music.MainActivityHeadphone", R.drawable.app_icon_headphone),
    POOKIE("Pookie Edition", "com.meow.music.MainActivityPookie", R.drawable.app_icon_pookie_app);

    companion object {
        fun fromAlias(name: String): AppIconType =
            entries.firstOrNull { it.aliasName == name } ?: MEOW_LOGO
    }
}

object IconManager {

    private const val CUSTOM_ICON_FILENAME = "custom_app_icon.png"

    fun getCustomIconFile(context: Context): File {
        return File(context.filesDir, CUSTOM_ICON_FILENAME)
    }

    fun hasCustomIcon(context: Context): Boolean {
        val file = getCustomIconFile(context)
        return file.exists() && file.length() > 0
    }

    fun loadCustomIconBitmap(context: Context): Bitmap? {
        val file = getCustomIconFile(context)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    /**
     * Safely processes a user-selected image from Uri, crops it to a square bitmap (512x512),
     * and saves it locally in internal storage.
     */
    fun saveCustomIconFromUri(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val original = BitmapFactory.decodeStream(stream) ?: return null
                val size = min(original.width, original.height)
                val x = (original.width - size) / 2
                val y = (original.height - size) / 2
                val cropped = Bitmap.createBitmap(original, x, y, size, size)
                val scaled = Bitmap.createScaledBitmap(cropped, 512, 512, true)

                val file = getCustomIconFile(context)
                FileOutputStream(file).use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                scaled
            }
        }.getOrNull()
    }

    fun clearCustomIcon(context: Context) {
        runCatching {
            val file = getCustomIconFile(context)
            if (file.exists()) file.delete()
        }
    }

    /**
     * Prompts the Android launcher to pin a custom 1-tap shortcut on the home screen
     * using the user's custom gallery image bitmap as the icon!
     */
    fun requestPinCustomShortcut(context: Context, iconBitmap: Bitmap): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return false
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val iconCompat = IconCompat.createWithBitmap(iconBitmap)

        val shortcutInfo = ShortcutInfoCompat.Builder(context, "custom_meow_shortcut")
            .setShortLabel("Meow")
            .setLongLabel("Meow Music")
            .setIcon(iconCompat)
            .setIntent(launchIntent)
            .build()

        return ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
    }

    /**
     * Safely switches between preset activity aliases.
     */
    fun setPresetIcon(context: Context, targetType: AppIconType) {
        val pm = context.packageManager
        AppIconType.entries.forEach { type ->
            val comp = ComponentName(context, type.aliasName)
            val state = if (type == targetType) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                pm.setComponentEnabledSetting(comp, state, PackageManager.DONT_KILL_APP)
            }
        }
    }

    fun getActivePresetIcon(context: Context): AppIconType {
        val pm = context.packageManager
        AppIconType.entries.forEach { type ->
            val comp = ComponentName(context, type.aliasName)
            val state = runCatching { pm.getComponentEnabledSetting(comp) }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return type
            }
        }
        return AppIconType.MEOW_LOGO
    }
}
