package com.example.shiro

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class ShiroWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        ShiroWidgetUpdates.refresh(context) { pending.finish() }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        val pending = goAsync()
        ShiroWidgetUpdates.refresh(context) { pending.finish() }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetControl.ACTION) {
            val action = WidgetAction.entries.firstOrNull { it.name == intent.getStringExtra(WidgetControl.EXTRA) } ?: return
            val pending = goAsync()
            WidgetControl.send(context, action) { pending.finish() }
        } else super.onReceive(context, intent)
    }
}

internal fun addShiroWidget(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val added = manager.isRequestPinAppWidgetSupported &&
        manager.requestPinAppWidget(ComponentName(context, ShiroWidgetProvider::class.java), null, null)
    if (!added) Toast.makeText(context, R.string.widget_add_manually, Toast.LENGTH_LONG).show()
}
