package com.familyboard.app

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.familyboard.app.data.AppContainer
import com.google.firebase.auth.FirebaseAuth

/**
 * 앱 진입점. Firebase 는 google-services.json 으로 자동 초기화된다(FirebaseInitProvider).
 * 보안 규칙(인증된 요청만 허용)에 맞춰 시작 시 익명 로그인한다 — 사용자 로그인 UI 없음.
 *
 * ImageLoaderFactory: Coil 싱글턴에 전용 캐시를 지정. 기본 디스크 캐시는 '여유공간 2%'라
 * 사진첩·재미진 곳 등 이미지가 많으면 오래된 썸네일이 밀려나 재다운로드됨 → 디스크 캐시를
 * 512MB로 고정해 한 번 본 썸네일은 거의 재다운로드 없이 유지. 폴더는 Coil 기본과 동일
 * (image_cache)라 기존 캐시를 그대로 재사용. 썸네일은 서버가 immutable 30d 로 주므로 재검증도 없음.
 */
class FamilyBoardApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512L * 1024 * 1024) // 512MB
                    .build()
            }
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // 익명 인증: Firestore 보안 규칙(request.auth != null)을 통과시키기 위함
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener { Log.i(TAG, "익명 로그인 성공: ${it.user?.uid}") }
                .addOnFailureListener { Log.w(TAG, "익명 로그인 실패 (콘솔에서 익명 인증 사용 설정 필요)", it) }
        }
    }

    companion object { private const val TAG = "FamilyBoardApp" }
}
