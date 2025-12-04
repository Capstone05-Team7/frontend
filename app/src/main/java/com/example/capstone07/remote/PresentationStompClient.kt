/*
package com.example.capstone07.remote

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import com.google.gson.Gson
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.StompHeader
import ua.naiksoftware.stomp.dto.StompMessage
import ua.naiksoftware.stomp.dto.StompCommand


// 서버 주소 및 포트가 실제와 다를 수 있으므로 확인 필요
private const val WS_ENDPOINT = "ws://10.0.2.2:8080/ws/presentation/websocket"
//private const val WS_ENDPOINT = "ws://3.34.163.79:8080/ws/presentation/websocket"
private const val TAG = "StompClient"

*/
/**
 * 💡 새로운 힌트 응답 데이터 클래스 정의
 * FastAPI의 SimilarityResponseDto와 일치하는 구조를 가집니다.
 *//*


class PresentationStompClient(
    private val presentationId: String,
    private val onHintReceived: (SimilarityResponse) -> Unit, // 힌트 수신 시 실행할 콜백 함수
    private val onProgressReceived: (Float) -> Unit // 진행률 수신 시 실행할 콜백 함수
) {

    private lateinit var stompClient: StompClient   // STOMP 클라이언트 객체
    private val compositeDisposable = CompositeDisposable() // RxJava 관련 리소스 관리 (연결, 구독 해제용)
    val isConnected: Boolean    // 연결 상태 확인
        get() = ::stompClient.isInitialized && stompClient.isConnected
    // 웹소켓 재연결 관련
    private var reconnectAttempts = 0 // 시도 횟수
    private val maxReconnectAttempts = 10 // 최대 시도 횟수
    private val reconnectHandler = Handler(Looper.getMainLooper()) // 재연결 지연을 위한 핸들러

    */
/**
     * 웹소켓 연결 및 초기화
     *//*

    fun connect() {
        // OkHttpClient를 기반으로 STOMP 클라이언트 객체 생성
        stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, WS_ENDPOINT)

        // 연결 리스너 설정
        compositeDisposable.add(stompClient.lifecycle()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { lifecycleEvent ->
                when (lifecycleEvent.type) {
                    ua.naiksoftware.stomp.dto.LifecycleEvent.Type.OPENED -> {
                        Log.d(TAG, "STOMP 연결 성공")
                        // 연결 성공 후 구독
                        subscribeForHints()
                        subscribeForProgress()
                    }
                    ua.naiksoftware.stomp.dto.LifecycleEvent.Type.ERROR -> {
                        Log.e(TAG, "STOMP 연결 오류", lifecycleEvent.exception)
                    }
                    ua.naiksoftware.stomp.dto.LifecycleEvent.Type.CLOSED -> {
                        Log.d(TAG, "STOMP 연결 종료")
                        tryReconnect() // 연결 종료 시 재연결 시도
                    }
                    else -> {}
                }
            })

        stompClient.connect() // 연결 시도
    }

    */
/**
     * 연결 오류 및 종료 시 재연결을 시도하는 로직
     *//*

    private fun tryReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.e(TAG, "최대 재연결 시도 횟수($maxReconnectAttempts) 초과. 재연결 중단.")
            // TODO: 사용자에게 알림 또는 수동 재연결 유도
            return
        }

        reconnectAttempts++

        // Exponential Backoff 딜레이 계산으로 서버 과부화 방지; 2^n * 1000 ms (최대 10초로 제한)
        val delay: Long = (Math.pow(2.0, reconnectAttempts.toDouble()) * 1000L)
            .coerceAtMost(10000.0).toLong()

        Log.w(TAG, "재연결 시도 #$reconnectAttempts. $delay ms 후 재시도...")

        reconnectHandler.postDelayed({
            // 재연결 시도 전에 기존 연결 및 리소스 정리 (Dispose)
            compositeDisposable.clear()

            // STOMP 연결을 다시 시작
            connect()
        }, delay)
    }

    */
