package com.kutira.kone.ui.chat

import android.view.View
import com.kutira.kone.R
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kutira.kone.data.model.Message
import android.widget.TextView

class ChatAdapter(
    private val list: MutableList<Message>,
    private val currentUserId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_SENT = 1
    private val TYPE_RECEIVED = 2

    override fun getItemViewType(position: Int): Int {
        return if (list[position].senderId == currentUserId) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_SENT) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_sent, parent, false)
            SentViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_received, parent, false)
            ReceivedViewHolder(view)
        }
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = list[position]

        if (holder is SentViewHolder) {
            holder.text.text = message.message
        } else if (holder is ReceivedViewHolder) {
            holder.text.text = message.message
        }
    }

    fun update(newList: List<Message>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    class SentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvMessage)
    }

    class ReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvMessage)
    }
}