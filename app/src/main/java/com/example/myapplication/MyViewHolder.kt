package com.example.myapplication

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val positionText: TextView = itemView.findViewById(R.id.position)
    val titleText: TextView = itemView.findViewById(R.id.title)
}
