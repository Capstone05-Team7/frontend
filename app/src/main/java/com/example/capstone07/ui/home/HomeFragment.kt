package com.example.capstone07.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone07.MainActivity
import com.example.capstone07.NetworkModule
import com.example.capstone07.R
import com.example.capstone07.model.Project
import com.example.capstone07.model.ProjectResponse
import com.example.capstone07.model.ProjectResponseData
import com.example.capstone07.model.ScriptResponse
import com.example.capstone07.model.ScriptResponseFragment
import com.example.capstone07.remote.ProjectService
import com.example.capstone07.remote.ScriptService
import com.example.capstone07.ui.analysis.AnalysisAdapter
import com.example.capstone07.ui.analysis.AnalysisTempFragment
import com.example.capstone07.ui.script.ScriptFragment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.home_scr_rcv)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val adapter = HomeAdpater(onProjectClick = { project ->
            val fragment = if (project.isScriptSaved) {
                AnalysisTempFragment().apply {
                    arguments = Bundle().apply { putInt("projectId", project.id) }
                }
            } else {
                ScriptFragment().apply {
                    arguments = Bundle().apply { putInt("projectId", project.id) }
                }
            }

            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_container_frl, fragment)
                .addToBackStack(null)
                .commit()

        })
        recyclerView.adapter = adapter

        // Retrofit 서비스 가져오기
        val projectService = NetworkModule.getClient().create(ProjectService::class.java)

        // 서버에서 프로젝트 데이터 가져오기
        val call = projectService.getProjects()
        call.enqueue(object : Callback<ProjectResponse> {
            override fun onResponse(call: Call<ProjectResponse>, response: Response<ProjectResponse>) {
                if (response.isSuccessful) {
                    val projects: List<ProjectResponseData> = response.body()?.result ?: emptyList()
                    adapter.setProjects(projects)  // adapter에 데이터 전달
                } else {
                    Log.e("API", "서버 응답 실패: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<ProjectResponse>, t: Throwable) {
                Log.e("API", "서버 통신 실패: ${t.message}")
            }
        })
    }
}