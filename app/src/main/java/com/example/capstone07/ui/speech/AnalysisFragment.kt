package com.example.capstone07.ui.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
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
import androidx.lifecycle.lifecycleScope
import com.example.capstone07.R
import com.example.capstone07.data.AppDatabase
import com.example.capstone07.data.ImageCacheDao
import com.example.capstone07.databinding.FragmentAnalysisBinding
import com.example.capstone07.model.ImageCacheEntity
import com.example.capstone07.model.ScriptResponseFragment
import com.example.capstone07.remote.PresentationStompClient
import com.example.capstone07.remote.ProgressResponse
import com.example.capstone07.remote.SimilarityResponse
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.tasks.Tasks
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue


class AnalysisFragment : Fragment() {

    /**
     * 변수 및 상수
     */

    private var _binding: FragmentAnalysisBinding? = null
    private val binding get() = _binding!!

    // 받아오는 스크립트 정보
    val scripts = arguments?.getParcelableArrayList<ScriptResponseFragment>("scripts")

    // 이미지 캐싱 관련
    private lateinit var imageCacheDao: ImageCacheDao

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
    private var PRESENTATION_ID: String = "1"

    private val TAG = "AnalysisFragment"

    // UI 스레드에서 동작할 핸들러
    private val hintHandler = Handler(Looper.getMainLooper())

    private val recognizedSpeechBuffer = StringBuilder()

    // ---버퍼 관리를 위한 상수---
    private val MAX_WORD_COUNT = 20 // 최대 허용 단어 수
    private val TRIM_WORD_COUNT = 10 // 삭제할 단어 수 (MAX_WORD_COUNT의 절반)

    // --- 현재 상태 저장용 ---
    private var speakingSentence: String = ""   // 현재 말하고 있는 문장
    private var speakingId: String = ""     // 발화 중인 문장 id

    // --- '2-스레드 아키텍처'를 위한 변수 ---
    private val audioBuffer = LinkedBlockingQueue<ByteArray>()  // [스레드 A]가 녹음한 오디오 청크를 담아두는 '공용 바구니'
    private var audioRecordingThread: Thread? = null    // [스레드 A] AudioRecord에서 마이크 입력을 읽어 audioBuffer에 넣는 역할
    private var sttTransmissionThread: Thread? = null   // [스레드 B] audioBuffer에서 오디오를 꺼내 Google STT 서버로 전송하는 역할

