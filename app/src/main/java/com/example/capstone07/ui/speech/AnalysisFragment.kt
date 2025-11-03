package com.example.capstone07.ui.speech

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.capstone07.NetworkModule
import com.example.capstone07.databinding.FragmentAnalysisBinding
import com.example.capstone07.model.Speech
import com.example.capstone07.model.SpeechResponse
import com.example.capstone07.remote.SpeechService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.create
import java.util.Locale

class AnalysisFragment : Fragment() {

    private var _binding: FragmentAnalysisBinding? = null
    private val binding get() = _binding!!

    // SpeechRecognizer 객체
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognitionIntent: Intent

    // STT에 필요한 마이크 권한 요청용
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    // 마이크 인식 상태
    private var isListening = false

    // 침묵 인식용 Handler와 Runnable
    private val silenceHandler = Handler(Looper.getMainLooper())
    private var silenceRunnable: Runnable? = null
    private val SILENCE_DELAY = 500L // 0.5초를 침묵으로 간주

    // STT 결과를 누적할 변수
    private val recognizedScriptBuffer = StringBuilder()

    private val TAG = "AnalysisFragment"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 권한 요청 런처 등록
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            // 권한 요청 결과 처리
            if (isGranted) {
                startSTTListening()
            } else {
                Toast.makeText(requireContext(), "마이크 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // STT 인텐트 및 Recognizer 초기화
        setupSpeechRecognizer()

        // 처음엔 중단 버튼 숨기기
        binding.imageViewStop.visibility = View.GONE

        // 마이크 클릭 처리: STT 시작
        binding.imageViewMic.setOnClickListener {
            if (!isListening) {
                // 권한 확인 후 STT 시작
                checkMicrophonePermissionAndStartSTT()
            }
        }

        // 중단 버튼 클릭 처리: STT 중단
        binding.imageViewStop.setOnClickListener {
            stopContinuousSTT()
        }
    }

    // STT 설정 함수
    private fun setupSpeechRecognizer() {
        // SpeechRecognizer 객체 생성
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())

        // 인식 리스너 설정
        speechRecognizer.setRecognitionListener(STTListener())

        // STT를 위한 Intent 설정
        recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, requireContext().packageName)
            // 한국어 설정
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            // 부분 결과 수신 설정
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    // 마이크 권한 확인 및 STT 시작 로직
    private fun checkMicrophonePermissionAndStartSTT() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {

            // 권한이 이미 있으면 바로 STT 시작
            startSTTListening()
        } else {
            // 권한이 없으면 Launcher를 통해 권한 요청 실행
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startSTTListening() {
        if (isListening) return

        isListening = true

        // 중단 버튼 활성화
        binding.imageViewStop.visibility = View.VISIBLE

        Toast.makeText(requireContext(), "발표를 시작합니다.", Toast.LENGTH_SHORT).show()
        speechRecognizer.startListening(recognitionIntent)
    }

    private fun stopContinuousSTT() {
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.stopListening()
            isListening = false

            // 중단 버튼을 다시 숨기기
            binding.imageViewStop.visibility = View.GONE

            Toast.makeText(requireContext(), "발표가 종료되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // RecognitionListener 구현
    inner class STTListener : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle) {
            Log.d(TAG, "말할 준비 완료")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "음성 입력 시작")

            // 말이 다시 시작되면, 기존 침묵 감지 타이머 제거
            silenceRunnable?.let { silenceHandler.removeCallbacks(it) }
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray) { }

        override fun onEndOfSpeech() {
            Log.d(TAG, "음성 입력 종료")
        }

        override fun onError(error: Int) {
            // isListening 상태가 아니라면 사용자가 중단을 누른 것이므로 무시
            if (!isListening) return

            // 오류 처리
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 시간 초과"
                SpeechRecognizer.ERROR_NETWORK -> "네트워크 오류"
                SpeechRecognizer.ERROR_NO_MATCH -> "일치하는 결과 없음"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "인식 recognizer 사용 중"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "입력 시간 초과"
                else -> "기타 오류: $error"
            }
            Log.d(TAG, "오류 발생: $errorMessage")

            // 침묵 관련 오류가 발생했을 때만 타이머 시작
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                silenceRunnable = Runnable {
                    Log.d(TAG, "1.5초 침묵 감지")
                    sendSentenceToBackend()
                }
                silenceHandler.postDelayed(silenceRunnable!!, SILENCE_DELAY)
            }

            // 인식 킵고잉
            restartListening()
        }

        override fun onResults(results: Bundle?) {
            // 침묵 인식 타이머 제거
            silenceRunnable?.let { silenceHandler.removeCallbacks(it) }

            // 최종 인식 결과 수신
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]

                // 누적 버퍼에 텍스트 추가
                recognizedScriptBuffer.append(recognizedText).append(" ")
                Log.d(TAG, "누적된 스크립트: $recognizedScriptBuffer")
            }

            // 다음 인식 재시작
            restartListening()
        }

        override fun onPartialResults(partialResults: Bundle) {
            // 부분 결과를 수신하는 곳 (for 실시간 부분 전송)
            val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (matches != null && matches.isNotEmpty()) {
                Log.d(TAG, "부분 결과: ${matches[0]}")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle) {}
    }

    // 인식 재시작
    private fun restartListening() {
        if (isListening) {
            // 기존 리스너 중지하고 다시 시작
            speechRecognizer.cancel()
            speechRecognizer.startListening(recognitionIntent)
        }
    }

    // 백엔드 통신 함수
    private fun sendSentenceToBackend() {
        // 누적된 텍스트가 없으면 전송하지 않음
        if (recognizedScriptBuffer.isEmpty()) {
            Log.d(TAG, "누적 텍스트 없음. 힌트 요청 건너뜀.")
            return
        }

        val currentScript = recognizedScriptBuffer.toString().trim()
        Log.d(TAG, "전송할 누적 텍스트: $currentScript")

        // 이전 내용 숨기기(혼선 방지)

        // api 호출
        val service = NetworkModule.getClient().create<SpeechService>()

        val requestBody = Speech(
            projectNumber = 1,
            input = currentScript
        )

        service.getNextHint(requestBody).enqueue(object : Callback<SpeechResponse> {
            override fun onResponse(call: Call<SpeechResponse>, response: Response<SpeechResponse>) {

                if (response.isSuccessful) {
                    val hint = response.body()?.next
                    if (!hint.isNullOrEmpty()) {
                        // 서버가 반환한 다음 내용을 띄우기
                        //Toast.makeText(requireContext(), "다음 힌트: $hint", Toast.LENGTH_LONG).show()
                        binding.textViewResult.text = hint
                    }
                } else {
                    Log.e(TAG, "힌트 요청 실패: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<SpeechResponse>, t: Throwable) {
                Log.e(TAG, "네트워크 실패: ${t.message}")
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // 누적 버퍼 해제
        recognizedScriptBuffer.clear()

        // 침묵 인식 타이머 콜백 제거
        silenceRunnable?.let { silenceHandler.removeCallbacks(it) }

        // Recognizer 객체 해제
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }

        _binding = null
    }
}