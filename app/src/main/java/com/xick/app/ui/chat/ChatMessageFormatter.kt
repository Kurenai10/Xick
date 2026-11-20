package com.xick.app.ui.chat

import android.content.Context
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.core.graphics.toColorInt
import coil.request.ImageRequest
import coil.size.Size
import coil.target.Target
import com.xick.app.R
import com.xick.app.model.kick.chat.ChatMessage
import com.xick.app.util.image.KickImageLoader

/**
 * Renders a chat line into a TextView: coloured bold username, then content
 * with `[emote:id:name]` tokens replaced by the real emote image from
 * files.kick.com/emotes/<id>/fullsize (animated webp/gif via coil-gif).
 *
 * Recycle-safe: each call stamps the TextView with a fresh generation tag;
 * an async emote result only mutates the TextView if its generation still
 * matches (i.e. the row wasn't recycled onto a different message). The text
 * is set as BufferType.SPANNABLE so getText() stays editable for the async
 * span swap — without that, setText copies it to a read-only SpannedString
 * and the emote image never lands (the bug that left grey placeholders).
 */
object ChatMessageFormatter {

    private val EMOTE = Regex("""\[emote:(\d+):([^\]]+)]""")
    private const val EMOTE_PX = 72
    private val genTagKey = R.id.chat_emote_generation

    fun format(textView: TextView, message: ChatMessage) {
        val generation = (textView.getTag(genTagKey) as? Int ?: 0) + 1
        textView.setTag(genTagKey, generation)

        val builder = SpannableStringBuilder()
        val nameStart = builder.length
        builder.append(message.sender.username)
        val color = runCatching { message.sender.identity.color?.toColorInt() }.getOrNull() ?: 0xFFB0B0B0.toInt()
        builder.setSpan(ForegroundColorSpan(color), nameStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(StyleSpan(android.graphics.Typeface.BOLD), nameStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.append(": ")

        val emoteSlots = mutableListOf<Triple<String, Int, Int>>() // id, start, end
        val content = message.content
        var last = 0
        EMOTE.findAll(content).forEach { m ->
            builder.append(content.substring(last, m.range.first))
            val start = builder.length
            // Placeholder is the readable emote name — if the image fails to
            // load, the user sees ":name:" instead of an "OBJ" tofu box.
            builder.append(m.groupValues[2])
            emoteSlots.add(Triple(m.groupValues[1], start, builder.length))
            last = m.range.last + 1
        }
        if (last < content.length) builder.append(content.substring(last))

        textView.setText(builder, TextView.BufferType.SPANNABLE)

        val context = textView.context
        emoteSlots.forEach { (id, start, end) ->
            loadEmote(context, textView, generation, id, start, end)
        }
    }

    private fun loadEmote(context: Context, textView: TextView, generation: Int, id: String, start: Int, end: Int) {
        val request = ImageRequest.Builder(context)
            .data("https://files.kick.com/emotes/$id/fullsize")
            .size(Size(EMOTE_PX, EMOTE_PX))
            .target(object : Target {
                override fun onSuccess(result: Drawable) {
                    if (textView.getTag(genTagKey) != generation) return // row recycled
                    val spannable = textView.text as? Spannable ?: return
                    if (end > spannable.length) return
                    result.setBounds(0, 0, EMOTE_PX, EMOTE_PX)
                    spannable.getSpans(start, end, ImageSpan::class.java).forEach { spannable.removeSpan(it) }
                    spannable.setSpan(ImageSpan(result, ImageSpan.ALIGN_BOTTOM), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (result is Animatable) {
                        result.callback = object : Drawable.Callback {
                            override fun invalidateDrawable(who: Drawable) { textView.invalidate() }
                            override fun scheduleDrawable(who: Drawable, what: Runnable, w: Long) {
                                textView.postDelayed(what, w - android.os.SystemClock.uptimeMillis())
                            }
                            override fun unscheduleDrawable(who: Drawable, what: Runnable) { textView.removeCallbacks(what) }
                        }
                        result.start()
                    }
                    textView.invalidate()
                }
            })
            .build()
        KickImageLoader.get(context).enqueue(request)
    }
}

