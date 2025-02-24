package com.tthhfugapiyo.turnplayer

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val timeText: TextView = itemView.findViewById(R.id.time)
    val positionText: TextView = itemView.findViewById(R.id.position)
    val titleText: TextView = itemView.findViewById(R.id.title)
    val linearLayout: LinearLayout = itemView.findViewById(R.id.linear_layout)
    val cardView: CardView = itemView.findViewById(R.id.card_view)
}
