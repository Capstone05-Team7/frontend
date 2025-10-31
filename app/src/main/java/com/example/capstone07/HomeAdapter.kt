package com.example.capstone07

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class HomeAdpater :
    RecyclerView.Adapter<HomeAdpater.MyViewHolder>() {

    private val ITEM_COUNT = 10

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.item_image_view)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.home_rcv_item, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.imageView.contentDescription = "아이템 번호: $position"

        println("Binding item at position: $position")
    }

    override fun getItemCount(): Int {
        return ITEM_COUNT
    }
}