package io.github.yedidyatob.telegramnarrator.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded TDLib images (chat photos, ad media) by file id, so scrolling the chat list doesn't decode again. */
private object TdImageCache {
    private const val MAX_BYTES = 12 * 1024 * 1024
    private val cache = object : LruCache<Int, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: Int, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun get(fileId: Int): ImageBitmap? = cache.get(fileId)
    fun put(fileId: Int, image: ImageBitmap) {
        cache.put(fileId, image)
    }
}

/**
 * Decodes a TDLib file once it is downloaded; null until then (and when there is no file or it can't be read).
 * [loadFile] downloads the file and returns its local path.
 */
@Composable
internal fun rememberTdImage(fileId: Int?, loadFile: suspend (Int) -> String?): ImageBitmap? {
    val image by produceState(initialValue = fileId?.let(TdImageCache::get), fileId) {
        if (fileId == null) {
            value = null
            return@produceState
        }
        TdImageCache.get(fileId)?.let {
            value = it
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            try {
                loadFile(fileId)?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }?.also { TdImageCache.put(fileId, it) }
    }
    return image
}
