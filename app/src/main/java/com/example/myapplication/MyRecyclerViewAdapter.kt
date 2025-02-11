package com.example.myapplication

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class MyRecyclerViewAdapter(val list:ArrayList<RadioData>): RecyclerView.Adapter<MyViewHolder>() {

    // click listener
    private lateinit var listener: OnCellClickListener
    private var selectedPosition = -1

    interface OnCellClickListener {
        fun onItemClick(radioData: RadioData)
    }
    fun setOnCellClickListener(listener: OnCellClickListener){
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_recycler_view, parent, false)
        return MyViewHolder(itemView)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: MyViewHolder, @SuppressLint("RecyclerView") position: Int) {
        holder.positionText.text = position.toString()
        holder.titleText.text = list[position].getName()
        holder.timeText.text = list[position].getTime()

        holder.itemView.isSelected = position == selectedPosition
        if(holder.itemView.isSelected) {
            holder.itemView.setBackgroundColor(Color.RED)
        }else{
            holder.itemView.setBackgroundColor(Color.WHITE)
        }

        // click listener
        holder.itemView.setOnClickListener {
            selectedPosition = position
            notifyDataSetChanged()
            listener.onItemClick(list[position])
            holder.linearLayout.setBackgroundColor(Color.RED)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun changeSelection(position: Int){
        selectedPosition = position
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = list.size
}
