package com.example.capstone07.ui.speech

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.capstone07.R
import com.example.capstone07.databinding.FragmentAnalysisBinding
import com.example.capstone07.remote.PresentationStompClient
import com.example.capstone07.remote.ProgressResponse
import com.example.capstone07.remote.SimilarityResponse
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.rpc.ApiStreamObserver
import com.google.auth.oauth2.GoogleCredentials
import com.google.protobuf.ByteString
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.SpeechSettings
import com.google.cloud.speech.v1.StreamingRecognitionConfig
import com.google.cloud.speech.v1.StreamingRecognizeRequest
import com.google.cloud.speech.v1.StreamingRecognizeResponse

class AnalysisFragment : Fragment() {

    private var _binding: FragmentAnalysisBinding? = null
    private val binding get() = _binding!!

    // for Google Cloud STT
    private var speechClient: SpeechClient? = null
    private var requestObserver: ApiStreamObserver<StreamingRecognizeRequest>? = null

    // for AudioRecord
    private var audioRecord: AudioRecord? = null
    private val sampleRate = 16000 // STT API가 권장하는 표준 샘플 레이트
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var bufferSize = 0

    // STT에 필요한 마이크 권한 요청용
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    // 마이크 인식 상태
    private var isListening = false

    // 웹소켓 클라이언트 객체
    private lateinit var stompClient: PresentationStompClient
    // 발표 ID (아마 projectId를 쓸 것 같은데, 특정 프로젝트 조회 api가 없어서 테스트를 위해 하드코딩)
    private val PRESENTATION_ID = "1"

