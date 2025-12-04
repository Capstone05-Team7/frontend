package com.example.capstone07.ui.analysis

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone07.ui.speech.AnalysisFragment
import com.example.capstone07.NetworkModule
import com.example.capstone07.R
import com.example.capstone07.model.ScriptResponse
import com.example.capstone07.model.ScriptResponseFragment
import com.example.capstone07.model.StartRequestDto
import com.example.capstone07.remote.ScriptService
import com.example.capstone07.remote.StartService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AnalysisTempFragment : Fragment() {

    private lateinit var adapter: AnalysisAdapter

    private var projectId: Int = -1
    private var scripts: List<ScriptResponseFragment> = emptyList()

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

        // Retrofit service
        val scriptService =
            NetworkModule.getClient().create(ScriptService::class.java)

        // 서버에서 스크립트 목록 가져오기
        val call = scriptService.getScripts(projectId)
        call.enqueue(object : Callback<ScriptResponse> {
            override fun onResponse(
                call: Call<ScriptResponse>,
                response: Response<ScriptResponse>
            ) {
                if (response.isSuccessful) {
                    scripts =
                        response.body()?.result?.firstOrNull()?.scripts ?: emptyList()
                    adapter.setScripts(scripts)
                }
            }

            override fun onFailure(call: Call<ScriptResponse>, t: Throwable) {
                Log.e("API", "서버 통신 실패: ${t.message}")
            }
        })

        // 시작 버튼 처리
        val startBtn = view.findViewById<ImageButton>(R.id.analysis_start_ib)
        startBtn.setOnClickListener {

            val fragment = AnalysisFragment()
            val bundle = Bundle().apply {
                putParcelableArrayList("scripts", ArrayList(scripts))
            }
            fragment.arguments = bundle

            viewLifecycleOwner.lifecycleScope.launch {

                // Start API 서비스 가져오기
                val service = NetworkModule.getClient().create(StartService::class.java)

                // 요청 DTO 생성
                val request = StartRequestDto(projectId = projectId.toLong())

                // 🔥 API 요청은 그냥 실행만 하고, 응답 여부와 상관없이 처리
                launch {
                    try {
                        service.startPresentation(request)
                    } catch (e: Exception) {
                        Log.e("API", "Start 요청 실패: ${e.message}")
                    }
                }

                // 🔥 3초 기다림
                delay(3000)

                // 프래그먼트가 아직 살아있다면 화면 전환
                if (isAdded && !isDetached) {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.main_container_frl, fragment)
                        .addToBackStack(null)
                        .commitAllowingStateLoss()
                }
            }
        }

    }
}
