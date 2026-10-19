package io.github.yedidyatob.telegramnarrator.core.artwork

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.ParcelFileDescriptor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.yedidyatob.telegramnarrator.core.diagnostics.AutoLog
import io.github.yedidyatob.telegramnarrator.domain.avatar.AvatarPalette
import io.github.yedidyatob.telegramnarrator.domain.repository.ChatRepository
import io.github.yedidyatob.telegramnarrator.ui.components.AvatarInitials
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileNotFoundException

/**
 * Chat artwork for Android Auto browse items and the media notification / now-playing screen: the chat's small
 * TDLib photo (downloaded on demand), or Telegram-style initials on the chat's gradient. Not exported; read
 * access is granted per URI to the Android Auto hosts ([grantToAuto]) and Media3 reads it in-process.
 */
class ChatArtworkProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun chatRepository(): ChatRepository
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/png"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Read only")
        val context = context ?: throw FileNotFoundException()
        val chatId = ArtworkUris.chatIdOf(uri.pathSegments) ?: throw FileNotFoundException("Unknown artwork")
        val file = runCatching { runBlocking { artworkFile(context, chatId) } }
            .onFailure { AutoLog.error("artwork failed chat=$chatId", it) }
            .getOrNull() ?: throw FileNotFoundException("No artwork")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private suspend fun artworkFile(context: Context, chatId: Long): File? {
        val chats = EntryPointAccessors.fromApplication(context, Deps::class.java).chatRepository()
        val chat = chats.getChat(chatId) ?: return null
        val photo = chat.photoFileId?.let { id ->
            withTimeoutOrNull(PHOTO_TIMEOUT_MS) { chats.getFilePath(id) }?.let(::File)?.takeIf { it.isFile && it.length() > 0 }
        }
        return photo ?: initialsFile(context, chatId, AvatarInitials.of(chat.title))
    }

    private fun initialsFile(context: Context, chatId: Long, initials: String): File {
        val dir = File(context.cacheDir, "artwork").apply { mkdirs() }
        // Named by id and initials only: a renamed chat gets a new file, no title is stored
        val file = File(dir, "initials_${chatId}_${initials.hashCode()}.png")
        if (file.isFile) return file
        val size = SIZE_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (top, bottom) = AvatarPalette.forChat(chatId)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, size.toFloat(), top.toInt(), bottom.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), fill)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = size * 0.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val y = size / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(initials, size / 2f, y, text)
        val tmp = File(dir, file.name + ".tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        tmp.renameTo(file)
        return file
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0

    companion object {
        private const val SIZE_PX = 256
        private const val PHOTO_TIMEOUT_MS = 8_000L

        fun uriFor(context: Context, chatId: Long): Uri = Uri.parse(ArtworkUris.forChat(context.packageName, chatId))

        /** Lets the Android Auto hosts read [uri] (the provider itself is not exported). */
        fun grantToAuto(context: Context, uri: Uri, packages: Collection<String>) {
            for (pkg in packages) {
                runCatching { context.grantUriPermission(pkg, uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
        }
    }
}
