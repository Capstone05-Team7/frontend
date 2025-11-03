package com.example.capstone07.ui.script

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone07.NetworkModule
import com.example.capstone07.R
import com.example.capstone07.model.ProjectResponse
import com.example.capstone07.model.ProjectResponseData
import com.example.capstone07.model.Script
import com.example.capstone07.model.ScriptResponse
import com.example.capstone07.remote.ProjectService
import com.example.capstone07.remote.ScriptService
import com.example.capstone07.ui.home.HomeAdpater
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ScriptFragment : Fragment() {

    private val projectId = 5 // 임의 설정

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_script, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editText = view.findViewById<EditText>(R.id.script_edit_et)
        val draftButton = view.findViewById<ImageButton>(R.id.script_draft_btn)
        val sendButton = view.findViewById<ImageButton>(R.id.script_complete_btn)

        // Retrofit 서비스 가져오기
        val scriptService = NetworkModule.getClient().create(ScriptService::class.java)

        sendButton.setOnClickListener {
            val scriptText = editText.text.toString()
            if (scriptText.isBlank()) {
                Toast.makeText(requireContext(), "내용을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val script = Script(
                projectId = projectId,
                script = scriptText
            )

            val call = scriptService.postscript(script)
            call.enqueue(object : Callback<ScriptResponse> {
                override fun onResponse(call: Call<ScriptResponse>, response: Response<ScriptResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "전송 성공", Toast.LENGTH_SHORT).show()
                        // 필요하면 response.body() 처리
                        val responseBody = response.body()
                        // 예: Log.d("API", "응답: ${responseBody?.message}")
                    } else {
                        Log.e("API", "서버 응답 실패: ${response.code()}")
                        Toast.makeText(requireContext(), "서버 응답 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ScriptResponse>, t: Throwable) {
                    Log.e("API", "서버 통신 실패: ${t.message}")
                    Toast.makeText(requireContext(), "서버 통신 실패", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}