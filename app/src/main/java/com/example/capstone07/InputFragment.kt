package com.example.capstone07

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.capstone07.databinding.FragmentInputBinding
import com.example.capstone07.model.Script
import com.example.capstone07.model.ScriptResponse
import com.example.capstone07.remote.ScriptService
import retrofit2.Call
import retrofit2.create
import retrofit2.Callback
import retrofit2.Response


class InputFragment : Fragment() {

    private var _binding: FragmentInputBinding? = null
    private val binding get() = _binding!!

    private val TAG = "InputFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInputBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 확인 버튼 클릭 처리
        binding.buttonCheck.setOnClickListener {
            handleCheckButtonClick()
        }
    }

    private fun handleCheckButtonClick() {
        // TODO: 스크립트 전송
        val script = binding.editTextScriptInput.text.toString()

        if (script.isBlank()) {
            Toast.makeText(context, "스크립트를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        //postScript(script)
        findNavController().navigate(R.id.action_inputFragment_to_analysisFragment)
    }

    /**
     * 스크립트 데이터를 백엔드에 전송
     * @param scriptText 사용자가 입력한 스크립트 내용
     */
    private fun postScript(scriptText: String) {
        Log.d(TAG, scriptText)

        val service = NetworkModule.getClient().create<ScriptService>()

        val requestBody = Script(1, script = scriptText)    // 프로젝트id 임시로 하드코딩

        val call: Call<ScriptResponse> = service.postscript(requestBody)

        call.enqueue(object : Callback<ScriptResponse> {

            override fun onResponse(call: Call<ScriptResponse>, response: Response<ScriptResponse>) {

                if (response.isSuccessful) {
                    // 통신 성공
                    val scriptResponse = response.body()
                    Toast.makeText(context, "스크립트 전송 성공", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Success: ${scriptResponse?.message}")

                    // API 성공 시에만 발표 시작 준비 화면으로 이동
                    findNavController().navigate(R.id.action_inputFragment_to_analysisFragment)

                } else {
                    // 서버 응답 오류
                    val errorMessage = response.errorBody()?.string() ?: "알 수 없는 오류"
                    Log.e(TAG, "Error: ${response.code()} - $errorMessage")
                    Toast.makeText(context, "전송 실패: ${response.code()}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<ScriptResponse>, t: Throwable) {
                // 네트워크 연결 실패
                Log.e(TAG, "Network 실패", t)
                Toast.makeText(context, "서버 연결 실패. 네트워크 설정을 확인 필요", Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}