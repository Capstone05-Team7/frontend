/*
package com.example.capstone07.ui.speech

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
import com.example.capstone07.databinding.FragmentAnalysisBinding
import com.example.capstone07.remote.PresentationStompClient
import com.example.capstone07.remote.SimilarityResponse
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

    // 웹소켓 클라이언트 객체
    private lateinit var stompClient: PresentationStompClient
    // 발표 ID (아마 projectId를 쓸 것 같은데, 특정 프로젝트 조회 api가 없어서 테스트를 위해 하드코딩)
    private val PRESENTATION_ID = "1"

    private val TAG = "AnalysisFragment"

    // --- 힌트 타이머 로직 추가 ---
    // 백엔드 PresentationService의 MAX_SILENCE_MS와 동일하거나 약간 길게 설정
    private val HINT_TIMER_DELAY_MS = 0L

    // UI 스레드에서 동작할 핸들러
    private val hintHandler = Handler(Looper.getMainLooper())

    // 타이머가 만료되면 실행될 Runnable
    private val hintTimerRunnable = Runnable {
        //Log.d(TAG, "2초간 침묵 감지. 서버에 힌트 요청.")
        // TODO: PresentationStompClient에 "힌트 요청" 메서드 구현 필요
        //       (stompClient.sendSttText() 와는 다른, 힌트를 요청하는 별도 메시지 전송)
        if (::stompClient.isInitialized) {
            //stompClient.requestHint() // (가정) 힌트 요청 메서드 호출
        }
    }
    // --- 힌트 타이머 로직 끝 ---

    private val recognizedSpeechBuffer = StringBuilder()
    // 💡 추가: 버퍼 관리를 위한 상수
    private val MAX_WORD_COUNT = 20 // 최대 허용 단어 수
    private val TRIM_WORD_COUNT = 10 // 삭제할 단어 수 (MAX_WORD_COUNT의 절반)

    private var speakingSentence: String = ""

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

        binding.textViewNowspeaking.text = speakingSentence

        // STT 인텐트 및 Recognizer 초기화
        setupSpeechRecognizer()

        // 웹소켓 클라이언트 초기화 및 연결
        stompClient = PresentationStompClient(PRESENTATION_ID, ::onHintReceived, ::onProgressReceived)
        stompClient.connect()

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
            stompClient.disconnect() // 웹소켓 연결 해제
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
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {

            // 권한이 이미 있으면 바로 STT 시작
            startSTTListening()
        } else {
            // 권한이 없으면 Launcher를 통해 권한 요청 실행
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startSTTListening() {
        if (isListening) return

        isListening = true

        // 중단 버튼 활성화
        binding.imageViewStop.visibility = View.VISIBLE

        recognizedSpeechBuffer.clear()

        Toast.makeText(requireContext(), "발표를 시작합니다.", Toast.LENGTH_SHORT).show()
        speechRecognizer.startListening(recognitionIntent)

        // --- 힌트 타이머 로직 추가 ---
        // 발표 시작과 동시에 첫 힌트 타이머 시작
        startOrResetHintTimer()
    }

    private fun stopContinuousSTT() {
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.stopListening()
            isListening = false

            // 중단 버튼을 다시 숨기기
            binding.imageViewStop.visibility = View.GONE

            Toast.makeText(requireContext(), "발표가 종료되었습니다.", Toast.LENGTH_SHORT).show()

            // --- 힌트 타이머 로직 추가 ---
            // 발표 중지 시 타이머 제거
            cancelHintTimer()
        }
    }

    // RecognitionListener 구현
    inner class STTListener : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle) {
            Log.d(TAG, "말할 준비 완료")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "음성 입력 시작")
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

            // 인식 킵고잉
            restartListening()
        }

        override fun onResults(results: Bundle?) {

            // 최종 인식 결과 수신
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]

                // --- 유사도 계산을 위한 버퍼 활용------
                recognizedSpeechBuffer.append(recognizedText).append(" ")

                trimSpeechBufferIfNeeded()

                val textToSend = recognizedSpeechBuffer.toString().trim()
                if (textToSend.isNotEmpty()) {
                    //stompClient.sendSttText(textToSend)
                    Log.d(TAG, "누적 버퍼 전송 완료 (길이: ${textToSend.length}): $textToSend")
                }

                // --- 진행률 계산 ---
                stompClient.sendSttTextForProgress(speakingSentence, textToSend)

                // 최종 결과도 한 번 더 스트리밍하여 정확도 향상
                //stompClient.sendSttText(recognizedText)
                Log.d(TAG, "최종 결과 스트리밍: $recognizedText")
            }

            // 다음 인식 재시작
            restartListening()
        }

        override fun onPartialResults(partialResults: Bundle) {
            // 부분 결과를 수신하는 곳 (for 실시간 부분 전송)
            val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (matches != null && matches.isNotEmpty()) {
                val partialText = matches[0]

                // --- 수정된 부분 시작 ---
                if (isMeaningfulSpeech(partialText)) {
                    // 1. 유의미한 텍스트일 때만 타이머 리셋
                    startOrResetHintTimer()

                    Log.d(TAG, "부분 결과 스트리밍: $partialText")

                    Log.d(TAG, "현재 문장 추적 요청: $partialText")
                    stompClient.sendSttText(partialText)

                    // 2. 유의미한 텍스트일 때만 서버로 스트리밍
                    if (partialText.length>5){
                        Log.d(TAG, "현재 문장 추적 요청: $partialText")
                        stompClient.sendSttText(partialText)
                    }


                    */