    private val TAG = "AnalysisFragment"

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 권한 요청 런처 등록
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            // 권한 요청 결과 처리
            if (isGranted) {
                // (!!) 권한 획득 시 바로 시작
                checkMicrophonePermissionAndStartSTT()
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

        // (!!) STT 클라이언트 초기화 (백그라운드 스레드에서)
        Thread {
            setupStreamingSTT()
        }.start()

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
            stopStreamingAudio()
            stompClient.disconnect() // 웹소켓 연결 해제
        }
    }

    /**
     * Google Cloud STT 클라이언트 초기화
     * (인증 파일 I/O가 있으므로 백그라운드 스레드에서 호출해야 함)
     */
    private fun setupStreamingSTT() {
        try {
            // 인증 파일(credential.json) 로딩
            val credentialsStream = requireContext().resources.openRawResource(R.raw.credential)
            val credentials = GoogleCredentials.fromStream(credentialsStream)

            // 인증 정보를 사용하여 SpeechSettings 생성
            val speechSettings = SpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build()

            // SpeechClient 인스턴스 생성
            speechClient = SpeechClient.create(speechSettings)
            Log.d(TAG, "SpeechClient 초기화 성공")

        } catch (e: Exception) {
            Log.e(TAG, "SpeechClient 초기화 실패", e)
            // 오류
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "STT 초기화 실패. 앱을 재시작하세요.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 마이크 권한 확인 및 STT 시작 로직
    private fun checkMicrophonePermissionAndStartSTT() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {

            // (!!) SpeechClient가 초기화되었는지 확인
            if (speechClient == null) {
                Toast.makeText(requireContext(), "STT 엔진을 초기화 중입니다. 잠시 후 다시 시도하세요.", Toast.LENGTH_SHORT).show()
                Thread { setupStreamingSTT() }.start() // (재시도)
                return
            }

            // (!!) 새 시작 함수 호출
            startStreamingAudio()
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // 기존 inner class STTListener 는 삭제합니다.

    /**
     * Google Cloud STT 서버로부터 실시간 응답(텍스트)을 수신하는 콜백 객체입니다.
     */
    private val responseObserver = object : ApiStreamObserver<StreamingRecognizeResponse> {

        /**
         * 서버에서 STT 결과(중간 또는 최종)가 도착했을 때 호출됩니다.
         * (이 함수는 백그라운드 스레드에서 실행됩니다)
         */
        override fun onNext(response: StreamingRecognizeResponse) {
            // 1. 유효한 결과가 있는지 확인
            val result = response.resultsList.firstOrNull()
            if (result == null || result.alternativesList.isEmpty()) {
                return
            }

            // 2. 인식된 텍스트 추출
            val transcript = result.alternativesList[0].transcript.trim()

            // 3. (중요) UI 스레드로 전환하여 기존 로직 실행
            activity?.runOnUiThread {
                if (result.isFinal) {
                    // --- '최종' 결과 (onResults와 유사) ---
                    Log.d(TAG, "[최종] $transcript")

                    // 기존 onResults 로직 (버퍼 누적 및 진행률 계산)
                    recognizedSpeechBuffer.append(transcript).append(" ")
                    trimSpeechBufferIfNeeded()
                    val textToSend = recognizedSpeechBuffer.toString().trim()

                    stompClient.sendSttTextForProgress(speakingId, speakingSentence, textToSend)

                } else {
                    // --- '중간' 결과 (onPartialResults와 유사) ---
                    Log.d(TAG, "[중간] $transcript")

                    // 기존 onPartialResults 로직 (잡음 필터링 및 힌트 요청)
                    if (isMeaningfulSpeech(transcript)) {
                        stompClient.sendSttText(transcript) // 힌트 추적 요청
                        stompClient.sendSttTextForProgress(speakingId, speakingSentence, transcript) // 진행률 즉시 반영
                    }
                }
            }
        }

        /** 오류 발생 시 (기존 onError와 유사) */
        override fun onError(t: Throwable) {
            Log.e(TAG, "STT 스트리밍 오류", t)
            // (필요시) 스트리밍 재시작 로직
        }

        /** 스트림이 정상 종료되었을 때 */
        override fun onCompleted() {
            Log.d(TAG, "STT 스트리밍 완료")
        }
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

    /**
     * AudioRecord를 시작하고, STT 스트리밍 요청을 시작합니다.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startStreamingAudio() {
        if (isListening) return

        // (권한 확인 로직은 checkMicrophonePermissionAndStartSTT 재활용)

        // (1) AudioRecord 초기화
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        // (2) STT 스트림 요청 시작
        // (responseObserver가 응답을 처리합니다)
        requestObserver = speechClient?.streamingRecognizeCallable()?.bidiStreamingCall(responseObserver)

        // (3) STT 스트림 설정 전송 (어떤 오디오인지 알려주기)
        val recognitionConfig = RecognitionConfig.newBuilder()
            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
            .setSampleRateHertz(sampleRate)
            .setLanguageCode("ko-KR") // 한국어 설정
            .setEnableAutomaticPunctuation(true) // 자동 구두점
            .build()

        val streamingConfig = StreamingRecognitionConfig.newBuilder()
            .setConfig(recognitionConfig)
            .setInterimResults(true) // (핵심!) 중간 결과 받기
            .build()

        val initialRequest = StreamingRecognizeRequest.newBuilder()
            .setStreamingConfig(streamingConfig)
            .build()

        requestObserver?.onNext(initialRequest)

        // (4) AudioRecord 녹음 시작
        audioRecord?.startRecording()
        isListening = true

        // (5) (핵심!) 오디오 읽기/전송을 위한 백그라운드 스레드 시작
        Thread {
            val buffer = ByteArray(bufferSize)
            while (isListening) {
                // 오디오 버퍼 읽기
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (readSize > 0) {
                    // 읽은 오디오 데이터를 ByteString으로 변환
                    val audioData = ByteString.copyFrom(buffer, 0, readSize)

                    // STT 서버로 오디오 데이터 스트리밍
                    val request = StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(audioData)
                        .build()
                    requestObserver?.onNext(request)
                }
            }
        }.start()

        // (6) UI 및 타이머 시작 (기존 로직)
        activity?.runOnUiThread {
            binding.imageViewStop.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "발표를 시작합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * AudioRecord를 중지하고, STT 스트리밍을 종료합니다.
     */
    private fun stopStreamingAudio() {
        if (!isListening) return

        isListening = false

        // (1) AudioRecord 중지 및 해제
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        // (2) STT 스트림 종료 알림
        requestObserver?.onCompleted()
        requestObserver = null

        // (3) UI 및 타이머 중지 (기존 로직)
        binding.imageViewStop.visibility = View.GONE
        Toast.makeText(requireContext(), "발표가 종료되었습니다.", Toast.LENGTH_SHORT).show()
        cancelHintTimer()

    }

    private fun cancelHintTimer() {
        hintHandler.removeCallbacks(hintTimerRunnable)
    }

    // 웹소켓으로 힌트 메시지를 수신했을 때 실행될 콜백 함수
    private fun onHintReceived(response: SimilarityResponse) {
        Log.d(TAG, "서버에서 힌트 수신: ${response.mostSimilarId}")
        if (isAdded) {
            // 힌트를 UI에 표시
            binding.textViewResult.text = ""
            binding.textViewResult.text = response.mostSimilarText

            speakingSentence = response.mostSimilarText
            speakingId = response.mostSimilarId
            binding.textViewNowspeaking.text = "현재 발화 중인 문장: \n ${speakingSentence}"

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

        // (!!) 스트리밍 중지
        if (isListening) {
            stopStreamingAudio()
        }

        // (!!) STT 클라이언트 해제
        speechClient?.shutdown()
        speechClient?.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)
        speechClient = null

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
}