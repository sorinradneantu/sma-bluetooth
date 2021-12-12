package ro.upt.sma.blechat

import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.ViewHolder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.LinkedList
import ro.upt.sma.blechat.ChatRecyclerViewAdapter.ChatEntryViewHolder

class ChatRecyclerViewAdapter : RecyclerView.Adapter<ChatEntryViewHolder>() {

    private val messages = LinkedList<String>()

    fun addMessage(message: String) {
        messages.add(0, message)
        notifyItemInserted(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatEntryViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat, parent, false)

        return ChatEntryViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatEntryViewHolder, position: Int) {
        holder.tvContent.text = messages[position]
    }

    override fun getItemCount(): Int {
        return messages.size
    }

    inner class ChatEntryViewHolder(itemView: View) : ViewHolder(itemView) {
        val tvContent: TextView = itemView.findViewById(R.id.tv_chat_item_content)
    }

}