/*recognizedSpeechBuffer.append(partialText).append(" ")

                    trimSpeechBufferIfNeeded()

                    val textToSend = recognizedSpeechBuffer.toString().trim()
                    if (textToSend.isNotEmpty()) {
                        stompClient.sendSttText(textToSend)
                        Log.d(TAG, "누적 버퍼 전송 완료 (길이: ${textToSend.length}): $textToSend")
                    }*//*


                } else {
                    // 3. 잡음이나 무의미한 텍스트는 전송하지 않고,
                    //    타이머 리셋도 하지 않아 계속 침묵 카운트가 진행되도록 함.
                    Log.v(TAG, "잡음성 텍스트 무시: $partialText")
                }

//                // --- 힌트 타이머 로직 추가 ---
//                // (STT 결과 수신) 타이머 리셋
//                startOrResetHintTimer()
//
//                Log.d(TAG, "부분 결과 스트리밍: $partialText")
//
//                // 서버로 실시간 STT 텍스트 스트리밍
//                stompClient.sendSttText(partialText)
//
//                // --- 힌트 타이머 로직 추가 ---
//                // (STT 결과 수신) 타이머 리셋
//                startOrResetHintTimer()
            }
        }

        override fun onEvent(eventType: Int, params: Bundle) {}
    }

    */
/**
     * STT 결과가 잡음이나 짧은 감탄사가 아닌 유의미한 발화인지 판단합니다.
     * 이 함수가 false를 반환하면 침묵 타이머가 리셋되지 않습니다.
     * @param text STT 엔진으로부터 수신된 텍스트
     * @return 유의미하면 true, 잡음성 텍스트면 false
     *//*

    private fun isMeaningfulSpeech(text: String): Boolean {
        // 1. 전처리: 구두점과 공백을 제거하여 실제 내용물만 비교할 수 있도록 정규화
        // 구두점과 공백을 제거해도 텍스트가 남아있는지 확인
        val normalizedText = text.replace(Regex("[\\s.,?!:;\"'\\-_]"), "").trim()

//        // 2. 최소 길이 검사 (정규화된 텍스트 기준)
//        // 2글자 미만은 대부분 잡음 (예: "아", "음")
//        if (normalizedText.length < 2) {
//            Log.v(TAG, "FILTERED: 짧은 길이 ($normalizedText)")
//            return false
//        }

        // 3. 반복되는 문자열 검사 (정규화된 텍스트 기준)
        // "ㅋㅋㅋ", "아아아", "......" 등 의미 없는 반복
        if (normalizedText.all { it == normalizedText.first() } && normalizedText.length > 1) {
            Log.v(TAG, "FILTERED: 반복 문자열 ($normalizedText)")
            return false
        }

        // 4. 잡음/감탄사 패턴 검사
        // '아', '에', '이', '오', '우', '음', '흠', '흐' 등으로만 이루어진 패턴 (한 글자 초과)
        val noisePattern = Regex("^[아에이오우음흠흐]+$")
        if (normalizedText.matches(noisePattern)) {
            Log.v(TAG, "FILTERED: 감탄사 패턴 ($normalizedText)")
            return false
        }

        // 5. 일반적인 잡음 키워드 포함 검사
        val commonNoiseKeywords = listOf("콜록", "에헴", "음", "흐음", "어", "아", "음...", "음...")
        for (keyword in commonNoiseKeywords) {
            if (normalizedText.contains(keyword)) {
                // "음"이 포함된 텍스트라도 길이가 길면 유의미할 수 있으므로,
                // 길이가 짧거나 (예: 4글자 미만) 해당 키워드와 매우 유사할 경우에만 필터링
                if (normalizedText.length < 4 || normalizedText == keyword.replace("...", "")) {
                    Log.v(TAG, "FILTERED: 일반 잡음 키워드 포함 ($normalizedText)")
                    return false
                }
            }
        }

        // 위 필터를 모두 통과하면 유의미한 발화로 간주하여 타이머 리셋
        return true
    }

    // 인식 재시작
    private fun restartListening() {
        if (isListening) {
            // 기존 리스너 중지하고 다시 시작
            speechRecognizer.cancel()
            speechRecognizer.startListening(recognitionIntent)
        }
    }
    */
