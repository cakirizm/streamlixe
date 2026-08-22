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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private class TvNetworkImageView(context: android.content.Context) : ImageView(context) {
    var requestedKey: String? = null
}

private object TvImageLoader {
    private val executor = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    // Hard cap: poster bitmap cache max ~14 MB.
    private val cache = object : LruCache<String, Bitmap>(14 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            value.byteCount / 1024
    }

    private val token = AtomicInteger(0)

    fun load(
        url: String,
        width: Int,
        height: Int,
        imageView: ImageView,
    ) {
        val widthBucket = ((width.coerceAtLeast(1) + 119) / 120) * 120
        val heightBucket = ((height.coerceAtLeast(1) + 119) / 120) * 120
        val cacheKey = "$url@$widthBucket:$heightBucket"
        val networkView = imageView as? TvNetworkImageView
        if (networkView?.requestedKey == cacheKey) return

        val requestToken = token.incrementAndGet()
        imageView.tag = requestToken
        networkView?.requestedKey = cacheKey
        imageView.setImageDrawable(null)

        cache.get(cacheKey)?.let {
            imageView.setImageBitmap(it)
            return
        }

        executor.execute {
            val bitmap = runCatching {
                fetchAndDecode(
                    url = url,
                    reqWidth = widthBucket.coerceAtLeast(180),
                    reqHeight = heightBucket.coerceAtLeast(270),
                )
            }.getOrNull()

            if (bitmap != null) cache.put(cacheKey, bitmap)

            main.post {
                if (imageView.tag == requestToken && bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    /*
     * One HTTP request per poster.
     * The previous V2 loader opened the same URL twice (bounds + decode).
     * Here bytes are downloaded once with a hard size cap, then sampled locally.
     */
    private fun fetchAndDecode(
        url: String,
        reqWidth: Int,
        reqHeight: Int,
    ): Bitmap? {
        val bytes = downloadLimited(url, 4 * 1024 * 1024)
        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                reqWidth = reqWidth,
                reqHeight = reqHeight,
            )
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            options,
        )
    }

    private fun downloadLimited(
        url: String,
        maxBytes: Int,
    ): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 7_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "StreamLiveX-TV")
            connection.connect()

            if (connection.responseCode !in 200..299) return ByteArray(0)

            val announced = connection.contentLength
            if (announced > maxBytes) return ByteArray(0)

            val output = ByteArrayOutputStream(
                if (announced in 1..maxBytes) announced else 64 * 1024,
            )
            val buffer = ByteArray(16 * 1024)
            var total = 0

            connection.inputStream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) return ByteArray(0)
                    output.write(buffer, 0, read)
                }
            }

            return output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun calculateSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        if (width <= 0 || height <= 0) return 1
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
            key(url) {
                AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    TvNetworkImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        adjustViewBounds = false
                    }
                },
                update = { view ->
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
}

@Composable
fun TvLogoImage(
    url: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            key(url) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        TvNetworkImageView(context).apply {
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                            adjustViewBounds = false
                        }
                    },
                    update = { view ->
                        view.post {
                            TvImageLoader.load(url, view.width.coerceAtLeast(160), view.height.coerceAtLeast(80), view)
                        }
                    },
                )
            }
        }
    }
}
