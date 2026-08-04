package com.familyboard.app.ui.emergency

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyboard.app.data.Family
import com.familyboard.app.ui.AppViewModel

private val EmergencyRed = Color(0xFFE8894A) // 따뜻한 톤 강조색
private const val DEFAULT_MSG = "급한데 연락이 안되어 보내니, 이 알림을 보면 바로 전화 줘!"
private const val LOC_SUFFIX = "위치도 공유해주면 더 안심이 되겠어."

/** 최종 발송 문구: 비어 있으면 기본 문구, 위치요청이면 하단에 위치 문구 추가 */
private fun buildBody(input: String, wantLoc: Boolean): String {
    val base = input.trim().ifBlank { DEFAULT_MSG }
    return if (wantLoc) "$base\n$LOC_SUFFIX" else base
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmergencySendScreen(
    vm: AppViewModel,
    currentMemberId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var targets by remember { mutableStateOf(emptyList<String>()) }
    var message by remember { mutableStateOf("") }
    var wantLoc by remember { mutableStateOf(false) }
    val candidates = remember(currentMemberId) { Family.members.filter { it.id != currentMemberId } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("빠른 연락 요청") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp).fillMaxWidth(),
        ) {
            Text(
                "전화·문자를 받지 않을 때, 받는 사람 화면을 전체화면으로 덮어 꼭 확인하게 합니다.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
            )
            Spacer(Modifier.height(20.dp))

            Text("받는 사람", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { m ->
                    val on = targets.contains(m.id)
                    Row(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (on) EmergencyRed else Color(0xFFF1F3F5))
                            .clickable {
                                targets = if (on) targets - m.id else targets + m.id
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (on) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(15.dp)); Spacer(Modifier.size(4.dp)) }
                        Text(m.name, color = if (on) Color.White else Color(0xFF444444),
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("내용", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = message, onValueChange = { if (it.length <= 200) message = it },
                placeholder = { Text(DEFAULT_MSG) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                minLines = 3,
                supportingText = { Text("비워두면 위 예시 문구가 그대로 전송돼요 · ${message.length}/200") },
            )

            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { wantLoc = !wantLoc }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = wantLoc, onCheckedChange = { wantLoc = it })
                Icon(Icons.Default.LocationOn, null, tint = EmergencyRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Column {
                    Text("위치 공유 요청", fontWeight = FontWeight.Medium)
                    Text("받는 사람 화면에 '내 위치 공유' 버튼이 생겨요",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    vm.sendEmergency(targets, buildBody(message, wantLoc), wantLoc)
                    Toast.makeText(context, "빠른 연락 요청을 보냈어요", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                enabled = targets.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
            ) {
                Icon(Icons.Default.Send, null); Spacer(Modifier.size(8.dp))
                Text("빠른 연락 요청 보내기", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    // 혼자 테스트: FCM 없이 내 폰에 전체화면을 바로 띄워본다(보낸이=배우자로 가정)
                    val spouse = when (currentMemberId) {
                        "seonil" -> "eunseon"; "eunseon" -> "seonil"; else -> currentMemberId ?: "seonil"
                    }
                    context.startActivity(
                        Intent(context, EmergencyActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(EmergencyActivity.EXTRA_SENDER, spouse)
                            putExtra(EmergencyActivity.EXTRA_MESSAGE, buildBody(message, wantLoc))
                            putExtra(EmergencyActivity.EXTRA_WANT_LOC, wantLoc)
                            putExtra(EmergencyActivity.EXTRA_TEST, true)
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("테스트: 내 폰에 전체화면 미리보기", fontWeight = FontWeight.Medium)
            }
        }
    }
}