/**
     * 힌트 타이머를 취소하고 2초 뒤에 새로 시작합니다.
     * (STT 결과가 수신될 때마다 호출됩니다)
     *//*

    private fun startOrResetHintTimer() {
        // 기존에 예약된 타이머(Runnable)가 있다면 취소
        hintHandler.removeCallbacks(hintTimerRunnable)
        // 2초(HINT_TIMER_DELAY_MS) 뒤에 힌트 요청 Runnable 실행
        hintHandler.postDelayed(hintTimerRunnable, HINT_TIMER_DELAY_MS)
    }

    private fun cancelHintTimer() {
        hintHandler.removeCallbacks(hintTimerRunnable)
    }

    // 웹소켓으로 힌트 메시지를 수신했을 때 실행될 콜백 함수
    private fun onHintReceived(response: SimilarityResponse) {
        Log.d(TAG, "서버에서 힌트 수신: ${response.mostSimilarText}")
        if (isAdded) {
            // 힌트를 UI에 표시
            binding.textViewResult.text = ""
            binding.textViewResult.text = response.mostSimilarText

            speakingSentence = response.mostSimilarText
            binding.textViewNowspeaking.text = "현재 발화 중인 문장: \n ${speakingSentence}"

        }
    }

    // 진행률 계산 결과 수신했을 때
    private fun onProgressReceived(progress: Float){
        Log.d(TAG, "서버에서 진행률 계산 결과 수신: ${progress.toString()}")
        if (isAdded) {
            // 진행률 UI에 표시(임시)
            binding.textViewProgress.text = ""
            binding.textViewProgress.text = ("진행률: ${progress.toString()}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // 웹소켓 연결 해제
        if (::stompClient.isInitialized) {
            stompClient.disconnect()
        }

        // Recognizer 객체 해제
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }

        // --- 힌트 타이머 로직 추가 ---
        // 화면 종료 시 핸들러 콜백 제거 (메모리 누수 방지)
        cancelHintTimer()

        _binding = null
    }

    private fun trimSpeechBufferIfNeeded() {
        // 버퍼를 공백을 기준으로 단어 리스트로 분리
        val words = recognizedSpeechBuffer.toString().trim().split("\\s+".toRegex())

        if (words.size > MAX_WORD_COUNT) {
            Log.d(TAG, "버퍼 단어 수 초과 (${words.size}개). 앞부분 ${TRIM_WORD_COUNT}개 삭제.")

            // 최신 내용 (words.size - TRIM_WORD_COUNT)개만 유지
            val newWords = words.subList(TRIM_WORD_COUNT, words.size)

            // 버퍼를 새로운 단어 리스트로 재구성
            recognizedSpeechBuffer.clear()
            recognizedSpeechBuffer.append(newWords.joinToString(" "))
        }
    }
}*/

