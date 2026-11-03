package com.example.chat.ui.chats.detail

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.example.chat.domain.models.ChatMessage
import com.example.chat.ui.kit.ChatyConfirmationDialog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Whole canonical message from the selected bubble, never a highlighted text range. */
internal object MessageQuoteRenderer {
    fun render(message: ChatMessage, senderName: String, avatar: Bitmap? = null): Bitmap {
        require(SelectionAction.copy in SelectionActionPolicy.available(listOf(message)))
        require(message.text.length <= 12000) { "This message is too long for one quote image." }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 46f }
        val layout = StaticLayout.Builder.obtain(message.text, 0, message.text.length, bodyPaint, 800)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(16f, 1f).setIncludePad(false).build()
        val height = maxOf(440, 300 + layout.height)
        require(height <= 8192) { "This message is too long for one quote image." }
        val bitmap = Bitmap.createBitmap(1000, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xff111111.toInt()
        canvas.drawRoundRect(RectF(35f, 35f, 965f, height - 35f), 65f, 65f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; paint.color = 0xff333333.toInt()
        canvas.drawRoundRect(RectF(35f, 35f, 965f, height - 35f), 65f, 65f, paint)
        paint.style = Paint.Style.FILL
        // Initials are an explicit fallback, never an invented contact photo or verification badge.
        paint.color = 0xffdedee7.toInt(); canvas.drawCircle(880f, 130f, 52f, paint)
        paint.color = android.graphics.Color.BLACK; paint.textSize = 46f; paint.textAlign = Paint.Align.CENTER
        canvas.drawText(senderName.trim().take(1).uppercase().ifEmpty { "?" }, 880f, 146f, paint)
        if (avatar != null) {
            canvas.save()
            canvas.clipPath(android.graphics.Path().apply { addCircle(880f, 130f, 52f, android.graphics.Path.Direction.CW) })
            canvas.drawBitmap(avatar, null, RectF(828f, 78f, 932f, 182f), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        }
        paint.textAlign = Paint.Align.RIGHT; paint.color = android.graphics.Color.WHITE; paint.textSize = 44f
        val name = android.text.TextUtils.ellipsize(senderName, TextPaint(paint), 690f, android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(name, 800f, 132f, paint)
        paint.color = 0xff888888.toInt(); paint.textSize = 32f
        canvas.drawText("Message Quote", 800f, 176f, paint)
        paint.color = 0xff333333.toInt(); paint.strokeWidth = 3f
        canvas.drawLine(85f, 225f, 915f, 225f, paint)
        canvas.save(); canvas.translate(95f, 275f); layout.draw(canvas); canvas.restore()
        return bitmap
    }
}

@Composable
internal fun MessageQuoteDialog(message: ChatMessage, profile: com.example.chat.domain.models.UserProfile?, fallbackName: String, currentMessage: () -> ChatMessage?, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestMessage by rememberUpdatedState(currentMessage)
    val pendingExport = remember { java.util.concurrent.atomic.AtomicReference<File?>(null) }
    DisposableEffect(message) { onDispose { pendingExport.getAndSet(null)?.delete() } }
    val senderName = profile?.displayName?.takeIf { it.isNotBlank() } ?: fallbackName
    val avatarUrl = profile?.avatarUrl

    var preview by remember(message, senderName, avatarUrl) { mutableStateOf<Bitmap?>(null) }
    var error by remember(message) { mutableStateOf<String?>(null) }
    var sharing by remember(message) { mutableStateOf(false) }
    LaunchedEffect(message, senderName, avatarUrl) {
        runCatching {
            val avatar = withContext(Dispatchers.IO) {
                avatarUrl?.let { url -> runCatching {
                    val request = coil.request.ImageRequest.Builder(context).data(url).size(104).allowHardware(false).build()
                    (coil.Coil.imageLoader(context).execute(request) as? coil.request.SuccessResult)?.drawable?.let {
                        it.toBitmap(104, 104)
                    }
                }.getOrNull() }
            }
            withContext(Dispatchers.Default) { MessageQuoteRenderer.render(message, senderName, avatar) }
        }
            .onSuccess { preview = it }.onFailure { error = it.message ?: "Could not create quote image." }
    }
    ChatyConfirmationDialog(onDismissRequest = onClose, title = { Text("Image quote") },
        text = { Column {
            preview?.let { Image(it.asImageBitmap(), "Whole message quote preview", Modifier.fillMaxWidth().heightIn(max = 480.dp)) }
            error?.let { Text(it) }
            Text("Uses the original message, not a local modification. Sender initials are shown when a photo is unavailable.")
        } },
        confirmButton = { TextButton(enabled = preview != null && !sharing, onClick = {
            sharing = true
            scope.launch {
                try {
                    val live = latestMessage()
                    require(live != null && live == message && SelectionAction.copy in SelectionActionPolicy.available(listOf(live))) { "Message changed or is no longer shareable." }
                    val image = requireNotNull(preview)
                    val file = withContext(Dispatchers.IO) {
                        val folder = File(context.cacheDir, "message_quotes").apply { mkdirs() }
                        folder.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000L }?.forEach { it.delete() }
                        File.createTempFile("quote-", ".png", folder).also { out -> pendingExport.set(out); out.outputStream().use { require(image.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
                    }
                    require(latestMessage() == message) { "Message changed or is no longer shareable." }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("Message quote", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share message quote"))
                    pendingExport.set(null) // The recipient needs the granted URI; keep until bounded cache cleanup.
                } catch (failure: Exception) { pendingExport.getAndSet(null)?.delete(); if (failure is kotlinx.coroutines.CancellationException) throw failure; error = failure.message ?: "Could not share quote image." }
                finally { sharing = false }
            }
        }) { Text(if (sharing) "Preparing..." else "Share image") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
}
