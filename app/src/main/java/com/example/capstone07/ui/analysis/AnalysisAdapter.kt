package com.example.capstone07.ui.analysis

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
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
        val keywordTextView: TextView = itemView.findViewById(R.id.tvKeywordText)
        val keywordEditText: EditText = itemView.findViewById(R.id.etKeywordText)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.analysis_rcv_item, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        //val scriptText = "안녕하세요. 지금부터 kbo의 역사에 대해서 소개하겠습니다."
        //val keywords = listOf("안녕", "kbo", "역사", "소개")
        val script = scripts[position]

        holder.scriptTextView.text = script.sentenceFragmentContent

        // 키워드 버튼
        holder.keywordTextView.visibility = View.VISIBLE
        holder.keywordEditText.visibility = View.GONE

        holder.keywordTextView.text = script.keyword
        holder.keywordEditText.setText(script.keyword)

        holder.keywordTextView.setOnClickListener {
            holder.keywordEditText.setText(holder.keywordTextView.text)

            holder.keywordTextView.visibility = View.GONE
            holder.keywordEditText.visibility = View.VISIBLE

            holder.keywordEditText.requestFocus()
            showKeyboard(holder.keywordEditText)
        }

        holder.keywordEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val modifiedText = holder.keywordEditText.text.toString()

                scripts[position].keyword = modifiedText

                holder.keywordTextView.text = modifiedText

                holder.keywordEditText.visibility = View.GONE
                holder.keywordTextView.visibility = View.VISIBLE

                hideKeyboard(holder.keywordEditText)
                true
            } else {
                false
            }
        }

        holder.keywordEditText.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus && holder.keywordEditText.visibility == View.VISIBLE) {
                val modifiedText = holder.keywordEditText.text.toString()

                scripts[position].keyword = modifiedText

                holder.keywordTextView.text = modifiedText
                holder.keywordEditText.visibility = View.GONE
                holder.keywordTextView.visibility = View.VISIBLE

                hideKeyboard(holder.keywordEditText)
            }
        }


    }

    override fun getItemCount(): Int = scripts.size

    // 서버에서 데이터를 받아 adapter에 세팅할 때 호출
    fun setScripts(newScripts: List<ScriptResponseFragment>) {
        scripts = newScripts
        notifyDataSetChanged()
    }


    fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // (참고: 키보드를 올리는 함수)
    fun showKeyboard(view: View) {
        if (view.requestFocus()) {
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            view.postDelayed({
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }, 100)
        }
    }
}