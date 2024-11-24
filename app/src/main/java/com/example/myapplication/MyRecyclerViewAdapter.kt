package com.example.myapplication

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class MyRecyclerViewAdapter(val list:ArrayList<RadioData>): RecyclerView.Adapter<MyViewHolder>() {

    // click listener
    private lateinit var listener: OnCellClickListener
    interface OnCellClickListener {
        fun onItemClick(radioData: RadioData)
    }
    fun setOnCellClickListener(listener: OnCellClickListener){
        this.listener = listener;
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_recycler_view, parent, false)
        return MyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.positionText.text = position.toString()
        holder.titleText.text = list[position].documentFile.name

        // click listener
        holder.itemView.setOnClickListener {
            listener.onItemClick(list[position])
        }
    }

    override fun getItemCount(): Int = list.size
}
