package com.familyboard.app.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.familyboard.app.notif.PhotoUploader
import kotlinx.coroutines.launch
import java.io.File

/**
 * 사진 첨부 UI (갤러리/카메라 → 압축·업로드 → 썸네일·삭제). 최대 5장.
 * 일정/버킷 등에서 재사용. 업로드된 URL 목록을 onChange 로 알린다.
 */
@Composable
fun PhotoPickerRow(
    photoUrls: List<String>,
    onChange: (List<String>) -> Unit,
    max: Int = 5,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun uploadFrom(uri: Uri) {
        if (photoUrls.size >= max) return
        uploading = true
        scope.launch {
            val url = PhotoUploader.compressAndUpload(context, uri)
            uploading = false
            if (url != null) onChange(photoUrls + url)
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) uploadFrom(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) uploadFrom(u)
    }
    fun launchCamera() {
        val f = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        camera.launch(cameraUri!!)
    }

    Column(Modifier.fillMaxWidth()) {
        Text("사진 (${photoUrls.size}/$max)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            photoUrls.forEach { url ->
                Box(Modifier.size(76.dp)) {
                    AsyncImage(
                        model = url, contentDescription = null,
                        modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape)
                            .background(Color(0x99000000)).clickable { onChange(photoUrls - url) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Close, "삭제", tint = Color.White, modifier = Modifier.size(15.dp)) }
                }
            }
            if (uploading) {
                Box(Modifier.size(76.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            }
            if (photoUrls.size < max) {
                AddBtn(Icons.Default.PhotoLibrary, "갤러리") {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                AddBtn(Icons.Default.PhotoCamera, "카메라") { launchCamera() }
            }
        }
    }
}

@Composable
private fun AddBtn(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1F3F5)).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color(0xFF6B6B6B))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF6B6B6B))
    }
}
