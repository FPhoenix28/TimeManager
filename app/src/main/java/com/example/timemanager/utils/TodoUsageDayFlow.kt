package com.example.timemanager.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/** One wake-up at the next existing usage-day boundary, also responds to clock/timezone changes. */
fun todoUsageDays(context: Context): Flow<UsageDayProvider.UsageDay> = flow {
    val app = context.applicationContext
    val changes = Channel<Unit>(Channel.CONFLATED)
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { changes.trySend(Unit) }
    }
    ContextCompat.registerReceiver(app, receiver, IntentFilter().apply {
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
        addAction(Intent.ACTION_DATE_CHANGED)
    }, ContextCompat.RECEIVER_NOT_EXPORTED)
    try {
        while (true) {
            val day = UsageDayProvider().currentUsageDay()
            emit(day)
            val waitMillis = (day.endTimeMillis - System.currentTimeMillis()).coerceAtLeast(1_000L)
            withTimeoutOrNull(waitMillis) { changes.receive() }
        }
    } finally {
        app.unregisterReceiver(receiver)
        changes.close()
    }
}.distinctUntilChanged()
