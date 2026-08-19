package com.streamlivex.android.tv.content

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private object TvImageLoader {
    private val executor = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    // Hard cap: poster cache max 12 MB.
    private val cache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            value.byteCount / 1024
    }

    private val token = AtomicInteger(0)

    fun load(url: String, width: Int, height: Int, imageView: ImageView): Int {
        val requestToken = token.incrementAndGet()
        imageView.tag = requestToken

        cache.get(url)?.let {
            imageView.setImageBitmap(it)
            return requestToken
        }

        executor.execute {
            val bitmap = runCatching { fetchSampled(url, width, height) }.getOrNull()
            if (bitmap != null) cache.put(url, bitmap)

            main.post {
                if (imageView.tag == requestToken && bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }

        return requestToken
    }

    private fun fetchSampled(url: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(url).useConnection { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        val sample = calculateSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            reqWidth = reqWidth.coerceAtLeast(160),
            reqHeight = reqHeight.coerceAtLeast(240),
        )

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        return open(url).useConnection { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun calculateSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        var sample = 1
        var w = width
        var h = height

        while (w / 2 >= reqWidth && h / 2 >= reqHeight) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun open(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "StreamLiveX-TV")
            connect()
        }
    }

    private inline fun <T> HttpURLConnection.useConnection(
        block: (java.io.InputStream) -> T,
    ): T {
        try {
            return inputStream.use(block)
        } finally {
            disconnect()
        }
    }
}

@Composable
fun TvPosterImage(
    url: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color(0xFF172033)),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        adjustViewBounds = false
                    }
                },
                update = { view ->
                    view.setImageDrawable(null)
                    view.post {
                        TvImageLoader.load(
                            url = url,
                            width = view.width.coerceAtLeast(240),
                            height = view.height.coerceAtLeast(360),
                            imageView = view,
                        )
                    }
                },
            )
        }
    }
}
