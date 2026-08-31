package com.streamlivex.android.tv.data

import android.app.ActivityManager
import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object TvPerformanceManager {
    private val heavyExecutor =
        Executors.newSingleThreadExecutor {
            runnable ->
            Thread(
                runnable,
                "slx-heavy-worker",
            ).apply {
                priority =
                    Thread.NORM_PRIORITY - 1
            }
        }

    private val heavyRunning =
        AtomicBoolean(false)

    fun isLowRam(
        context: Context,
    ): Boolean {
        val manager =
            context.applicationContext
                .getSystemService(
                    Context.ACTIVITY_SERVICE,
                ) as? ActivityManager

        val runtimeLimitMb =
            Runtime.getRuntime()
                .maxMemory() /
                (1024L * 1024L)

        return manager?.isLowRamDevice == true ||
            runtimeLimitMb < 256L
    }

    fun pageSize(
        context: Context,
    ): Int =
        if (isLowRam(context)) {
            60
        } else {
            120
        }

    fun imageRamCacheBytes(
        context: Context,
    ): Int =
        if (isLowRam(context)) {
            6 * 1024 * 1024
        } else {
            16 * 1024 * 1024
        }

    fun imageDiskCacheBytes(
        context: Context,
    ): Long =
        if (isLowRam(context)) {
            48L * 1024L * 1024L
        } else {
            128L * 1024L * 1024L
        }

    fun imageMaxWidth(
        context: Context,
    ): Int =
        if (isLowRam(context)) {
            360
        } else {
            600
        }

    fun executeHeavy(
        label: String,
        block: () -> Unit,
    ) {
        heavyExecutor.execute {
            heavyRunning.set(true)
            try {
                block()
            } finally {
                heavyRunning.set(false)
            }
        }
    }

    fun isHeavyWorkRunning(): Boolean =
        heavyRunning.get()

    fun summary(
        context: Context,
    ): String =
        buildString {
            append(
                if (isLowRam(context)) {
                    "Düşük RAM modu"
                } else {
                    "Normal performans modu"
                },
            )
            append(" · Sayfa ")
            append(pageSize(context))
            append(" · Poster RAM ")
            append(
                imageRamCacheBytes(context) /
                    (1024 * 1024),
            )
            append(" MB")
        }
}