/**
     * 힌트 수신 채널 구독 (/sub/current/{presentationId})
     *//*

    private fun subscribeForHints() {
        val destination = "/sub/current/$presentationId"

        compositeDisposable.add(stompClient.topic(destination)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ stompMessage ->
                Log.d(TAG, "힌트 메시지 수신: ${stompMessage.payload}")
                try {
                    // 💡 수정: JSON 페이로드를 HintResponse 객체로 변환하여 콜백 함수에 전달
                    val hintResponse = Gson().fromJson(stompMessage.payload, SimilarityResponse::class.java)
                    onHintReceived(hintResponse)
                } catch (e: Exception) {
                    Log.e(TAG, "힌트 메시지 JSON 파싱 오류: ${stompMessage.payload}", e)
                }
            }, { throwable ->
                Log.e(TAG, "구독 오류", throwable)
            }))
    }

    */
/**
     * 실시간 STT 텍스트 스트리밍 메서드 (/pub/stt)
     *//*

    fun sendSttText(text: String) {
        // 빈 문자열은 전송하지 않음
        if (text.isBlank()) {
            Log.d(TAG, "빈 STT 텍스트는 전송하지 않음")
            return
        }
        if (!isConnected) {
            Log.w(TAG, "웹소켓이 연결되지 않아 STT 메시지 전송 실패")
            return
        }

        // STOMP SEND 프레임 헤더 구성 (destination + custom header)
        val headers = mutableListOf(
            StompHeader(StompHeader.DESTINATION, "/pub/stt"),
            StompHeader("presentationId", presentationId)
        )
        // STT 텍스트는 JSON 형태로 본문(Payload)에 담아 전송
        // 수동 문자열 연결 대신 Gson을 사용해 안전하게 직렬화 (특수문자/따옴표 포함 시 오류 방지)
        val payload = Gson().toJson(mapOf("spokenText" to text))

        // Rx Completable 반환을 구독하여 전송 수행
        compositeDisposable.add(
            stompClient
                .send(StompMessage(StompCommand.SEND, headers, payload))
                .subscribe({
                    Log.d(TAG, "STT 메시지 전송 완료")
                }, { error ->
                    Log.e(TAG, "/pub/stt 전송 실패", error)
                })
        )
    }

    */
/**
     * 진행률 수신 채널 구독 (/sub/progress/{presentationId})
     *//*

    private fun subscribeForProgress() {
        val destination = "/sub/progress/$presentationId"

        compositeDisposable.add(stompClient.topic(destination)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ stompMessage ->
                Log.d(TAG, "진행률 수신: ${stompMessage.payload}")
                // 수신된 진행률 프래그먼트 콜백으로 전달
                try {
                    // ⚠️ 수정: 수신된 String payload를 Float으로 변환하여 콜백 함수를 호출합니다.
                    val progress = stompMessage.payload.toFloat()
                    onProgressReceived(progress)
                } catch (e: NumberFormatException) {
                    Log.e(TAG, "진행률 Payload를 Float으로 변환 실패: ${stompMessage.payload}", e)
                }
            }, { throwable ->
                Log.e(TAG, "구독 오류", throwable)
            }))
    }

    */
