package com.example.capstone07.ui.analysis

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone07.NetworkModule
import com.example.capstone07.R
import com.example.capstone07.model.ScriptResponse
import com.example.capstone07.model.ScriptResponseFragment
import com.example.capstone07.remote.ScriptService
import com.example.capstone07.ui.speech.AnalysisFragment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AnalysisTempFragment : Fragment() {

    private lateinit var adapter: AnalysisAdapter

    private var projectId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_analysis_temp, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        projectId = arguments?.getInt("projectId") ?: -1

        val recyclerView = view.findViewById<RecyclerView>(R.id.analysis_scr_rcv)
        recyclerView.layoutManager = LinearLayoutManager(context)

        adapter = AnalysisAdapter()
        recyclerView.adapter = adapter

        // Retrofit 서비스 가져오기
        val scriptService = NetworkModule.getClient().create(ScriptService::class.java)

        // 서버에서 스크립트 데이터 가져오기
        val call = scriptService.getScripts(projectId)
        call.enqueue(object : Callback<ScriptResponse> {
            override fun onResponse(call: Call<ScriptResponse>, response: Response<ScriptResponse>) {
                if (response.isSuccessful) {
                    val scripts: List<ScriptResponseFragment> =
                        response.body()?.result?.firstOrNull()?.scripts ?: emptyList()
                    adapter.setScripts(scripts)
                }
            }

            override fun onFailure(call: Call<ScriptResponse>, t: Throwable) {
                Log.e("API", "서버 통신 실패: ${t.message}")
            }
        })

        val startBtn = view.findViewById<ImageButton>(R.id.analysis_start_ib)
        startBtn.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main_container_frl, AnalysisFragment())
                .addToBackStack(null) // 뒤로가기 시 이전 화면으로 돌아가게
                .commit()
        }
    }
}