package com.expiryreminder.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class ItemAdapter(private val onClick: (Item) -> Unit) :
    ListAdapter<Item, ItemAdapter.Holder>(Diff) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.name)
        val details: TextView = view.findViewById(R.id.details)
        val due: TextView = view.findViewById(R.id.due)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        val daysLeft = item.daysLeft()

        holder.name.text = item.name
        holder.details.text =
            context.getString(R.string.row_details, item.category, Reminders.formatDate(item.expiry))
        holder.due.text = Reminders.dueText(context, daysLeft)
        holder.due.setTextColor(
            ContextCompat.getColor(
                context,
                when {
                    daysLeft <= 7 -> R.color.due_urgent
                    daysLeft <= 30 -> R.color.due_soon
                    else -> R.color.due_later
                }
            )
        )
        holder.itemView.setOnClickListener { onClick(item) }
    }

    private object Diff : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
    }
}