/**
     * 실시간 STT 텍스트 스트리밍 메서드 (/pub/stt/progress)
     *//*

    fun sendSttTextForProgress(targetScriptText: String, text: String) {
        // 빈 문자열은 전송하지 않음
        if (text.isBlank()) {
            Log.d(TAG, "빈 STT 텍스트는 전송하지 않음")
            return
        }
        if (!isConnected) {
            Log.w(TAG, "웹소켓이 연결되지 않아 STT 메시지 전송 실패")
            return
        }

        // STOMP SEND 프레임 헤더 구성 (destination + custom header)
        val headers = mutableListOf(
            StompHeader(StompHeader.DESTINATION, "/pub/stt/progress"),
            StompHeader("presentationId", presentationId)
        )

        // 💡 수정: targetScriptText와 spokenText(전달받은 text) 모두 포함하는 JSON 페이로드 생성
        val payloadData = mapOf(
            "targetScriptText" to targetScriptText,
            "spokenText" to text
        )

        // JSON 형태로 본문(Payload)에 담아 전송
        val payload = Gson().toJson(payloadData)

        // Rx Completable 반환을 구독하여 전송 수행
        compositeDisposable.add(
            stompClient
                .send(StompMessage(StompCommand.SEND, headers, payload))
                .subscribe({
                    Log.d(TAG, "STT 메시지 전송 완료")
                }, { error ->
                    Log.e(TAG, "/pub/stt/progress 전송 실패", error)
                })
        )
    }

    fun requestHint(){
        if (!isConnected) {
            Log.w(TAG, "웹소켓이 연결되지 않아 힌트 요청 전송 실패")
            return
        }

        // STOMP SEND 프레임 헤더 구성 (destination + custom header)
        val headers = mutableListOf(
            StompHeader(StompHeader.DESTINATION, "/pub/hint/request"),
            StompHeader("presentationId", presentationId)
        )
        // 힌트 요청은 별도 페이로드가 필요 없음 (빈 JSON 전송)
        val payload = "{}"

        // Rx Completable 반환을 구독하여 전송 수행
        compositeDisposable.add(
            stompClient
                .send(StompMessage(StompCommand.SEND, headers, payload))
                .subscribe({
                    Log.d(TAG, "힌트 요청 메시지 전송 완료")
                }, { error ->
                    Log.e(TAG, "/pub/hint/request 전송 실패", error)
                })
        )
    }

    */
/**
     * 연결 해제 및 리소스 정리
     *//*

    fun disconnect() {
        compositeDisposable.dispose()
        if (::stompClient.isInitialized && stompClient.isConnected) {
            stompClient.disconnect()
            Log.d(TAG, "STOMP 연결 해제")
        }
    }
}
*/
package com.example.capstone07.remote

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import com.google.gson.Gson
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.StompHeader
import ua.naiksoftware.stomp.dto.StompMessage
import ua.naiksoftware.stomp.dto.StompCommand


// 서버 주소 및 포트가 실제와 다를 수 있으므로 확인 필요
//private const val WS_ENDPOINT = "ws://10.0.2.2:8080/ws/presentation/websocket"
private const val WS_ENDPOINT = "ws://3.34.163.79:8080/ws/presentation/websocket"
private const val TAG = "StompClient"

/**
 * 💡 새로운 힌트 응답 데이터 클래스 정의
 * FastAPI의 SimilarityResponseDto와 일치하는 구조를 가집니다.
 */

