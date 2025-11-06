package com.example.capstone07.ui.analysis

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.material3.Button
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone07.R
import com.example.capstone07.model.ScriptResponseFragment

class AnalysisAdapter(private var scripts: List<ScriptResponseFragment> = emptyList()) :
    RecyclerView.Adapter<AnalysisAdapter.MyViewHolder>() {

    private val ITEM_COUNT = 10

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val scriptTextView: TextView = itemView.findViewById(R.id.tvScriptText)
        val gridKeywords: GridLayout = itemView.findViewById(R.id.gridKeywords)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.analysis_rcv_item, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
//        val scriptText = "안녕하세요. 지금부터 kbo의 역사에 대해서 소개하겠습니다."
//        val keywords = listOf("안녕", "kbo", "역사", "소개")
        val script = scripts[position]

        holder.scriptTextView.text = script.sentenceFragmentContent

        // 키워드 버튼
        holder.gridKeywords.removeAllViews()
        val keywordBtn = Button(holder.itemView.context).apply {
            text = script.keyword
            setTextColor(Color.parseColor("#205CFF"))
            textSize = 15f
        }
        holder.gridKeywords.addView(keywordBtn)

        println("Binding item at position: $position")
    }

    override fun getItemCount(): Int = scripts.size

    // 서버에서 데이터를 받아 adapter에 세팅할 때 호출
    fun setScripts(newScripts: List<ScriptResponseFragment>) {
        scripts = newScripts
        notifyDataSetChanged()
    }
}