package com.familyboard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 권한 게이트 한 항목. granted=true 면 ✓, 아니면 [허용] 버튼. */
data class PermItem(
    val title: String,
    val desc: String,
    val granted: Boolean,
    val onGrant: () -> Unit,
)

private val Accent = Color(0xFFE8894A)
private val GrantedGreen = Color(0xFF2E9E5B)

/**
 * 필수 권한 미승인 시 앱 내용 대신 보이는 전체 화면.
 * - required: 모두 허용해야 앱이 열리는 필수 권한들
 * - optional: 선택 권한(위치 등) · 미사용 앱 권한 자동제거 끄기 안내 (게이트에 영향 없음)
 */
@Composable
fun PermissionGateScreen(
    required: List<PermItem>,
    optional: List<PermItem>,
) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFFFBF7F0)).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("가족 알림판을 쓰려면", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A2E22))
        Spacer(Modifier.height(6.dp))
        Text("아래 권한을 모두 허용해 주세요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Accent)
        Spacer(Modifier.height(10.dp))
        Text(
            "급한 연락·알림을 놓치지 않으려면 꼭 필요해요. 각 항목의 [허용]을 눌러 켜 주세요.",
            fontSize = 15.sp, color = Color(0xFF6B5B49), textAlign = TextAlign.Center, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(22.dp))

        required.forEach { PermCard(it, required = true) }

        if (optional.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("선택 (원하면)", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF8A7A66), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            Spacer(Modifier.height(8.dp))
            optional.forEach { PermCard(it, required = false) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermCard(item: PermItem, required: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A2E22))
            Spacer(Modifier.height(3.dp))
            Text(item.desc, fontSize = 13.sp, color = Color(0xFF7A6A58), lineHeight = 18.sp)
        }
        Spacer(Modifier.size(12.dp))
        if (item.granted) {
            Icon(Icons.Default.CheckCircle, "허용됨", tint = GrantedGreen, modifier = Modifier.size(34.dp))
        } else {
            Button(
                onClick = item.onGrant,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (required) Accent else Color(0xFFBFB2A0),
                    contentColor = Color.White,
                ),
            ) {
                Text(if (required) "허용" else "켜기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
