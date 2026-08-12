package com.familyboard.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.familyboard.app.notif.UpdateChecker
import com.familyboard.app.notif.UpdateInfo
import kotlinx.coroutines.launch

/**
 * 공용 인앱 업데이트 다이얼로그. 다운로드→설치까지 자체 처리(HomeScreen 인라인 버전과 동일 동작).
 * 여러 화면(홈·용돈 등)에서 재사용.
 */
@Composable
fun UpdatePrompt(info: UpdateInfo?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("새 버전 ${info?.versionName ?: ""} 있어요") },
        text = {
            Column {
                Text("업데이트가 있습니다. 지금 설치할까요?")
                if (!info?.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(info!!.notes)
                }
                if (downloading) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("다운로드 중…")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !downloading && info != null,
                onClick = {
                    val url = info?.url ?: return@TextButton
                    downloading = true
                    scope.launch {
                        val f = UpdateChecker.downloadApk(context, url, info.sha256)
                        downloading = false
                        if (f != null) { onDismiss(); UpdateChecker.installApk(context, f) }
                    }
                },
            ) { Text("지금 설치") }
        },
        dismissButton = { TextButton(enabled = !downloading, onClick = onDismiss) { Text("나중에") } },
    )
}

/**
 * 설치 버전(cur)과 최신 버전(latest)의 차이가 [minGap] 이상이면 true.
 * versionName "MAJOR.MINOR.PATCH" 에서 major/minor 가 다르면 큰 차이로 간주. 같으면 patch 차이로 판단.
 * (예: minGap=30 → "0.3 이상 차이" = 마지막 자리 30 이상)
 */
fun versionGapAtLeast(cur: String, latest: String, minGap: Int): Boolean {
    fun parts(v: String) = v.trim().split(".").let {
        Triple(it.getOrNull(0)?.toIntOrNull() ?: 0, it.getOrNull(1)?.toIntOrNull() ?: 0, it.getOrNull(2)?.toIntOrNull() ?: 0)
    }
    val (cMaj, cMin, cPatch) = parts(cur)
    val (lMaj, lMin, lPatch) = parts(latest)
    if (lMaj != cMaj || lMin != cMin) return true // major/minor 다르면 무조건 큰 차이
    return (lPatch - cPatch) >= minGap
}
