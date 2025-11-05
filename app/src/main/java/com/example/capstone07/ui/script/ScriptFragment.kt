package com.example.capstone07.ui.script

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone07.MainActivity
import com.example.capstone07.NetworkModule
import com.example.capstone07.R
import com.example.capstone07.model.ProjectResponse
import com.example.capstone07.model.ProjectResponseData
import com.example.capstone07.model.Script
import com.example.capstone07.model.ScriptRegisterResponse
import com.example.capstone07.model.ScriptResponse
import com.example.capstone07.remote.ProjectService
import com.example.capstone07.remote.ScriptService
import com.example.capstone07.ui.analysis.AnalysisTempFragment
import com.example.capstone07.ui.home.HomeAdpater
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ScriptFragment : Fragment() {

    private var projectId: Int = -1

    private lateinit var loadingLayout: FrameLayout
    private lateinit var imageViews: List<ImageView>
    private var loadingHandler: Handler? = null
    private var loadingRunnable: Runnable? = null
    private var currentImageIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_script, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        projectId = arguments?.getInt("projectId") ?: -1
        Log.d("ScriptFragment", "projectId: $projectId")

        loadingLayout = view.findViewById(R.id.script_loading_fl) // FrameLayout의 id 지정 필요
        imageViews = listOf(
            view.findViewById(R.id.script_blur_pro1_iv),
            view.findViewById(R.id.script_blur_pro2_iv),
            view.findViewById(R.id.script_blur_pro3_iv),
            view.findViewById(R.id.script_blur_pro4_iv)
        )

        // 로딩 애니메이션 준비
        loadingHandler = Handler(Looper.getMainLooper())
        loadingRunnable = object : Runnable {
            override fun run() {
                // 모든 이미지 숨기기
                imageViews.forEach { it.visibility = View.GONE }

                // 현재 인덱스 이미지만 보이기
                imageViews[currentImageIndex].visibility = View.VISIBLE

                // 다음 인덱스로 이동 (4 → 다시 0)
                currentImageIndex = (currentImageIndex + 1) % imageViews.size

                // 0.5초 후 다음 이미지로
                loadingHandler?.postDelayed(this, 500)
            }
        }

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

            loadingLayout.visibility = View.VISIBLE
            currentImageIndex = 0
            loadingHandler?.post(loadingRunnable!!)

            val script = Script(
                projectId = projectId,
                script = scriptText
            )

            val call = scriptService.postscript(script)
            call.enqueue(object : Callback<ScriptRegisterResponse> {
                override fun onResponse(
                    call: Call<ScriptRegisterResponse>,
                    response: Response<ScriptRegisterResponse>
                ) {
                    stopLoading()
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        if (body.isSuccess) {
                            Toast.makeText(requireContext(), "전송 성공", Toast.LENGTH_SHORT).show()

                            val fragment = AnalysisTempFragment().apply {
                                arguments = Bundle().apply {
                                    putInt("projectId", projectId)
                                }
                            }

                            requireActivity().supportFragmentManager.beginTransaction()
                                .replace(R.id.main_container_frl, fragment)
                                .addToBackStack(null)
                                .commit()
                        } else {
                            Log.e("API", "서버 처리 실패: ${body.message}")
                            Toast.makeText(requireContext(), "서버 처리 실패: ${body.message}", Toast.LENGTH_SHORT).show()
                        }
                        Log.d("API", "서버 응답 성공: $body")
                    } else {
                        Log.e("API", "서버 응답 실패: ${response.code()}")
                        Toast.makeText(requireContext(), "서버 응답 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ScriptRegisterResponse>, t: Throwable) {
                    stopLoading()
                    Log.e("API", "서버 통신 실패: ${t.message}")
                    Toast.makeText(requireContext(), "서버 통신 실패", Toast.LENGTH_SHORT).show()
                }
            })

        }
    }

    private fun stopLoading() {
        loadingHandler?.removeCallbacks(loadingRunnable!!)
        loadingLayout.visibility = View.GONE
        imageViews.forEach { it.visibility = View.GONE }
    }
}