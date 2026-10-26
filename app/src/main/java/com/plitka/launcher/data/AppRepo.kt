package com.plitka.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

data class AppEntry(val pkg: String, val label: String)

class AppRepo(private val ctx: Context) {

    private val pm: PackageManager get() = ctx.packageManager

    /** Все приложения, которые можно запустить с рабочего стола. */
    fun launchable(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        for (ri in list) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == ctx.packageName) continue
            if (!seen.add(pkg)) continue
            out.add(AppEntry(pkg, ri.loadLabel(pm).toString()))
        }
        val collator = java.text.Collator.getInstance(java.util.Locale("ru"))
        collator.strength = java.text.Collator.PRIMARY
        out.sortWith(Comparator { a, b -> collator.compare(a.label, b.label) })
        return out
    }

    fun icon(pkg: String, sizePx: Int): Bitmap? = try {
        val d = pm.getApplicationIcon(pkg)
        drawableToBitmap(d, sizePx)
    } catch (e: Exception) {
        null
    }

    fun label(pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    fun launch(pkg: String): Boolean {
        val i = pm.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        fun drawableToBitmap(d: Drawable, size: Int): Bitmap {
            if (d is BitmapDrawable && d.bitmap != null) {
                return Bitmap.createScaledBitmap(d.bitmap, size, size, true)
            }
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(0)
            val c = Canvas(bmp)
            d.setBounds(0, 0, size, size)
            d.draw(c)
            return bmp
        }
    }
}
