package com.plitka.launcher.util

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager

/** «Плитка» сейчас домашний экран? */
fun Activity.isDefaultHome(): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return false
    return resolved.activityInfo?.packageName == packageName
}

/** Показать системный выбор домашнего экрана («Плитка» уже в списке). */
fun Activity.requestDefaultHome() {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    startActivity(Intent.createChooser(intent, "Выберите «Плитку»"))
}