package com.example.capstone07.ui.speech

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import com.example.capstone07.databinding.FragmentAnalysisBinding
import com.example.capstone07.remote.PresentationStompClient
import com.example.capstone07.remote.ProgressResponse
import com.example.capstone07.remote.SimilarityResponse
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

    // 웹소켓 클라이언트 객체
    private lateinit var stompClient: PresentationStompClient
    // 발표 ID (아마 projectId를 쓸 것 같은데, 특정 프로젝트 조회 api가 없어서 테스트를 위해 하드코딩)
    private val PRESENTATION_ID = "1"

    private val TAG = "AnalysisFragment"

    // --- 힌트 타이머 로직 추가 ---
    // 백엔드 PresentationService의 MAX_SILENCE_MS와 동일하거나 약간 길게 설정
    private val HINT_TIMER_DELAY_MS = 0L

    // UI 스레드에서 동작할 핸들러
    private val hintHandler = Handler(Looper.getMainLooper())

    // 타이머가 만료되면 실행될 Runnable
    private val hintTimerRunnable = Runnable {
        //Log.d(TAG, "2초간 침묵 감지. 서버에 힌트 요청.")
        // TODO: PresentationStompClient에 "힌트 요청" 메서드 구현 필요
        //       (stompClient.sendSttText() 와는 다른, 힌트를 요청하는 별도 메시지 전송)
        if (::stompClient.isInitialized) {
            //stompClient.requestHint() // (가정) 힌트 요청 메서드 호출
        }
    }
    // --- 힌트 타이머 로직 끝 ---

    private val recognizedSpeechBuffer = StringBuilder()
    // 💡 추가: 버퍼 관리를 위한 상수
    private val MAX_WORD_COUNT = 20 // 최대 허용 단어 수
    private val TRIM_WORD_COUNT = 10 // 삭제할 단어 수 (MAX_WORD_COUNT의 절반)

    private var speakingSentence: String = ""
    private var speakingId: String = ""

    // ============================================================================
    // [추가 시작] 1. 하이브리드 로직을 위한 변수와 스크립트 데이터
    // ============================================================================

    // 현재 메인 화면에 표시 중인 문장 ID 기억용
    private var currentDisplayId: String = ""

    // 스크립트 데이터 클래스 (durationSec: 힌트가 뜨기까지 걸리는 시간)
    data class ScriptItem(val id: String, val text: String, val durationSec: Long)

    private val scriptList = listOf(
        ScriptItem("1-1", "안녕하십니까? 오늘 여러분과 함께 한국 프로야구의 심장, KIA 타이거즈에 대해 이야기 나누고자 합니다.", 10L),
        ScriptItem("1-2", "타이거즈는 단순한 야구팀을 넘어선, 한국 스포츠 역사와 호남 지역민의 자부심 그 자체입니다.", 8L),
        ScriptItem("1-3", "저희 발표는 역사부터 현재, 그리고 미래 비전까지 폭넓게 다룰 것입니다.", 7L),
        ScriptItem("1-4", "타이거즈의 역사는 1982년 프로야구 리그 출범과 함께 창단된 해태 타이거즈에서 시작됩니다.", 10L),
        ScriptItem("1-5", "해태는 곧 KIA 타이거즈의 뿌리이자, 불멸의 'V11' 신화를 일군 주역입니다.", 9L)
    )

    // 타이머 핸들러
    private val scriptTimerHandler = Handler(Looper.getMainLooper())

    // 시간이 다 되었을 때 실행할 작업: "다음 문장 힌트 보여주기"
    private val scriptTimerRunnable = Runnable {
        showNextSentenceHint()
    }

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

        binding.textViewNowspeaking.text = speakingSentence

        // STT 인텐트 및 Recognizer 초기화
        setupSpeechRecognizer()

        // 웹소켓 클라이언트 초기화 및 연결
        stompClient = PresentationStompClient(PRESENTATION_ID, ::onHintReceived, ::onProgressReceived)
        stompClient.connect()

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
            stompClient.disconnect() // 웹소켓 연결 해제
        }

        // ============================================================================
        // [추가 시작] 초기 화면 텍스트 세팅
        // ============================================================================
        binding.textViewNowspeaking.text = "마이크를 켜면 발표 도우미가 시작됩니다."
        binding.textViewResult.text = ""
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
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {

            // 권한이 이미 있으면 바로 STT 시작
            startSTTListening()
        } else {
            // 권한이 없으면 Launcher를 통해 권한 요청 실행
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startSTTListening() {
        if (isListening) return

        isListening = true

        // 중단 버튼 활성화
        binding.imageViewStop.visibility = View.VISIBLE

        recognizedSpeechBuffer.clear()

        Toast.makeText(requireContext(), "발표를 시작합니다.", Toast.LENGTH_SHORT).show()
        speechRecognizer.startListening(recognitionIntent)

        // --- 힌트 타이머 로직 추가 ---
        // 발표 시작과 동시에 첫 힌트 타이머 시작
        startOrResetHintTimer()
    }

    private fun stopContinuousSTT() {
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.stopListening()
            isListening = false

            // 중단 버튼을 다시 숨기기
            binding.imageViewStop.visibility = View.GONE

            Toast.makeText(requireContext(), "발표가 종료되었습니다.", Toast.LENGTH_SHORT).show()

            // --- 힌트 타이머 로직 추가 ---
            // 발표 중지 시 타이머 제거
            cancelHintTimer()

            // ============================================================================
            // [추가 시작] 발표 끝나면 하이브리드 타이머도 꺼야 함
            // ============================================================================
            stopScriptTimer()
        }
    }

    // RecognitionListener 구현
    inner class STTListener : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle) {
            Log.d(TAG, "말할 준비 완료")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "음성 입력 시작")
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

            // 인식 킵고잉
            restartListening()
        }

        override fun onResults(results: Bundle?) {

            // 최종 인식 결과 수신
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]

                // --- 유사도 계산을 위한 버퍼 활용------
                recognizedSpeechBuffer.append(recognizedText).append(" ")

                trimSpeechBufferIfNeeded()

                val textToSend = recognizedSpeechBuffer.toString().trim()
                if (textToSend.isNotEmpty()) {
                    //stompClient.sendSttText(textToSend)
                    Log.d(TAG, "누적 버퍼 전송 완료 (길이: ${textToSend.length}): $textToSend")
                }

                // --- 진행률 계산 ---
                stompClient.sendSttTextForProgress(speakingId,speakingSentence, textToSend)

                // 최종 결과도 한 번 더 스트리밍하여 정확도 향상
                //stompClient.sendSttText(recognizedText)
                Log.d(TAG, "최종 결과 스트리밍: $recognizedText")
            }

            // 다음 인식 재시작
            restartListening()
        }

        override fun onPartialResults(partialResults: Bundle) {
            // 부분 결과를 수신하는 곳 (for 실시간 부분 전송)
            val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (matches != null && matches.isNotEmpty()) {
                val partialText = matches[0]

                // --- 수정된 부분 시작 ---
                if (isMeaningfulSpeech(partialText)) {
                    // 1. 유의미한 텍스트일 때만 타이머 리셋
                    startOrResetHintTimer()

                    Log.d(TAG, "부분 결과 스트리밍: $partialText")

                    Log.d(TAG, "현재 문장 추적 요청: $partialText")
                    stompClient.sendSttText(partialText)

                    stompClient.sendSttTextForProgress(speakingId, speakingSentence, partialText)

                    /*recognizedSpeechBuffer.append(partialText).append(" ")

                    trimSpeechBufferIfNeeded()

                    val textToSend = recognizedSpeechBuffer.toString().trim()
                    if (textToSend.isNotEmpty()) {
                        stompClient.sendSttText(textToSend)
                        Log.d(TAG, "누적 버퍼 전송 완료 (길이: ${textToSend.length}): $textToSend")
                    }*/

                } else {
                    // 3. 잡음이나 무의미한 텍스트는 전송하지 않고,
                    //    타이머 리셋도 하지 않아 계속 침묵 카운트가 진행되도록 함.
                    Log.v(TAG, "잡음성 텍스트 무시: $partialText")
                }

//                // --- 힌트 타이머 로직 추가 ---
//                // (STT 결과 수신) 타이머 리셋
//                startOrResetHintTimer()
//
//                Log.d(TAG, "부분 결과 스트리밍: $partialText")
//
//                // 서버로 실시간 STT 텍스트 스트리밍
//                stompClient.sendSttText(partialText)
//
//                // --- 힌트 타이머 로직 추가 ---
//                // (STT 결과 수신) 타이머 리셋
//                startOrResetHintTimer()
            }
        }

        override fun onEvent(eventType: Int, params: Bundle) {}
    }

    /**
     * STT 결과가 잡음이나 짧은 감탄사가 아닌 유의미한 발화인지 판단합니다.
     * 이 함수가 false를 반환하면 침묵 타이머가 리셋되지 않습니다.
     * @param text STT 엔진으로부터 수신된 텍스트
     * @return 유의미하면 true, 잡음성 텍스트면 false
     */
    private fun isMeaningfulSpeech(text: String): Boolean {
        // 1. 전처리: 구두점과 공백을 제거하여 실제 내용물만 비교할 수 있도록 정규화
        // 구두점과 공백을 제거해도 텍스트가 남아있는지 확인
        val normalizedText = text.replace(Regex("[\\s.,?!:;\"'\\-_]"), "").trim()

//        // 2. 최소 길이 검사 (정규화된 텍스트 기준)
//        // 2글자 미만은 대부분 잡음 (예: "아", "음")
//        if (normalizedText.length < 2) {
//            Log.v(TAG, "FILTERED: 짧은 길이 ($normalizedText)")
//            return false
//        }

        // 3. 반복되는 문자열 검사 (정규화된 텍스트 기준)
        // "ㅋㅋㅋ", "아아아", "......" 등 의미 없는 반복
        if (normalizedText.all { it == normalizedText.first() } && normalizedText.length > 1) {
            Log.v(TAG, "FILTERED: 반복 문자열 ($normalizedText)")
            return false
        }

        // 4. 잡음/감탄사 패턴 검사
        // '아', '에', '이', '오', '우', '음', '흠', '흐' 등으로만 이루어진 패턴 (한 글자 초과)
        val noisePattern = Regex("^[아에이오우음흠흐]+$")
        if (normalizedText.matches(noisePattern)) {
            Log.v(TAG, "FILTERED: 감탄사 패턴 ($normalizedText)")
            return false
        }

        // 5. 일반적인 잡음 키워드 포함 검사
        val commonNoiseKeywords = listOf("콜록", "에헴", "음", "흐음", "어", "아", "음...", "음...")
        for (keyword in commonNoiseKeywords) {
            if (normalizedText.contains(keyword)) {
                // "음"이 포함된 텍스트라도 길이가 길면 유의미할 수 있으므로,
                // 길이가 짧거나 (예: 4글자 미만) 해당 키워드와 매우 유사할 경우에만 필터링
                if (normalizedText.length < 4 || normalizedText == keyword.replace("...", "")) {
                    Log.v(TAG, "FILTERED: 일반 잡음 키워드 포함 ($normalizedText)")
                    return false
                }
            }
        }

        // 위 필터를 모두 통과하면 유의미한 발화로 간주하여 타이머 리셋
        return true
    }

    // 인식 재시작
    private fun restartListening() {
        if (isListening) {
            // 기존 리스너 중지하고 다시 시작
            speechRecognizer.cancel()
            speechRecognizer.startListening(recognitionIntent)
        }
    }
    /**
     * 힌트 타이머를 취소하고 2초 뒤에 새로 시작합니다.
     * (STT 결과가 수신될 때마다 호출됩니다)
     */
    private fun startOrResetHintTimer() {
        // 기존에 예약된 타이머(Runnable)가 있다면 취소
        hintHandler.removeCallbacks(hintTimerRunnable)
        // 2초(HINT_TIMER_DELAY_MS) 뒤에 힌트 요청 Runnable 실행
        hintHandler.postDelayed(hintTimerRunnable, HINT_TIMER_DELAY_MS)
    }

    private fun cancelHintTimer() {
        hintHandler.removeCallbacks(hintTimerRunnable)
    }

    // ============================================================================
    // [추가 시작] 2. 하이브리드 타이머 제어 로직 (새로 만드는 함수들)
    // ============================================================================

    // 타이머 시작 함수
    private fun startScriptTimer(scriptId: String) {
        // 1. 기존 타이머 취소
        stopScriptTimer()

        // 2. ID로 시간 정보 찾기
        val item = scriptList.find { it.id == "1-1" }

        if (item != null) {
            val delayMs = item.durationSec * 1000L
            // 3. 타이머 예약
            scriptTimerHandler.postDelayed(scriptTimerRunnable, delayMs)
            Log.d(TAG, "타이머 시작: $scriptId (${item.durationSec}초)")
        }
    }

    // 타이머 중지 함수
    private fun stopScriptTimer() {
        scriptTimerHandler.removeCallbacks(scriptTimerRunnable)
    }

    // 시간이 다 됐을 때 다음 문장을 힌트로 보여주는 함수
    private fun showNextSentenceHint() {
        val currentIndex = scriptList.indexOfFirst { it.id == currentDisplayId }

        // 다음 문장이 있으면
        if (currentIndex != -1 && currentIndex < scriptList.size - 1) {
            val nextItem = scriptList[currentIndex + 1]

            // 힌트 텍스트뷰(textViewResult)에 회색으로 표시
            binding.textViewResult.text = "[다음 내용 힌트]\n${nextItem.text}"
            binding.textViewResult.setTextColor(Color.GRAY)

            Log.d(TAG, "시간 초과! 힌트 표시: ${nextItem.id}")
        }
    }

    // 웹소켓으로 힌트 메시지를 수신했을 때 실행될 콜백 함수
    private fun onHintReceived(response: SimilarityResponse) {
        if (!isAdded) return

        activity?.runOnUiThread {
            // ============================================================================
            // [추가 시작] AI 응답과 타이머 연동 로직
            // ============================================================================
            val detectedId = response.mostSimilarId
            val detectedText = response.mostSimilarText

            // AI가 "새로운 문장"을 감지했으면 (현재 보고있는 것과 다를 때)
            if (detectedId != currentDisplayId) {
                Log.d(TAG, "화면 전환: $currentDisplayId -> $detectedId")

                // 1. ID 갱신
                currentDisplayId = detectedId
                speakingId = detectedId
                speakingSentence = detectedText

                // 2. 메인 화면 업데이트 (검은색)
                binding.textViewNowspeaking.text = "[${detectedId}]\n$detectedText"
                binding.textViewNowspeaking.setTextColor(Color.BLACK)

                // 3. 힌트 창은 지움 (새 문장 시작했으니)
                binding.textViewResult.text = ""

                // 4. 새 문장에 맞는 타이머 시작
                startScriptTimer(detectedId)
            }
        }
    }

    // 진행률 계산 결과 수신했을 때
    private fun onProgressReceived(progress: ProgressResponse){
        Log.d(TAG, "서버에서 진행률 계산 결과 수신: ${progress.nextScriptId}")
        if (isAdded) {
            // 진행률 UI에 표시(임시)
            binding.textViewProgress.text = ""
            binding.textViewProgress.text = ("다음 문장 id: ${progress.nextScriptId}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // 웹소켓 연결 해제
        if (::stompClient.isInitialized) {
            stompClient.disconnect()
        }

        // Recognizer 객체 해제
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }

        // --- 힌트 타이머 로직 추가 ---
        // 화면 종료 시 핸들러 콜백 제거 (메모리 누수 방지)
        cancelHintTimer()
        // ============================================================================
        // [추가 시작] 타이머 해제
        // ============================================================================
        stopScriptTimer()

        _binding = null
    }

    private fun trimSpeechBufferIfNeeded() {
        // 버퍼를 공백을 기준으로 단어 리스트로 분리
        val words = recognizedSpeechBuffer.toString().trim().split("\\s+".toRegex())

        if (words.size > MAX_WORD_COUNT) {
            Log.d(TAG, "버퍼 단어 수 초과 (${words.size}개). 앞부분 ${TRIM_WORD_COUNT}개 삭제.")

            // 최신 내용 (words.size - TRIM_WORD_COUNT)개만 유지
            val newWords = words.subList(TRIM_WORD_COUNT, words.size)

            // 버퍼를 새로운 단어 리스트로 재구성
            recognizedSpeechBuffer.clear()
            recognizedSpeechBuffer.append(newWords.joinToString(" "))
        }
    }
}