    // --- 감시자 도입 ---
    private var lastSttResponseTime = 0L    // 마지막으로 서버 응답(onNext)을 받은 시간
    private val watchdogHandler = Handler(Looper.getMainLooper())   // 3.5초 동안 응답 없으면 재시작시키는 감시자
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (isListening) {
                val currentTime = System.currentTimeMillis()
                // (주시작 직후 3초간은 무시 (연결 초기화 시간 고려)
                if (currentTime - lastSttResponseTime > 3000) {
                    /*Log.w(TAG, "[감시자] 3초간 응답 없음. 전송 스레드 재시작")

                    // 전송 스레드만 리셋 (녹음은 계속됨 -> 끊김 없음)
                    startSttTransmission()

                    // 시간 갱신
                    lastSttResponseTime = System.currentTimeMillis()*/

                    // ⭐️ [핵심 수정] 큐에 데이터가 쌓여있는데도(>0) 응답이 없으면 진짜 문제!
                    // 큐가 비어있다면(=사용자가 말을 안 해서 보낼 게 없으면) 응답 없는 건 당연함.
                    if (audioBuffer.isNotEmpty()) {
                        Log.w(TAG, "[감시자] 큐에 데이터가 ${audioBuffer.size}개나 있는데 응답 없음. 재시작")
                        startSttTransmission()
                        lastSttResponseTime = System.currentTimeMillis()
                    } else {
                        // 큐가 비어있으면 그냥 시간만 갱신해서 살려둠 (False Alarm 방지)
                        // Log.v(TAG, "[감시자] 응답 없지만 큐도 비어있음(침묵 중). 패스.")
                        lastSttResponseTime = System.currentTimeMillis()
                    }
                }
                // 1초마다 감시
                watchdogHandler.postDelayed(this, 1000)
            }
        }
    }

    // 문장 조각을 모으는 변수
    private val accumulatedScript = StringBuilder()



    /**
     * ---------메소드들-----------
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        imageCacheDao = AppDatabase.getDatabase(requireContext()).imageCacheDao()

        // 권한 요청 런처 등록
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            // 권한 요청 결과 처리
            if (isGranted) {
                // 권한 획득 시 바로 시작
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

        PRESENTATION_ID = arguments
            ?.getInt("projectId")
            ?.toString()
            ?: "1"

        val scripts =
            arguments?.getParcelableArrayList<ScriptResponseFragment>("scripts")
                ?: return

        val appContext = requireContext().applicationContext

        // 이미지 캐싱 완료 전까지 마이크 비활성화
        binding.imageViewMic.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

            withContext(Dispatchers.Main) {
                binding.progressLoading.visibility = View.VISIBLE
                binding.imageViewMic.visibility = View.INVISIBLE
                binding.imageViewMic.isEnabled = false
            }

            // imageCacheDao.clearAll()         // DB 초기화
            // clearAllCachedImages(appContext) // 시작하기 전 이미 있는 캐시 이미지들 삭제

            val jobs = scripts.map { script ->
                launch {
                    val sentenceId = script.sentenceId
                    val imageUrl = script.image
                    Log.d("Image", "이미지 경로:, path=$imageUrl")

                    if (imageCacheDao.exists(PRESENTATION_ID.toInt(), sentenceId)) {
                        Log.w("ImageCache", "⏭️ 이미 DB에 존재해서 스킵됨: id=$sentenceId")
                        return@launch
                    }

                    try {
                        val bitmap = downloadBitmap(imageUrl)
                        val path = saveBitmap(appContext, bitmap, PRESENTATION_ID.toInt(), sentenceId)
                        Log.d("ImageCache", "📂 파일 저장 완료: id=$sentenceId, path=$path")

                        imageCacheDao.insert(
                            ImageCacheEntity(
                                projectId = PRESENTATION_ID.toInt(),
                                sentenceId = sentenceId,
                                filePath = path
                            )
                        )

                    } catch (e: Exception) {
                        Log.e("ImageCache", "이미지 처리 실패: id=$id", e)
                    }
                }
            }

            jobs.forEach { it.join() }

            withContext(Dispatchers.Main) {
                binding.progressLoading.visibility = View.GONE
                binding.imageViewMic.visibility = View.VISIBLE
                binding.imageViewMic.isEnabled = true

                // 웹소켓 클라이언트 초기화 및 연결
                stompClient = PresentationStompClient(PRESENTATION_ID, ::onHintReceived, ::onProgressReceived)
                stompClient.connect()
            }
        }

        binding.textViewNowspeaking.text = speakingSentence

        // STT 클라이언트 초기화 (백그라운드 스레드에서)
        Thread {
            setupStreamingSTT()
        }.start()

        // 처음엔 중단 버튼 숨기기
        binding.imageViewStop.visibility = View.GONE

        // 마이크 클릭 처리
        binding.imageViewMic.setOnClickListener {
            if (!isListening) {
                // 권한 확인 후 STT 시작
                checkMicrophonePermissionAndStartSTT()
            }
        }

        // 중단 버튼 클릭 처리
        binding.imageViewStop.setOnClickListener {
            stopStreamingAudio()    // STT 중단
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

    // 마이크 권한 확인 및 STT 시작
    private fun checkMicrophonePermissionAndStartSTT() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {

            // SpeechClient 초기화
            if (speechClient == null) {
                Toast.makeText(requireContext(), "STT 엔진을 초기화 중입니다. 잠시 후 다시 시도하세요.", Toast.LENGTH_SHORT).show()
                Thread { setupStreamingSTT() }.start() // 재시도
                return
            }

            // 녹음 및 오디오 스레드, STT 전송 스레드 시작
            startStreamingAudio()
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    /**
     * Google Cloud STT 서버로부터 실시간 응답(변환 텍스트)을 수신하는 콜백 객체
     */
    private val responseObserver = object : ApiStreamObserver<StreamingRecognizeResponse> {

        /**
         * 서버에서 STT 결과가 도착했을 때 호출
         * (백그라운드 스레드에서 실행)
         */
        override fun onNext(response: StreamingRecognizeResponse) {
            // 최근 응답 시간 갱신
            lastSttResponseTime = System.currentTimeMillis()

            // 유효한 결과가 있는지 확인
            val result = response.resultsList.firstOrNull()
            if (result == null || result.alternativesList.isEmpty()) {
                return
            }

            // 인식된 텍스트 추출
            val transcript = result.alternativesList[0].transcript.trim()

            // UI 스레드로 전환하여 작업
            activity?.runOnUiThread {
                if (result.isFinal) {
                    // --- '최종' 결과 (onResults와 유사) ---
                    Log.d(TAG, "[최종] $transcript")

                    // 버퍼 누적 및 진행률 계산
                    recognizedSpeechBuffer.append(transcript).append(" ")
                    //trimSpeechBufferIfNeeded()  // 버퍼 관리
                    val textToSend = recognizedSpeechBuffer.toString().trim()

                    stompClient.sendSttTextForProgress(speakingId, speakingSentence, textToSend)

                } else {
                    // --- '중간' 결과 (onPartialResults와 유사) ---
                    Log.d(TAG, "[중간] $transcript")

                    // 잡음 필터링 해서 STT 전송
                    if (isMeaningfulSpeech(transcript)) {
                        stompClient.sendSttText(transcript) // STT 전송
                        //stompClient.sendSttTextForProgress(speakingId, speakingSentence, transcript) // 진행률 계산
                    }
                }
            }
        }

        /** 오류 발생 시  */
        override fun onError(t: Throwable) {
            Log.e(TAG, "STT 스트리밍 오류", t)
            // 사용자가 중지한 게 아니라면, 전송 스레드만 재시작
            if (isListening) {
                activity?.runOnUiThread { startSttTransmission() }
            }
        }

        /** 스트림이 정상 종료되었을 때 (재시작) */
        override fun onCompleted() {
            Log.d(TAG, "STT 스트리밍 완료")
            // 사용자가 중지한 게 아니라면, 전송 스레드만 재시작
            if (isListening) {
                activity?.runOnUiThread { startSttTransmission() }
            }
        }
    }

    /**
     * STT 결과가 잡음이나 짧은 감탄사가 아닌 유의미한 발화인지 판단합니다.
     * @param text STT 엔진으로부터 수신된 텍스트
     * @return 유의미하면 true, 잡음성 텍스트면 false
     */
    private fun isMeaningfulSpeech(text: String): Boolean {
        // 전처리: 구두점과 공백을 제거하여 실제 내용물만 비교할 수 있도록 정규화
        // 구두점과 공백을 제거해도 텍스트가 남아있는지 확인
        val normalizedText = text.replace(Regex("[\\s.,?!:;\"'\\-_]"), "").trim()

        // 최소 길이 검사 (정규화된 텍스트 기준)
        // 2글자 미만은 대부분 잡음 ("아", "음" 등)
        if (normalizedText.length < 20) {
            //Log.v(TAG, "FILTERED: 짧은 길이 ($normalizedText)")
            return false
        }

        // 반복되는 문자열 검사 (정규화된 텍스트 기준)
        // "ㅋㅋㅋ", "아아아", "......" 등 의미 없는 반복
        if (normalizedText.all { it == normalizedText.first() } && normalizedText.length > 1) {
            Log.v(TAG, "FILTERED: 반복 문자열 ($normalizedText)")
            return false
        }

        // 잡음/감탄사 패턴 검사
        // '아', '에', '이', '오', '우', '음', '흠', '흐' 등으로만 이루어진 패턴
        val noisePattern = Regex("^[아에이오우음흠흐]+$")
        if (normalizedText.matches(noisePattern)) {
            Log.v(TAG, "FILTERED: 감탄사 패턴 ($normalizedText)")
            return false
        }

        // 일반적인 잡음 키워드 포함 검사
        val commonNoiseKeywords = listOf("콜록", "에헴", "음", "흐음", "어", "아", "음...", "음...")
        for (keyword in commonNoiseKeywords) {
            if (normalizedText.contains(keyword)) {
                // 잡음이 포함된 텍스트라도 길이가 길면 유의미할 수 있으므로, 길이가 짧거나 (4글자 미만으로 설정) 해당 키워드와 매우 유사할 경우에만 필터링
                if (normalizedText.length < 4 || normalizedText == keyword.replace("...", "")) {
                    Log.v(TAG, "FILTERED: 일반 잡음 키워드 포함 ($normalizedText)")
                    return false
                }
            }
        }

        // 위 필터를 모두 통과하면 유의미한 발화로 간주
        return true
    }

    /**
     * AudioRecord를 시작하고, STT 스트리밍 요청을 시작합니다.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startStreamingAudio() {
        if (isListening) return
        isListening = true

        // AudioRecord 초기화
        try {
            bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize
            )
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "마이크 초기화 실패: ${e.message}")
            isListening = false
            return
        }

        // [스레드 A] 오디오 녹음 스레드
        audioRecordingThread = Thread {
            Log.d(TAG, "[스레드 A] 녹음 시작")
            val buffer = ByteArray(bufferSize)
            var errorCount = 0

            while (isListening) {
                try {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                    if (readSize > 0) {
                        // 정상: 큐에 데이터 넣기
                        audioBuffer.offer(buffer.copyOf(readSize))
                        errorCount = 0 // 성공하면 에러 카운트 초기화
                    } else {
                        // 비정상: 오디오 읽기 실패 예외 처리
                        Log.w(TAG, "[스레드 A] 오디오 읽기 실패 (코드: $readSize)")
                        errorCount++

                        // 연속으로 에러가 나면 잠깐 쉬어줌 (CPU 과부하 방지)
                        if (errorCount > 10) Thread.sleep(100)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[스레드 A] 치명적 오류: ${e.message}")
                }
            }
            Log.d(TAG, "[스레드 A] 녹음 스레드 종료")
        }
        audioRecordingThread?.start()

        // [스레드 B] 시작
        startSttTransmission()

        // 감시자 가동 (현재 시간으로 초기화)
        lastSttResponseTime = System.currentTimeMillis()
        watchdogHandler.removeCallbacks(watchdogRunnable)
        watchdogHandler.postDelayed(watchdogRunnable, 2000) // 2초 뒤부터 감시 시작

        // STOMP 연결 상태 확인 및 재연결
        if (!stompClient.isConnected) {
            // 이미 onViewCreated에서 연결했더라도, 중간에 끊겼으면 다시 연결 시도
            stompClient.connect()
        }

        // UI 스레드 작업
        activity?.runOnUiThread {
            binding.imageViewStop.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "발표를 시작합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * [스레드 B] STT 전송 스트림 및 스레드를 (재)시작합니다.
     */
    private fun startSttTransmission() {
        if (!isListening) return
        Log.d(TAG, "[스레드 B] STT 전송 스트림 (재)시작...")

        // 스트림 연결
        try {
            requestObserver = speechClient?.streamingRecognizeCallable()?.bidiStreamingCall(responseObserver)

            val recognitionConfig = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(sampleRate)
                .setLanguageCode("ko-KR")
                .setEnableAutomaticPunctuation(true)
                .build()
            val streamingConfig = StreamingRecognitionConfig.newBuilder()
                .setConfig(recognitionConfig)
                .setInterimResults(true)
                .build()
            val initialRequest = StreamingRecognizeRequest.newBuilder()
                .setStreamingConfig(streamingConfig)
                .build()

            requestObserver?.onNext(initialRequest)

            Thread.sleep(200)

        } catch (e: Exception) {
            Log.e(TAG, "[스레드 B] 스트림 초기화 실패: ${e.message}")
            return
        }

        // [스레드 B] 전송 루프
        sttTransmissionThread = Thread {
            Log.d(TAG, "[스레드 B] 전송 루프 진입")
            while (isListening) {
                try {
                    // 큐에서 데이터 꺼내기 (데이터가 없으면 여기서 대기)
                    val audioData = audioBuffer.take()

                    // STT 전송
                    val request = StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(ByteString.copyFrom(audioData))
                        .build()
                    requestObserver?.onNext(request)

                } catch (e: InterruptedException) {
                    Log.d(TAG, "[스레드 B] 인터럽트로 종료")
                    break
                } catch (e: Exception) {
                    // 스트림이 끊겼을 때 주로 발생
                    Log.w(TAG, "[스레드 B] 전송 중 오류 (재시작 대기): ${e.message}")
                    break
                }
            }
            Log.d(TAG, "[스레드 B] 전송 스레드 종료")
        }
        sttTransmissionThread?.start()
    }

    /**
     * AudioRecord를 중지하고, STT 스트리밍을 종료합니다.
     */
    private fun stopStreamingAudio() {
        if (!isListening) return

        // 감시자 비활성화
        watchdogHandler.removeCallbacks(watchdogRunnable)

        isListening = false

        // [스레드 A] 중지 - 스레드 자체도 중단
        audioRecordingThread?.interrupt()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioRecordingThread = null

        // [스레드 B] 중지
        // .take()에서 대기 중일 수 있으므로 interrupt()로 깨워야 함
        sttTransmissionThread?.interrupt()
        requestObserver?.onCompleted() // STT 서버에 종료 알림
        requestObserver = null
        sttTransmissionThread = null

        // 큐 비우기
        audioBuffer.clear()

        // 텍스트 버퍼 비우기
        recognizedSpeechBuffer.setLength(0)

        // 종료 메시지
        binding.imageViewStop.visibility = View.GONE
        Toast.makeText(requireContext(), "발표가 종료되었습니다.", Toast.LENGTH_SHORT).show()
    }

    // 웹소켓으로 힌트 메시지(현재 발화 중인 문장 id와 내용, 유사도, 처리 시간)를 수신했을 때 실행될 콜백 함수
    private fun onHintReceived(response: SimilarityResponse) {
        Log.d(TAG, "서버에서 힌트 수신: ${response.mostSimilarId}")
        if (isAdded) {
            // 힌트를 UI에 표시
            binding.textViewResult.text = ""
            binding.textViewResult.text = "가장 유사한 문장:\n${response.mostSimilarText}"

            speakingSentence = response.mostSimilarText
            speakingId = response.mostSimilarId
            binding.textViewNowspeaking.text = "현재 발화 중인 문장: \n ${speakingSentence}"

        }
    }

    private var lastNextScriptId: Int? = null

    // nextScriptId에 대한 정보가 오면 워치로 이미지 보냄.
    private fun onProgressReceived(progress: ProgressResponse) {
        Log.d(TAG, "서버에서 진행률 계산 결과 수신: ${progress.nextScriptId}")

        val nextId = progress.nextScriptId ?: return

        val nextIdInt = nextId.toIntOrNull()
        if (nextIdInt == null) {
            Log.e(TAG, "nextScriptId 변환 실패: $nextId")
            return
        }

        if (lastNextScriptId == nextIdInt) {
            Log.d(TAG, "nextScriptId 동일 → 처리 스킵")
            return
        }
        lastNextScriptId = nextIdInt

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

            // 1. DB에서 ID로 이미지 정보 조회
            val entity = imageCacheDao.getByProjectAndSentence(PRESENTATION_ID.toInt(),nextIdInt)

            if (entity == null) {
                Log.e(TAG, "❌ DB에 해당 ID 이미지 없음: id=$nextIdInt")
                return@launch
            }

            val filePath = entity.filePath

            // 2. 파일 → Bitmap 복원
            val bitmap = BitmapFactory.decodeFile(filePath)

            if (bitmap == null) {
                val file = File(filePath)
                Log.e(
                    TAG,
                    "❌ Bitmap 디코딩 실패: $filePath " +
                            "(exists=${file.exists()}, length=${file.length()}, lastModified=${file.lastModified()})"
                )
                return@launch
            }

            // 3. Bitmap → ByteArray 변환
            val byteStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteStream)
            val imageBytes = byteStream.toByteArray()

            // 4. 메인 스레드에서 워치로 전송
            withContext(Dispatchers.Main) {
                sendImageToWatch(imageBytes)
                Log.d(TAG, "✅ 워치로 이미지 전송 완료: id=$nextIdInt")
            }
        }
    }

    fun sendImageToWatch(imageBytes: ByteArray) {
        val path = "/image_display"

        val nodeClient = Wearable.getNodeClient(requireContext())
        val messageClient = Wearable.getMessageClient(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 연결된 모든 워치 노드 가져오기
                val nodes = Tasks.await(nodeClient.connectedNodes)

                if (nodes.isEmpty()) {
                    Log.e(TAG, "❌ 연결된 워치 노드 없음")
                    return@launch
                }

                // 모든 워치로 이미지 전송
                for (node in nodes) {
                    Tasks.await(
                        messageClient.sendMessage(
                            node.id,
                            path,
                            imageBytes
                        )
                    )

                    Log.d(TAG, "✅ MessageClient 이미지 전송 성공 → nodeId=${node.id}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ MessageClient 이미지 전송 실패", e)
            }
        }
    }

    // url 기반으로 이미지 다운로드 받는 함수
    fun downloadBitmap(url: String): Bitmap {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 100000
        connection.readTimeout = 100000
        connection.doInput = true
        connection.connect()

        val inputStream = connection.inputStream
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        if (bitmap == null) {
            throw IllegalStateException("❌ Bitmap decode 실패: $url")
        }

        return bitmap
    }

    // 비트맵 저장.
    fun saveBitmap(context: Context, bitmap: Bitmap, projectId: Int, sentenceId: Int): String {
        val file = File(context.filesDir, "img_$projectId-$sentenceId.jpg")

        FileOutputStream(file).use { fos ->
            val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            if (!success) {
                throw IllegalStateException("❌ Bitmap compress 실패: id=$sentenceId")
            }
        }

        if (!file.exists() || file.length() == 0L) {
            throw IllegalStateException("❌ 파일 저장 실패: id=$sentenceId")
        }

        Log.d("ImageCache", "✅ 실제 파일 저장 성공: ${file.absolutePath} (${file.length()} bytes)")
        return file.absolutePath
    }

    fun clearAllCachedImages(context: Context) {
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("img_")) {
                file.delete()
            }
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()

        // 스트리밍 중지
        if (isListening) {
            stopStreamingAudio()
        }

        // 웹소켓 연결 해제
        if (::stompClient.isInitialized) {
            stompClient.disconnect()
        }

        // STT 클라이언트 해제
        speechClient?.shutdown()
        speechClient?.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)
        speechClient = null

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