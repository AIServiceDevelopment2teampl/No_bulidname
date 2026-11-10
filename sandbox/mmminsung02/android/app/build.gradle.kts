plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    // 코드 패키지 이름. applicationId와 달라도 된다
    namespace = "com.team12.closet"
    compileSdk = 37

    defaultConfig {
        // Play Console에 이 값으로 앱을 만들었다 (2026-11-10). 바꿀 수 없다
        applicationId = "com.dupecheck.app"
        minSdk = 26
        targetSdk = 36
        // 시험용 APK 버전은 -PversionCode=2 -PversionName=0.1.1 처럼 넘긴다 (README 'AI에게 요청')
        versionCode = providers.gradleProperty("versionCode").map { it.toInt() }.getOrElse(1)
        versionName = providers.gradleProperty("versionName").getOrElse("0.1.0")

        // 서버 주소. 바꾸려면 gradle.properties나 -PserverBaseUrl=... 로 넘긴다
        val serverBaseUrl = providers.gradleProperty("serverBaseUrl").getOrElse("http://10.0.2.2:8000/")
        buildConfigField("String", "SERVER_BASE_URL", "\"$serverBaseUrl\"")

        // Google Cloud의 'Web' OAuth 클라이언트 ID (서버 GOOGLE_CLIENT_ID와 같은 값). 비어 있으면 로그인 버튼이 안내만 한다
        val googleWebClientId = providers.gradleProperty("googleWebClientId").getOrElse("")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    sourceSets {
        // 서버와 같은 쇼핑몰 기본 목록 파일을 앱에도 넣는다. 서버에 연결하지 못하면 이걸 쓴다
        getByName("main").assets.directories.add("../../src/server/shared")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric이 JDK 25에서 FileDescriptor 내부에 접근해야 한다
        unitTests.all {
            it.jvmArgs(
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--enable-native-access=ALL-UNNAMED",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
