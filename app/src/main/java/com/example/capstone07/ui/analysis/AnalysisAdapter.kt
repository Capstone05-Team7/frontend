package com.example.capstone07.ui.analysis

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.capstone07.R
import com.example.capstone07.model.ScriptResponseFragment

class AnalysisAdapter(private var scripts: List<ScriptResponseFragment> = emptyList(),
                      private val onImageClick: (String) -> Unit) :
    RecyclerView.Adapter<AnalysisAdapter.MyViewHolder>() {

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val scriptTextView: TextView = itemView.findViewById(R.id.tvScriptText)
        // XML에서 추가한 ImageView ID (이전 질문의 XML 기준)
        val keywordImageView: ImageView = itemView.findViewById(R.id.ivKeywordImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.analysis_rcv_item, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val script = scripts[position]

        // 왼쪽 스크립트 텍스트 설정
        holder.scriptTextView.text = script.sentenceFragmentContent

        // 오른쪽 이미지 설정 (Glide 사용)
        // script.image가 URL 문자열이라고 가정합니다.
        Glide.with(holder.itemView.context)
            .load(script.image) // 이미지 URL 로드
            .centerCrop()       // XML의 scaleType과 맞춰줌 (선택사항)
            .placeholder(R.drawable.ic_launcher_background) // 로딩 중에 보여줄 이미지 (기본 아이콘 등)
            .error(R.drawable.ic_launcher_background)       // 로드 실패 시 보여줄 이미지
            .into(holder.keywordImageView)

        // 이미지 클릭 시 Fragment로 이벤트를 전달
        holder.keywordImageView.setOnClickListener {
            onImageClick(script.image)
        }
    }

    override fun getItemCount(): Int = scripts.size

    // 서버에서 데이터를 받아 adapter에 세팅할 때 호출
    fun setScripts(newScripts: List<ScriptResponseFragment>) {
        scripts = newScripts
        notifyDataSetChanged()
    }
}