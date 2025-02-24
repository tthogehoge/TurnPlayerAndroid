package com.tthhfugapiyo.turnplayer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors

class MyRecyclerViewAdapter(private val list:ArrayList<RadioData>): RecyclerView.Adapter<MyViewHolder>() {

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

        val colorP = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary)
        val colorBG = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnPrimary)
        holder.itemView.isSelected = position == selectedPosition
        holder.cardView.isSelected = position == selectedPosition
        if(holder.itemView.isSelected) {
            holder.itemView.setBackgroundColor(colorP)
        }else{
            holder.itemView.setBackgroundColor(colorBG)
        }

        // click listener
        holder.cardView.setOnClickListener {
            selectedPosition = position
            notifyDataSetChanged()
            listener.onItemClick(list[position])
            holder.linearLayout.setBackgroundColor(colorP)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun changeSelection(position: Int){
        selectedPosition = position
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = list.size
}
