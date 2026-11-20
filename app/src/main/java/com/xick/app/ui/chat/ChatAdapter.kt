package com.xick.app.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.xick.app.R
import com.xick.app.model.kick.chat.ChatMessage

/** Each row is a TextView; emotes render inline as real images (see ChatMessageFormatter). */
class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    private val items = mutableListOf<ChatMessage>()

    fun submitList(new: List<ChatMessage>) {
        val old = items.size
        if (new.size >= old && new.take(old) == items) {
            // Append-only (the common case): only notify for the new tail.
            val added = new.subList(old, new.size)
            items.addAll(added)
            notifyItemRangeInserted(old, added.size)
        } else {
            items.clear()
            items.addAll(new)
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false) as TextView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    class ViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(message: ChatMessage) = ChatMessageFormatter.format(textView, message)
    }
}
