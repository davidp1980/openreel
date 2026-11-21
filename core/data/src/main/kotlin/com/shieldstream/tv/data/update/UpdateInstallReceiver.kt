package com.shieldstream.tv.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Log.i("Update", "Actualización instalada; limpiando caché de APKs")
        runCatching {
            val dir = File(context.cacheDir, "updates")
            dir.listFiles()?.forEach { it.delete() }
        }
    }
}
