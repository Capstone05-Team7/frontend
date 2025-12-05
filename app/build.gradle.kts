plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
    id("kotlin-kapt")
}

android {
    namespace = "com.example.capstone07"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.capstone07"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
    }

    packaging {
        // 중복되는 'META-INF/DEPENDENCIES' 파일 발견 시,
        // 빌드 시스템이 발견한 첫 번째 파일을 선택하고 나머지는 무시
        pickFirst("META-INF/DEPENDENCIES")
        exclude("META-INF/INDEX.LIST")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation("com.github.Dimezis:BlurView:version-2.0.3")

    // Retrofit
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)

    // OkHttp
    implementation(libs.okhttp)

    // STOMP 클라이언트
    implementation(libs.stomp.protocol.android)

    // STOMP 라이브러리가 의존하는 RxJava 및 RxAndroid 추가
    implementation(libs.rxjava2)
    implementation(libs.rxandroid)

    // Google Cloud STT
    implementation(libs.google.cloud.speech)
    implementation(libs.grpc.okhttp)

    // watch 관련
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    implementation("com.google.android.gms:play-services-tasks:18.1.0")

    // 코루틴 핵심 라이브러리
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3") // 최신 버전 확인
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2") // 최신 버전 확인
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2") // 필요할 수 있음

    //이미지용 glide
    implementation("com.github.bumptech.glide:glide:4.16.0")

    implementation("androidx.room:room-runtime:2.7.0")
    kapt("androidx.room:room-compiler:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
}