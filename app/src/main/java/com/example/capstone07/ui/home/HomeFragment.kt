package com.example.capstone07.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone07.NetworkModule
import com.example.capstone07.R
import com.example.capstone07.model.Project
import com.example.capstone07.model.ProjectPostResponse
import com.example.capstone07.model.ProjectResponse
import com.example.capstone07.remote.ProjectService
import com.example.capstone07.ui.analysis.AnalysisTempFragment
import com.example.capstone07.ui.script.ScriptFragment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeFragment : Fragment() {

    private lateinit var adapter: HomeAdpater

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

        adapter = HomeAdpater(onProjectClick = { project ->
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

        loadProjectList()

        val addBtn = view.findViewById<ImageView>(R.id.home_add_iv)
        addBtn.setOnClickListener {
            showAddProjectDialog()
        }
    }

    // 프로젝트 생성 다이얼로그
    private fun showAddProjectDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_project, null)

        val nameEt = dialogView.findViewById<EditText>(R.id.et_project_name)
        val descEt = dialogView.findViewById<EditText>(R.id.et_project_desc)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("프로젝트 생성")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val name = nameEt.text.toString()
                val desc = descEt.text.toString()
                postProjectAndRefresh(name, desc)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 프로젝트 생성 + 리스트 갱신
    private fun postProjectAndRefresh(name: String, desc: String) {
        val projectService = NetworkModule.getClient().create(ProjectService::class.java)

        val project = Project(
            name = name,
            description = desc,
            color = "#FFFFFF"
        )

        projectService.postProject(project)
            .enqueue(object : Callback<ProjectPostResponse> {

                override fun onResponse(
                    call: Call<ProjectPostResponse>,
                    response: Response<ProjectPostResponse>
                ) {
                    if (response.isSuccessful && response.body()?.isSuccess == true) {
                        Log.d("PROJECT", "✅ 프로젝트 생성 성공")
                        loadProjectList()
                    } else {
                        Log.e("PROJECT", "❌ POST 실패: ${response.errorBody()?.string()}")
                    }
                }

                override fun onFailure(call: Call<ProjectPostResponse>, t: Throwable) {
                    Log.e("PROJECT", "❌ 서버 통신 실패: ${t.message}")
                }
            })
    }

    // 프로젝트 리스트 불러오기
    private fun loadProjectList() {
        val projectService = NetworkModule.getClient().create(ProjectService::class.java)

        projectService.getProjects()
            .enqueue(object : Callback<ProjectResponse> {

                override fun onResponse(
                    call: Call<ProjectResponse>,
                    response: Response<ProjectResponse>
                ) {
                    if (response.isSuccessful) {
                        val projects = response.body()?.result ?: emptyList()
                        adapter.setProjects(projects)
                    }
                }

                override fun onFailure(call: Call<ProjectResponse>, t: Throwable) {
                    Log.e("PROJECT", "❌ 리스트 불러오기 실패: ${t.message}")
                }
            })
    }
}