class PresentationStompClient(
    private val presentationId: String,
    private val onHintReceived: (SimilarityResponse) -> Unit, // 힌트 수신 시 실행할 콜백 함수
    private val onProgressReceived: (ProgressResponse) -> Unit // 진행률 수신 시 실행할 콜백 함수
) {

    private lateinit var stompClient: StompClient   // STOMP 클라이언트 객체
    private val compositeDisposable = CompositeDisposable() // RxJava 관련 리소스 관리 (연결, 구독 해제용)
    val isConnected: Boolean    // 연결 상태 확인
        get() = ::stompClient.isInitialized && stompClient.isConnected
    // 웹소켓 재연결 관련
    private var reconnectAttempts = 0 // 시도 횟수
    private val maxReconnectAttempts = 10 // 최대 시도 횟수
    private val reconnectHandler = Handler(Looper.getMainLooper()) // 재연결 지연을 위한 핸들러

    /**
     * 웹소켓 연결 및 초기화
     */
    fun connect() {
        // OkHttpClient를 기반으로 STOMP 클라이언트 객체 생성
        stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, WS_ENDPOINT)

        // 연결 리스너 설정
        compositeDisposable.add(stompClient.lifecycle()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { lifecycleEvent ->
                when (lifecycleEvent.type) {
                    ua.naiksoftware.stomp.dto.LifecycleEvent.Type.OPENED -> {
                        Log.d(TAG, "STOMP 연결 성공")
                        // 연결 성공 후 구독
                        subscribeForHints()
                        subscribeForProgress()
                    }
                    ua.naiksoftware.stomp.dto.LifecycleEvent.Type.ERROR -> {
                        Log.e(TAG, "STOMP 연결 오류", lifecycleEvent.exception)
                    }
                    ua.naiksoftware.stomp.dto.LifecycleEvent.Type.CLOSED -> {
                        Log.d(TAG, "STOMP 연결 종료")
                        tryReconnect() // 연결 종료 시 재연결 시도
                    }
                    else -> {}
                }
            })

        stompClient.connect() // 연결 시도
    }

    /**
     * 연결 오류 및 종료 시 재연결을 시도하는 로직
     */
    private fun tryReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.e(TAG, "최대 재연결 시도 횟수($maxReconnectAttempts) 초과. 재연결 중단.")
            // TODO: 사용자에게 알림 또는 수동 재연결 유도
            return
        }

        reconnectAttempts++

        // Exponential Backoff 딜레이 계산으로 서버 과부화 방지; 2^n * 1000 ms (최대 10초로 제한)
        val delay: Long = (Math.pow(2.0, reconnectAttempts.toDouble()) * 1000L)
            .coerceAtMost(10000.0).toLong()

        Log.w(TAG, "재연결 시도 #$reconnectAttempts. $delay ms 후 재시도...")

        reconnectHandler.postDelayed({
            // 재연결 시도 전에 기존 연결 및 리소스 정리 (Dispose)
            compositeDisposable.clear()

            // STOMP 연결을 다시 시작
            connect()
        }, delay)
    }

    /**
     * 힌트 수신 채널 구독 (/sub/current/{presentationId})
     */
    private fun subscribeForHints() {
        val destination = "/sub/current/$presentationId"

        compositeDisposable.add(stompClient.topic(destination)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ stompMessage ->
                Log.d(TAG, "힌트 메시지 수신: ${stompMessage.payload}")
                try {
                    // 💡 수정: JSON 페이로드를 HintResponse 객체로 변환하여 콜백 함수에 전달
                    val hintResponse = Gson().fromJson(stompMessage.payload, SimilarityResponse::class.java)
                    onHintReceived(hintResponse)
                } catch (e: Exception) {
                    Log.e(TAG, "힌트 메시지 JSON 파싱 오류: ${stompMessage.payload}", e)
                }
            }, { throwable ->
                Log.e(TAG, "구독 오류", throwable)
            }))
    }

    /**
     * 실시간 STT 텍스트 스트리밍 메서드 (/pub/stt)
     */
    fun sendSttText(text: String) {
        // 빈 문자열은 전송하지 않음
        if (text.isBlank()) {
            Log.d(TAG, "빈 STT 텍스트는 전송하지 않음")
            return
        }
        if (!isConnected) {
            Log.w(TAG, "웹소켓이 연결되지 않아 STT 메시지 전송 실패")
            return
        }

        // STOMP SEND 프레임 헤더 구성 (destination + custom header)
        val headers = mutableListOf(
            StompHeader(StompHeader.DESTINATION, "/pub/stt"),
            StompHeader("presentationId", presentationId)
        )
        // STT 텍스트는 JSON 형태로 본문(Payload)에 담아 전송
        // 수동 문자열 연결 대신 Gson을 사용해 안전하게 직렬화 (특수문자/따옴표 포함 시 오류 방지)
        val payload = Gson().toJson(mapOf("spokenText" to text))

        // Rx Completable 반환을 구독하여 전송 수행
        compositeDisposable.add(
            stompClient
                .send(StompMessage(StompCommand.SEND, headers, payload))
                .subscribe({
                    Log.d(TAG, "STT 메시지 전송 완료")
                }, { error ->
                    Log.e(TAG, "/pub/stt 전송 실패", error)
                })
        )
    }

    /**
     * 진행률 수신 채널 구독 (/sub/progress/{presentationId})
     */
    private fun subscribeForProgress() {
        val destination = "/sub/progress/$presentationId"

        compositeDisposable.add(stompClient.topic(destination)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ stompMessage ->
                Log.d(TAG, "진행률 응답 수신: ${stompMessage.payload}")

                // 수신된 JSON payload를 ProgressResponse 객체로 변환
                try {
                    // 1. Gson을 사용하여 JSON 문자열을 ProgressResponse 객체로 변환
                    val response = Gson().fromJson(stompMessage.payload, ProgressResponse::class.java)

                    // 2. ⚠️ (Float) -> Unit 이 아닌 새 콜백 함수로 응답 객체 전체를 전달
                    //    (예시: onProgressResponseReceived(response))
                    //    이 콜백은 AnalysisFragment에서 구현해야 합니다.

                    // (가정) 클라이언트가 onProgressResponseReceived 콜백을 가지고 있다고 전제
                    onProgressReceived(response)

                } catch (e: Exception) { // JsonSyntaxException 등
                    Log.e(TAG, "진행률 Payload 파싱 실패: ${stompMessage.payload}", e)
                }
            }, { throwable ->
                Log.e(TAG, "구독 오류", throwable)
            }))
    }

    /**
     * 실시간 STT 텍스트 스트리밍 메서드 (/pub/stt/progress)
     */
    fun sendSttTextForProgress(targetScriptId:String, targetScriptText: String, text: String) {
        // 빈 문자열은 전송하지 않음
        if (text.isBlank()) {
            Log.d(TAG, "빈 STT 텍스트는 전송하지 않음")
            return
        }
        if (!isConnected) {
            Log.w(TAG, "웹소켓이 연결되지 않아 STT 메시지 전송 실패")
            return
        }

        // STOMP SEND 프레임 헤더 구성 (destination + custom header)
        val headers = mutableListOf(
            StompHeader(StompHeader.DESTINATION, "/pub/stt/progress"),
            StompHeader("presentationId", presentationId)
        )

        // 💡 수정: targetScriptText와 spokenText(전달받은 text) 모두 포함하는 JSON 페이로드 생성
        val payloadData = mapOf(
            "target_script_id" to targetScriptId,
            "target_script_text" to targetScriptText,
            "query_text" to text
        )

        // JSON 형태로 본문(Payload)에 담아 전송
        val payload = Gson().toJson(payloadData)

        Log.d(TAG, payload)

        // Rx Completable 반환을 구독하여 전송 수행
        compositeDisposable.add(
            stompClient
                .send(StompMessage(StompCommand.SEND, headers, payload))
                .subscribe({
                    Log.d(TAG, "STT 메시지 전송 완료")
                }, { error ->
                    Log.e(TAG, "/pub/stt/progress 전송 실패", error)
                })
        )
    }

    fun requestHint(){
        if (!isConnected) {
            Log.w(TAG, "웹소켓이 연결되지 않아 힌트 요청 전송 실패")
            return
        }

        // STOMP SEND 프레임 헤더 구성 (destination + custom header)
        val headers = mutableListOf(
            StompHeader(StompHeader.DESTINATION, "/pub/hint/request"),
            StompHeader("presentationId", presentationId)
        )
        // 힌트 요청은 별도 페이로드가 필요 없음 (빈 JSON 전송)
        val payload = "{}"

        // Rx Completable 반환을 구독하여 전송 수행
        compositeDisposable.add(
            stompClient
                .send(StompMessage(StompCommand.SEND, headers, payload))
                .subscribe({
                    Log.d(TAG, "힌트 요청 메시지 전송 완료")
                }, { error ->
                    Log.e(TAG, "/pub/hint/request 전송 실패", error)
                })
        )
    }

    /**
     * 연결 해제 및 리소스 정리
     */
    fun disconnect() {
        compositeDisposable.dispose()
        if (::stompClient.isInitialized && stompClient.isConnected) {
            stompClient.disconnect()
            Log.d(TAG, "STOMP 연결 해제")
        }
    }
}
