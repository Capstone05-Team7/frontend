package com.example.capstone07.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone07.R
import com.example.capstone07.model.ProjectResponse
import com.example.capstone07.model.ProjectResponseData
import com.example.capstone07.model.ScriptResponseFragment
import com.example.capstone07.ui.script.ScriptFragment

class HomeAdpater(
    private var projects: List<ProjectResponseData> = emptyList(),
    private val onProjectClick: (ProjectResponseData) -> Unit
) : RecyclerView.Adapter<HomeAdpater.MyViewHolder>() {

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val projectTitleTextView: TextView = itemView.findViewById(R.id.projectTitle)
        val projectContentTextView: TextView = itemView.findViewById(R.id.projectContent)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.home_rcv_item, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val project = projects[position]

        holder.projectTitleTextView.text = project.name
        holder.projectContentTextView.text = project.description

        // CardView 클릭 이벤트
        holder.itemView.setOnClickListener {
            onProjectClick(project)
        }
    }

    override fun getItemCount(): Int {
        return projects.size
    }

    fun setProjects(newProjects: List<ProjectResponseData>) {
        projects = newProjects
        notifyDataSetChanged()
    }
}