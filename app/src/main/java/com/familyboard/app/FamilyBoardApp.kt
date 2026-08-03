package com.familyboard.app

import android.app.Application
import android.util.Log
import com.familyboard.app.data.AppContainer
import com.google.firebase.auth.FirebaseAuth

/**
 * 앱 진입점. Firebase 는 google-services.json 으로 자동 초기화된다(FirebaseInitProvider).
 * 보안 규칙(인증된 요청만 허용)에 맞춰 시작 시 익명 로그인한다 — 사용자 로그인 UI 없음.
 */
class FamilyBoardApp : Application() {
    lateinit var container: AppContainer
        private set

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
