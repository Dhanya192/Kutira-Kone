package com.kutira.kone.ui.ideas

import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kutira.kone.R

class AiChatAdapter(private val list: MutableList<AiChatMessage>) :
    RecyclerView.Adapter<AiChatAdapter.ViewHolder>() {

    inner class ViewHolder(val container: LinearLayout, val text: TextView) :
        RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val container = LinearLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            setPadding(8, 8, 8, 8)
        }
        val text = TextView(parent.context).apply {
            setPadding(24, 14, 24, 14)
            maxWidth = 900
            textSize = 15f
        }
        container.addView(text)
        return ViewHolder(container, text)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.text.text = item.message
        val params = holder.text.layoutParams as LinearLayout.LayoutParams

        if (item.isUser) {
            holder.container.gravity = Gravity.END
            holder.text.setBackgroundResource(R.drawable.ai_user_bubble)
            params.gravity = Gravity.END
            holder.text.setTextColor(Color.WHITE)
        } else {
            holder.container.gravity = Gravity.START
            holder.text.setBackgroundResource(R.drawable.ai_bot_bubble)
            params.gravity = Gravity.START
            val typedValue = TypedValue()
            holder.text.context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface,
                typedValue,
                true
            )
            holder.text.setTextColor(typedValue.data)
        }
        holder.text.layoutParams = params
    }

    override fun getItemCount() = list.size
}
