package com.example.capstone07

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*
import com.example.capstone07.theme.Capstone07Theme
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import java.io.InputStream
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.text.style.TextAlign

// -----------------------------------------------------------------------------
// 1. ViewModel: 이미지 데이터를 관리합니다.
// -----------------------------------------------------------------------------
class ImageViewModel : ViewModel() {
    private val _receivedBitmap = mutableStateOf<Bitmap?>(null)
    val receivedBitmap: State<Bitmap?> = _receivedBitmap

    fun setBitmap(bitmap: Bitmap) {
        _receivedBitmap.value = bitmap
    }
}

// -----------------------------------------------------------------------------
// 2. WearableListenerService: 데이터 변경을 수신하고 이미지를 로드합니다.
// -----------------------------------------------------------------------------
const val IMAGE_PATH = "/image_display"
const val IMAGE_KEY = "target_image"
const val TAG = "WatchImageReceiver"

class WatchImageReceiverService : WearableListenerService() {

    // ViewModel 인스턴스를 서비스와 UI가 공유하기 위한 변수
    companion object {
        var staticViewModel: ImageViewModel? = null
        private var pendingBitmap: Bitmap? = null // 이미지를 임시 저장할 변수

        fun updateViewModel(vm: ImageViewModel) {
            staticViewModel = vm
            Log.d(TAG, "ViewModel 참조 설정됨.")

            // ViewModel이 연결될 때, 보류 중인 이미지가 있는지 확인
            pendingBitmap?.let {
                vm.setBitmap(it)
                pendingBitmap = null // 전달 후 캐시 지우기
                Log.d(TAG, "보류 중이던 이미지를 ViewModel에 전달했습니다.")
            }
        }
    }

    // 서비스에서 사용할 독립적인 Coroutine Scope
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                if (dataItem.uri.path == IMAGE_PATH) {
                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                    val asset = dataMap.getAsset(IMAGE_KEY)

                    if (asset != null) {
                        Log.d(TAG, "이미지 Asset 수신됨. 로드 시작.")
                        loadBitmapFromAsset(asset)
                    } else {
                        Log.w(TAG, "Asset이 null입니다.")
                    }
                }
            }
        }
    }

    private fun loadBitmapFromAsset(asset: Asset) {
        serviceScope.launch(Dispatchers.IO) {
            var inputStream: InputStream? = null
            try {
                val response = Wearable.getDataClient(this@WatchImageReceiverService)
                    .getFdForAsset(asset)
                    .await()

                inputStream = response.inputStream

                val bitmap = BitmapFactory.decodeStream(inputStream)

                withContext(Dispatchers.Main) {
                    val vm = staticViewModel
                    if (vm != null) {
                        // ViewModel이 연결되어 있으면 즉시 업데이트
                        vm.setBitmap(bitmap)
                        Log.d(TAG, "이미지 로드 및 ViewModel 업데이트 성공.")
                    } else {
                        // ViewModel이 아직 없으면 이미지를 보류
                        pendingBitmap = bitmap
                        Log.e(TAG, "ViewModel이 없어 이미지를 보류합니다.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Asset에서 이미지 로드 실패", e)
            } finally {
                inputStream?.close()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // 서비스 종료 시 코루틴 취소
    }
}


// -----------------------------------------------------------------------------
// 3. Composable 함수: UI를 정의하고 ViewModel을 사용합니다.
// -----------------------------------------------------------------------------
@Composable
fun WearApp(viewModel: ImageViewModel) {
    val bitmap by viewModel.receivedBitmap

    // ViewModel에 서비스가 접근할 수 있도록 컨텍스트가 구성될 때 설정
    DisposableEffect(Unit) {
        WatchImageReceiverService.updateViewModel(viewModel)
        onDispose {
            // 필요 시 정리 작업 수행
        }
    }

    Capstone07Theme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()

            if (bitmap != null) {
                // 이미지가 로드되었을 경우 표시
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "모바일에서 전송된 이미지",
                    modifier = Modifier.fillMaxSize().padding(24.dp)
                )
            } else {
                // 이미지가 없을 경우 안내 텍스트 표시
                Text(
                    text = "이미지 대기 중...\n경로: $IMAGE_PATH",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 4. Activity: 앱의 진입점
// -----------------------------------------------------------------------------
class WatchMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            // ViewModel 인스턴스를 얻습니다.
            val viewModel: ImageViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return ImageViewModel() as T
                    }
                }
            )
            WearApp(viewModel)
        }
    }
}