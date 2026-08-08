package com.familyboard.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familyboard.app.FamilyBoardApp
import com.familyboard.app.data.model.BoardType
import com.familyboard.app.data.model.CalendarEvent
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.data.Family
import com.familyboard.app.notif.NotifyApi
import com.familyboard.app.notif.ReminderScheduler
import com.familyboard.app.notif.UpdateChecker
import com.familyboard.app.notif.UpdateInfo
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** 온보딩 게이팅용 사용자 상태 */
sealed interface UserState {
    data object Loading : UserState
    data object NeedSelect : UserState
    data class Selected(val id: String) : UserState
}

/** 공유로 받은 장소(이름/링크/파싱요약/주소). 저장 위치(맛집/가볼 곳) 선택 대기용. */
data class SharedPlace(
    val name: String,
    val link: String,
    val description: String = "",
    val address: String = "",
    val category: String = "",  // 네이버 종목(맛집/가볼곳 필터용)
    val image: String = "",
    val images: List<String> = emptyList(), // 여러 장 묶음(재미진 곳)
    val naverScore: Double = 0.0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val loading: Boolean = false,
    val isFun: Boolean = false, // true=재미진 곳(유튜브/웹/이미지/영상), false=장소(맛집/가볼곳)
)

/** 다른 앱에서 '공유'로 받은 파일(문서함 저장 대기용). */
data class PendingDoc(
    val uri: android.net.Uri,
    val name: String,
    val uploading: Boolean = false,
)

/**
 * 앱 전역 상태/동작 허브. 화면들은 이 VM 을 공유한다(activity 스코프).
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as FamilyBoardApp).container
    private val board = container.boardRepository
    private val holidayRepo = container.holidayRepository

    val currentMemberId: StateFlow<String?> =
        container.currentUserStore.currentMemberId
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val userState: StateFlow<UserState> =
        container.currentUserStore.currentMemberId
            .map { if (it.isNullOrBlank()) UserState.NeedSelect else UserState.Selected(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, UserState.Loading)

    val events: StateFlow<List<CalendarEvent>> =
        board.events().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shoppingItems: StateFlow<List<ListItem>> =
        board.items(BoardType.SHOPPING.key)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todoItems: StateFlow<List<ListItem>> =
        board.items(BoardType.TODO.key)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 가족 공지사항 (부모 전용)
    val noticeItems: StateFlow<List<ListItem>> =
        board.items(BoardType.NOTICE.key)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // D-Day (카운트다운, 가족 모두)
    val ddayItems: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.DDayBoard.BOARD)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 사용자 커스텀 체크리스트 정의(board="customlists"). 본인만 화면에 표시.
    val customLists: StateFlow<List<ListItem>> =
        board.items("customlists")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 임의 보드 키의 항목 스트림(커스텀 리스트 포함). */
    fun boardItems(boardKey: String): kotlinx.coroutines.flow.Flow<List<ListItem>> = board.items(boardKey)

    // 장소 북마크 보드(맛집/가볼 곳). 초기값 null=아직 로딩 전(스피너), 빈 리스트=진짜 없음.
    val restaurantItems: StateFlow<List<ListItem>?> =
        board.items(com.familyboard.app.data.model.PlaceBoards.RESTAURANT)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val visitItems: StateFlow<List<ListItem>?> =
        board.items(com.familyboard.app.data.model.PlaceBoards.VISIT)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun placeItems(boardKey: String): StateFlow<List<ListItem>?> =
        if (boardKey == com.familyboard.app.data.model.PlaceBoards.RESTAURANT) restaurantItems else visitItems

    // 가족 공유 문서함(pdf·이미지·docx·엑셀 등). 항목 수가 많지 않아 실시간 전체 조회 + 클라 정렬/권한필터.
    // null=로딩 전(스피너), 빈 리스트=없음.
    val docItems: StateFlow<List<ListItem>?> =
        board.items(com.familyboard.app.data.model.DocBoard.BOARD)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 재미진 곳(유튜브/웹/이미지 게시판). 페이지 방식(이전/다음 + 이어보기).
    // 한 페이지 FUN_PAGE개. 페이지 상태/로딩은 화면(FunListScreen)이 관리하고,
    // VM 은 1회성 조회(fetchFunPage) · 전체 개수 · 마지막 본 페이지 저장만 담당.
    // BOARD=공용, PRIVATE=내것(쿼리에서 createdBy 로 본인 것만).
    private val userStore = container.currentUserStore

    /** 항목 추가/삭제 시 개수 다시 집계하기 위한 트리거. */
    private val funRefresh = MutableStateFlow(0)
    private fun bumpFunRefresh() { funRefresh.value++ }

    // 리스트 화면 카드에 표시할 "전체" 개수(집계 count, 페이지와 무관).
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val funCount: StateFlow<Int> =
        funRefresh.flatMapLatest {
            kotlinx.coroutines.flow.flow {
                emit(runCatching { board.countByBoard(com.familyboard.app.data.model.FunBoard.BOARD) }.getOrDefault(0))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myFunCount: StateFlow<Int> =
        combine(funRefresh, currentMemberId) { _, me -> me }
            .flatMapLatest { me ->
                kotlinx.coroutines.flow.flow {
                    emit(if (me.isNullOrBlank()) 0
                    else runCatching { board.countByBoard(com.familyboard.app.data.model.FunBoard.PRIVATE, me) }.getOrDefault(0))
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun funCountFor(boardKey: String): StateFlow<Int> =
        if (boardKey == com.familyboard.app.data.model.FunBoard.PRIVATE) myFunCount else funCount

    /** 페이지 방식 1회성 조회. [afterCreatedAt] 이후부터 [limit]개(최신순/등록순). 내것은 본인 것만. */
    suspend fun fetchFunPage(boardKey: String, ascending: Boolean, afterCreatedAt: Long?, limit: Int): List<ListItem> {
        val isPrivate = boardKey == com.familyboard.app.data.model.FunBoard.PRIVATE
        val createdBy = if (isPrivate) currentMemberId.value else null
        if (isPrivate && createdBy.isNullOrBlank()) return emptyList()
        return runCatching { board.pageByBoard(boardKey, limit, createdBy, ascending, afterCreatedAt) }.getOrDefault(emptyList())
    }

    /** 길찾기 기본 앱("항상" 선택). 빈 값=매번 선택창. */
    val navDefaultApp: StateFlow<String> =
        userStore.navDefaultApp.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    fun setNavDefaultApp(key: String) = viewModelScope.launch { userStore.setNavDefaultApp(key) }

    /** 마지막 본 페이지(1-based, 0=없음) 흐름/저장 — 방향별(최신순/등록순) 이어보기용. */
    fun lastFunPage(boardKey: String, ascending: Boolean): kotlinx.coroutines.flow.Flow<Int> =
        userStore.lastFunPage(boardKey, ascending)
    fun saveLastFunPage(boardKey: String, ascending: Boolean, page: Int) =
        viewModelScope.launch { userStore.setLastFunPage(boardKey, ascending, page) }

    /** 네이버 플레이스 등에서 공유받은 장소(저장 위치 선택 대기). */
    val pendingShare: MutableStateFlow<SharedPlace?> = MutableStateFlow(null)

    /** 다른 앱에서 '공유'로 받은 파일(문서함 저장 대기). */
    val pendingDoc: MutableStateFlow<PendingDoc?> = MutableStateFlow(null)

    /** 공유받은 파일(pdf·docx·엑셀 등) → 문서함 저장 확인 대기. */
    fun handleSharedDocument(uri: android.net.Uri) {
        val (name, _) = queryFileNameSize(getApplication<Application>().contentResolver, uri)
        pendingDoc.value = PendingDoc(uri, name.ifBlank { "문서" })
    }

    /** 대기 중인 공유 파일을 문서함에 업로드. onDone(성공, 실패사유). */
    fun savePendingDoc(onDone: (Boolean, String?) -> Unit) {
        val p = pendingDoc.value ?: return
        if (p.uploading) return
        pendingDoc.value = p.copy(uploading = true)
        addDocFromUri(p.uri) { ok, err -> pendingDoc.value = null; onDone(ok, err) }
    }

    fun clearPendingDoc() { pendingDoc.value = null }

    /** 공유 텍스트 처리. 네이버 플레이스 링크 → 장소(맛집/가볼곳), 그 외 링크 → 재미진 곳. 서버로 정보 파싱. */
    fun handleSharedText(raw: String?, subject: String?) {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return
        val url = Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')', ']') ?: ""
        var name = text.replace(url, " ").split('\n').map { it.trim() }
            .firstOrNull { it.isNotBlank() && it != "[네이버지도]" }.orEmpty()
        if (name.isBlank()) name = subject?.trim().orEmpty()
        val isNaverPlace = url.contains("naver.me") || url.contains("map.naver.com") || url.contains("place.naver.com")

        if (isNaverPlace) {
            pendingShare.value = SharedPlace(name.ifBlank { "장소 불러오는 중…" }, url, loading = true, isFun = false)
            viewModelScope.launch {
                val info = com.familyboard.app.notif.NotifyApi.parsePlace(url)
                val cur = pendingShare.value ?: return@launch
                pendingShare.value = if (info != null && info.name.isNotBlank())
                    cur.copy(name = info.name, description = buildPlaceDesc(info), address = info.address,
                        category = info.category, image = info.image, naverScore = info.score ?: 0.0,
                        lat = info.lat ?: 0.0, lng = info.lng ?: 0.0, loading = false)
                else cur.copy(name = if (name.isBlank()) "새 장소" else name, loading = false)
            }
        } else if (url.isNotBlank()) {
            // 유튜브/웹 링크 → 재미진 곳
            pendingShare.value = SharedPlace(name.ifBlank { "불러오는 중…" }, url, loading = true, isFun = true)
            viewModelScope.launch {
                val info = com.familyboard.app.notif.NotifyApi.parseLink(url)
                val cur = pendingShare.value ?: return@launch
                pendingShare.value = if (info != null && (info.title.isNotBlank() || info.image.isNotBlank()))
                    cur.copy(name = info.title.ifBlank { name.ifBlank { "링크" } }, image = info.image, loading = false, isFun = true)
                else cur.copy(name = name.ifBlank { "링크" }, loading = false, isFun = true)
            }
        }
    }
    private fun buildPlaceDesc(i: com.familyboard.app.notif.PlaceInfo): String {
        val lines = mutableListOf<String>()
        val head = buildString {
            if (i.category.isNotBlank()) append(i.category)
            if (i.score != null) {
                if (isNotEmpty()) append(" · ")
                append("★${i.score}")
                if (i.reviews != null) append(" (리뷰 ${i.reviews})")
            }
        }
        if (head.isNotBlank()) lines.add(head)
        if (i.hours.isNotBlank()) lines.add("영업 ${i.hours}")
        return lines.joinToString("\n")
    }
    fun savePlace(boardKey: String) {
        val s = pendingShare.value ?: return
        if (s.loading) return
        val nm = s.name.trim().let { if (it.isBlank() || it == "장소 불러오는 중…") "새 장소" else it }
        addPlace(boardKey, nm, s.link, s.description, s.address, s.image, s.naverScore, s.lat, s.lng, s.category)
        pendingShare.value = null
    }
    fun clearPendingShare() { pendingShare.value = null }

    fun saveFun(boardKey: String) {
        val s = pendingShare.value ?: return
        if (s.loading) return
        val photos = if (s.images.isNotEmpty()) s.images else if (s.image.isNotBlank()) listOf(s.image) else emptyList()
        val isImage = s.link.isBlank() && photos.isNotEmpty()
        val nm = s.name.trim().let { if (it.isBlank() || it.endsWith("중…")) (if (isImage) "이미지" else "링크") else it }
        addFun(boardKey, nm, s.link, photos)
        pendingShare.value = null
    }
    fun addFun(boardKey: String, title: String, link: String, photoUrls: List<String> = emptyList()) = viewModelScope.launch {
        runCatching {
            board.upsertItem(ListItem(text = title.trim(), link = link.trim(), photoUrls = photoUrls,
                board = boardKey, createdBy = currentMemberId.value.orEmpty(),
                createdAt = System.currentTimeMillis()))
        }
        bumpFunRefresh()
    }
    /** 공유받은 이미지 여러 장 → 한 항목으로(고화질 업로드). */
    fun handleSharedImages(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        if (uris.size == 1) { handleSharedImage(uris[0]); return }
        pendingShare.value = SharedPlace(name = "이미지 올리는 중…", link = "", loading = true, isFun = true)
        viewModelScope.launch {
            val urls = uris.mapNotNull { com.familyboard.app.notif.PhotoUploader.uploadImageHiQ(getApplication(), it) }
            val cur = pendingShare.value ?: return@launch
            pendingShare.value = if (urls.isNotEmpty()) cur.copy(name = "이미지 ${urls.size}장", images = urls, link = "", loading = false)
            else null
        }
    }
    /** 공유받은 영상(mp4 등) → 업로드 + 썸네일. 확장자는 실제 mime에서 판별(기본 mp4). */
    fun handleSharedVideo(uri: android.net.Uri, hintExt: String) {
        pendingShare.value = SharedPlace(name = "영상 올리는 중…", link = "", loading = true, isFun = true)
        viewModelScope.launch {
            val ext = resolveVideoExt(uri, hintExt)
            val videoUrl = com.familyboard.app.notif.PhotoUploader.uploadRaw(getApplication(), uri, ext)
            val cur = pendingShare.value ?: return@launch
            if (videoUrl == null) { pendingShare.value = null; return@launch }
            val thumb = com.familyboard.app.notif.PhotoUploader.uploadVideoThumb(getApplication(), uri).orEmpty()
            pendingShare.value = cur.copy(name = "공유 영상", link = videoUrl, image = thumb, loading = false)
        }
    }
    private fun resolveVideoExt(uri: android.net.Uri, hintExt: String): String {
        val valid = Regex("[a-z0-9]{1,5}")
        val cr = getApplication<android.app.Application>().contentResolver
        val fromMime = cr.getType(uri)?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return listOf(fromMime, hintExt).mapNotNull { it?.lowercase() }
            .firstOrNull { it.matches(valid) } ?: "mp4"
    }
    /** 재미진 곳 항목을 다른 재미진 곳 보드(공용/내것)로 복사. */
    fun copyFunTo(item: ListItem, targetBoard: String) = viewModelScope.launch {
        runCatching {
            board.upsertItem(ListItem(
                text = item.text, link = item.link, photoUrls = item.photoUrls,
                board = targetBoard, createdBy = currentMemberId.value.orEmpty(),
                createdAt = System.currentTimeMillis()))
        }
        bumpFunRefresh()
    }
    /** 재미진 곳 항목을 현재 사용자가 봤다고 표시(중복 방지, arrayUnion). */
    fun markFunViewed(item: ListItem) {
        val me = currentMemberId.value.orEmpty()
        if (me.isBlank() || item.viewedBy.contains(me)) return
        viewModelScope.launch { runCatching { board.markViewed(item.id, me) } }
    }

    /** 홈 공지 강조를 현재 사용자가 '확인'했다고 표시(viewedBy arrayUnion). markFunViewed 와 동일 로직. */
    fun markNoticeSeen(item: ListItem) = markFunViewed(item)
    fun updateFun(item: ListItem, title: String, link: String, image: String = item.photoUrls.firstOrNull().orEmpty()) = viewModelScope.launch {
        // 여러 장 묶음은 대표 이미지가 그대로면 전체 보존, 바뀌면 그 이미지로 대체
        val photos = when {
            image.isBlank() -> item.photoUrls
            item.photoUrls.size > 1 && item.photoUrls.firstOrNull() == image -> item.photoUrls
            else -> listOf(image)
        }
        runCatching {
            board.updateFields(item.id, mapOf("text" to title.trim(), "link" to link.trim(), "photoUrls" to photos))
        }
    }
    /** 공유받은 이미지 → 서버 업로드(고화질) 후 저장 대기(재미진 곳). */
    fun handleSharedImage(uri: android.net.Uri) {
        pendingShare.value = SharedPlace(name = "이미지 올리는 중…", link = "", loading = true, isFun = true)
        viewModelScope.launch {
            val url = com.familyboard.app.notif.PhotoUploader.uploadImageHiQ(getApplication(), uri)
            val cur = pendingShare.value ?: return@launch
            pendingShare.value = if (url != null) cur.copy(name = "공유 이미지", image = url, link = "", loading = false, isFun = true)
            else null // 업로드 실패 시 대기 취소
        }
    }

    // ---- 가족 공유 문서함 ----
    private val MAX_DOC_BYTES = 58_000_000 // 서버 /uploadfile 한도(60MB) 이내 여유

    /** 파일 URI 업로드 후 문서함 항목 생성(제목=파일명, 열람=모두). onDone(성공, 실패사유). */
    fun addDocFromUri(uri: android.net.Uri, onDone: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val cr = getApplication<Application>().contentResolver
        val (rawName, size) = queryFileNameSize(cr, uri)
        val mime = cr.getType(uri).orEmpty()
        val name = rawName.ifBlank { "문서" }
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { cr.openInputStream(uri)!!.use { it.readBytes() } }.getOrNull()
        }
        if (bytes == null) { onDone(false, "파일을 읽을 수 없어요"); return@launch }
        if (bytes.size > MAX_DOC_BYTES) { onDone(false, "58MB 이하 파일만 올릴 수 있어요"); return@launch }
        val url = com.familyboard.app.notif.NotifyApi.uploadFile(bytes, docExt(name, mime))
        if (url.isNullOrBlank()) { onDone(false, "업로드 실패(네트워크 확인)"); return@launch }
        val ok = runCatching {
            board.upsertItem(ListItem(
                text = titleFromFileName(name), board = com.familyboard.app.data.model.DocBoard.BOARD,
                createdBy = currentMemberId.value.orEmpty(),
                memberIds = listOf(Family.ALL_ID),
                photoUrls = listOf(url), fileName = name, fileMime = mime, fileSize = size,
                createdAt = System.currentTimeMillis()))
        }.isSuccess
        onDone(ok, if (ok) null else "저장 실패")
    }

    /** 파일명에서 확장자를 뺀 기본 제목. (예: "가족여행.pdf" → "가족여행") */
    private fun titleFromFileName(name: String): String =
        name.substringBeforeLast('.', name).ifBlank { name }

    /** 문서 제목·열람 대상 수정(올린이/관리자). viewerIds 비거나 all 포함이면 모두 공개. */
    fun updateDoc(item: ListItem, title: String, viewerIds: List<String>) = viewModelScope.launch {
        val cleanTitle = title.trim().ifBlank { titleFromFileName(item.fileName).ifBlank { "문서" } }
        val viewers = if (viewerIds.isEmpty() || viewerIds.contains(Family.ALL_ID)) listOf(Family.ALL_ID) else viewerIds
        runCatching { board.updateFields(item.id, mapOf("text" to cleanTitle, "memberIds" to viewers)) }
    }

    private fun queryFileNameSize(cr: android.content.ContentResolver, uri: android.net.Uri): Pair<String, Long> {
        var name = ""; var size = 0L
        runCatching {
            cr.query(uri, null, null, null, null)?.use { c ->
                val ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (ni >= 0) name = c.getString(ni).orEmpty()
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        if (name.isBlank()) name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        return name to size
    }

    private fun docExt(name: String, mime: String): String {
        val fromName = name.substringAfterLast('.', "").lowercase()
        if (fromName.isNotBlank() && fromName.length <= 5 && fromName.all { it.isLetterOrDigit() }) return fromName
        return android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.lowercase() ?: "bin"
    }

    fun addPlace(boardKey: String, name: String, link: String, description: String = "", address: String = "",
                 image: String = "", naverScore: Double = 0.0, lat: Double = 0.0, lng: Double = 0.0,
                 category: String = "") = viewModelScope.launch {
        if (name.isBlank()) return@launch
        runCatching {
            board.upsertItem(ListItem(text = name.trim(), link = link.trim(), description = description,
                address = address, category = category, photoUrls = if (image.isBlank()) emptyList() else listOf(image),
                naverScore = naverScore, lat = lat, lng = lng,
                board = boardKey, createdBy = currentMemberId.value.orEmpty()))
        }
    }
    fun updatePlace(item: ListItem, name: String, link: String,
                    description: String = item.description, address: String = item.address,
                    image: String = item.photoUrls.firstOrNull().orEmpty(),
                    category: String = item.category) = viewModelScope.launch {
        runCatching {
            board.updateFields(item.id, mapOf(
                "text" to name.trim(), "link" to link.trim(), "description" to description, "address" to address,
                "category" to category,
                "photoUrls" to (if (image.isBlank()) emptyList<String>() else listOf(image)),
            ))
        }
    }
    /** 편집 다이얼로그에서 네이버 링크로 정보 가져오기(콜백으로 결과 전달). */
    fun fetchPlaceInfo(url: String, onResult: (com.familyboard.app.notif.PlaceInfo?) -> Unit) = viewModelScope.launch {
        onResult(com.familyboard.app.notif.NotifyApi.parsePlace(url))
    }
    /** '놓친 장소' 발굴 추천(저장된 곳 제외). 지역이 비어 있으면(전체) 현재 위치의 시군구로 한정해 먼 곳 추천 방지. */
    fun recommendPlace(
        board: String, category: String, region: String, savedNames: List<String>, lat: Double?, lng: Double?,
        onResult: (List<com.familyboard.app.notif.Recommendation>) -> Unit,
    ) = viewModelScope.launch {
        val effectiveRegion =
            if (region.isBlank() && lat != null && lng != null) reverseDistrict(lat, lng).orEmpty() else region
        onResult(com.familyboard.app.notif.NotifyApi.recommend(board, category, effectiveRegion, savedNames, lat, lng))
    }

    /** 좌표 → 현재 시군구 문자열(예: "고양시 일산동구"). 실패 시 null. */
    private suspend fun reverseDistrict(lat: Double, lng: Double): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val geo = android.location.Geocoder(getApplication<Application>(), java.util.Locale.KOREA)
                @Suppress("DEPRECATION")
                val addr = geo.getFromLocation(lat, lng, 1)?.firstOrNull() ?: return@withContext null
                val line = addr.getAddressLine(0)
                    ?: listOfNotNull(addr.adminArea, addr.locality, addr.subLocality).joinToString(" ")
                districtFrom(line)
                    ?: listOfNotNull(addr.locality ?: addr.subAdminArea, addr.subLocality)
                        .filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
            }.getOrNull()
        }

    /** 주소 문자열에서 '시 + 구/군'만 추출(예: "…경기도 고양시 일산동구 백석동…" → "고양시 일산동구"). */
    private fun districtFrom(addressLine: String): String? {
        val toks = addressLine.split(' ', ',').map { it.trim() }.filter { it.isNotBlank() }
        val si = toks.firstOrNull { it.endsWith("시") }
        val guGun = toks.firstOrNull { it.endsWith("구") || it.endsWith("군") }
        return listOfNotNull(si, guGun).distinct().joinToString(" ").ifBlank { null }
    }
    fun describePlace(info: com.familyboard.app.notif.PlaceInfo): String = buildPlaceDesc(info)
    fun fetchLinkInfo(url: String, onResult: (com.familyboard.app.notif.LinkInfo?) -> Unit) = viewModelScope.launch {
        onResult(com.familyboard.app.notif.NotifyApi.parseLink(url))
    }
    fun setPlaceRating(item: ListItem, rating: Int) = viewModelScope.launch {
        runCatching { board.upsertItem(item.copy(rating = rating.toLong().coerceIn(0L, 5L))) }
    }
    fun addPlaceComment(item: ListItem, text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        val note = com.familyboard.app.data.model.ProgressNote(
            text = text.trim(), by = currentMemberId.value.orEmpty(), dateIso = LocalDate.now().toString())
        runCatching { board.upsertItem(item.copy(progress = item.progress + note)) }
    }
    fun deletePlaceComment(item: ListItem, index: Int) = viewModelScope.launch {
        if (index < 0 || index >= item.progress.size) return@launch
        runCatching { board.upsertItem(item.copy(progress = item.progress.filterIndexed { i, _ -> i != index })) }
    }
    fun updatePlaceComment(item: ListItem, index: Int, text: String) = viewModelScope.launch {
        if (index < 0 || index >= item.progress.size || text.isBlank()) return@launch
        val updated = item.progress.mapIndexed { i, n -> if (i == index) n.copy(text = text.trim()) else n }
        runCatching { board.upsertItem(item.copy(progress = updated)) }
    }

    val allowanceJunyoung: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.AllowanceBoards.JUNYOUNG)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allowanceJunho: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.AllowanceBoards.JUNHO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 인생 버킷 (부부 공용, 단일 보드)
    val bucketItems: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.BucketBoards.BOARD)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun bucketById(id: String): ListItem? = bucketItems.value.firstOrNull { it.id == id }

    /** 사용 가능한 앱 업데이트(없으면 null) */
    val updateInfo: MutableStateFlow<UpdateInfo?> = MutableStateFlow(null)

    /** dateIso -> 공휴일명 */
    val holidays: MutableStateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    private val loadedMonths = mutableSetOf<String>()

    init {
        // 일정/담당자 변경 시 미리 알림 예약 동기화
        viewModelScope.launch {
            combine(events, currentMemberId) { evs, mid -> evs to mid }.collect { (evs, mid) ->
                ReminderScheduler.reconcile(getApplication(), evs, mid)
            }
        }
        // D-Day/생일 알림 예약 (앱 실행/항목 변경 시 다음 회차 재예약)
        viewModelScope.launch {
            combine(ddayItems, currentMemberId) { items, mid -> items to mid }.collect { (items, mid) ->
                com.familyboard.app.notif.DDayReminderScheduler.reconcile(getApplication(), items, mid)
            }
        }
        // 본인 확정 시 FCM 토큰을 서버에 등록 (등록 알림 수신용)
        viewModelScope.launch {
            currentMemberId.filterNotNull().distinctUntilChanged().collect { mid ->
                runCatching {
                    val token = FirebaseMessaging.getInstance().token.await()
                    NotifyApi.register(mid, token)
                }
                // Home Assistant 기기정보 5분 주기 리포트 예약(+즉시 1회)
                runCatching {
                    com.familyboard.app.notif.HaReporter.report(getApplication(), mid)
                    com.familyboard.app.notif.HaReportScheduler.schedule(getApplication())
                }
            }
        }
        // 앱 업데이트 확인
        viewModelScope.launch { updateInfo.value = UpdateChecker.check() }
    }

    /** 홈 화면 진입 등에서 업데이트를 다시 확인(최신이거나 확인 실패 시 배지 사라짐). */
    fun refreshUpdate() = viewModelScope.launch { updateInfo.value = UpdateChecker.check() }

    fun itemsFor(boardKey: String): StateFlow<List<ListItem>> = when (boardKey) {
        BoardType.TODO.key -> todoItems
        BoardType.NOTICE.key -> noticeItems
        else -> shoppingItems
    }

    fun selectMember(id: String) = viewModelScope.launch {
        container.currentUserStore.setCurrentMember(id)
    }

    fun ensureHolidays(month: YearMonth) = viewModelScope.launch {
        val key = month.toString()
        if (!loadedMonths.add(key)) return@launch
        val fetched = holidayRepo.holidays(month.year, month.monthValue)
        if (fetched.isNotEmpty()) {
            holidays.value = holidays.value + fetched.associate { it.dateIso to it.name }
        }
    }

    fun addEvent(event: CalendarEvent, notifyTargets: List<String> = emptyList()) = viewModelScope.launch {
        runCatching { board.upsertEvent(event) }
        notifyEventRegistered(event, notifyTargets)
    }

    /** 등록 알림: 사용자가 명시적으로 선택한 가족에게만 발송(기본은 없음, 등록자 제외). */
    private suspend fun notifyEventRegistered(e: CalendarEvent, notifyTargets: List<String>) {
        val actor = e.createdBy
        val targets = notifyTargets.filter { it != actor }.distinct()
        if (targets.isEmpty()) return
        val title = "📌 ${Family.nameOf(actor)}님이 일정을 등록했어요"
        val body = "\"${e.title}\"\n📅 ${eventWhenText(e)}"
        runCatching { NotifyApi.notify(actor, targets, title, body) }
    }
    fun updateEvent(event: CalendarEvent) = viewModelScope.launch { runCatching { board.upsertEvent(event) } }
    fun deleteEvent(id: String) = viewModelScope.launch { runCatching { board.deleteEvent(id) } }

    /** 반복 일정에서 특정 날짜만 제외(그 회차만 삭제) */
    fun excludeOccurrence(event: CalendarEvent, dateIso: String) = viewModelScope.launch {
        if (!event.exdates.contains(dateIso)) {
            runCatching { board.upsertEvent(event.copy(exdates = event.exdates + dateIso)) }
        }
    }

    fun eventById(id: String): CalendarEvent? = events.value.firstOrNull { it.id == id }

    fun addItem(item: ListItem) = viewModelScope.launch { runCatching { board.upsertItem(item) } }
    fun updateItem(item: ListItem) = viewModelScope.launch { runCatching { board.upsertItem(item) } }

    /** 커스텀 리스트 삭제: 자식 항목 먼저 삭제 후 정의 문서 삭제(중간 실패 시 정의가 남아 재시도 가능, 고아 방지). */
    fun deleteCustomList(listId: String) = viewModelScope.launch {
        runCatching { board.deleteByBoard(listId); board.deleteItem(listId) }
    }

    /** 할 일 추가 + 태깅된 담당자에게 등록 알림(등록자 제외). 장보기 등 다른 보드는 사용하지 않음. */
    fun addTodoWithNotify(item: ListItem) = viewModelScope.launch {
        runCatching { board.upsertItem(item) }
        val actor = item.createdBy
        val targets = (if (item.memberIds.contains(Family.ALL_ID)) Family.members.map { it.id } else item.memberIds)
            .filter { it != actor }
            .distinct()
        if (targets.isEmpty()) return@launch
        val body = "\"${item.text}\" 할 일이 등록됐어요\n등록: ${Family.nameOf(actor)}"
        runCatching { NotifyApi.notify(actor, targets, "✅ 새 할 일", body) }
    }

    fun toggleItem(id: String, checked: Boolean) = viewModelScope.launch { runCatching { board.setChecked(id, checked) } }
    fun deleteItem(id: String) = viewModelScope.launch { runCatching { board.deleteItem(id) }; bumpFunRefresh() }

    /** 긴급 연락 발송: 대상에게 전체화면 긴급 알림. wantLocation 이면 위치공유 요청 버튼 노출. */
    fun sendEmergency(targetIds: List<String>, message: String, wantLocation: Boolean) = viewModelScope.launch {
        val actor = currentMemberId.value.orEmpty()
        val targets = targetIds.filter { it != actor }.distinct()
        if (targets.isEmpty()) return@launch
        val data = mapOf(
            "type" to "emergency",
            "sender" to actor,
            "msg" to message,
            "wantLoc" to if (wantLocation) "1" else "0",
        )
        runCatching {
            NotifyApi.notifyData(actor, targets, "빠른 연락 요청", "${Family.nameOf(actor)}님의 빠른 연락 요청", data)
        }
    }

    /**
     * 용돈 정산: 체크된 항목들을 대상 아이에게 완료 알림으로 보내고 목록에서 삭제.
     * 메시지 예) "엄마의 용돈 정산 15,000원이 완료 되었습니다. 확인해보세요."
     */
    fun settleAllowance(targetMemberId: String, items: List<ListItem>) = viewModelScope.launch {
        if (items.isEmpty()) return@launch
        val actor = currentMemberId.value.orEmpty()
        val parent = when (actor) {
            "eunseon" -> "엄마"
            "seonil" -> "아빠"
            else -> Family.nameOf(actor)
        }
        // 항목별로 삭제하고 성공한 것만 집계 → 실제로 정산된 항목만 알림에 반영
        // (일괄 실패 시 알림 누락, 일부 실패 시 유령 알림/재정산을 방지)
        val settled = items.filter { runCatching { board.deleteItem(it.id) }.isSuccess }
        if (settled.isEmpty()) return@launch
        val settledTotal = settled.sumOf { it.amount }
        val msg = buildString {
            append("${parent}가 아래 항목을 정산했어요.\n")
            append("\n")
            settled.forEach {
                append("\t\t${it.text.ifBlank { "항목" }} %,d원\n".format(it.amount))
            }
            append("\t\t---------------\n")
            append("\t\t합계 : %,d원\n".format(settledTotal))
            append("\n")
            append("❤️ 사랑해 아들~")
        }
        runCatching { NotifyApi.notify(actor, listOf(targetMemberId), "용돈 정산 완료", msg) }
    }

    /** 여러 항목의 체크 상태를 한 번에 설정(전체 체크/해제). */
    fun setCheckedAll(items: List<ListItem>, checked: Boolean) = viewModelScope.launch {
        runCatching { items.forEach { if (it.checked != checked) board.setChecked(it.id, checked) } }
    }

    /**
     * 용돈 조르기: 자녀(준영/준호)가 엄마(은선)에게 체크한 항목의 정산을 조른다.
     * 항목은 삭제하지 않는다. 알림 제목 "조르기".
     */
    fun nudgeAllowance(childMemberId: String, childName: String, items: List<ListItem>) = viewModelScope.launch {
        if (items.isEmpty()) return@launch
        val total = items.sumOf { it.amount }
        val body = buildString {
            append("${childName}에게서 용돈 정산 요청이 왔습니다.\n")
            append("\n")
            items.forEach {
                append("\t\t${it.text.ifBlank { "항목" }} %,d원\n".format(it.amount))
            }
            append("\t\t---------------\n")
            append("\t\t합계 : %,d원\n".format(total))
            append("\n")
            append("❤️ 사랑해요 엄마~")
        }
        runCatching { NotifyApi.notify(childMemberId, listOf("eunseon"), "조르기", body) }
    }
}

private val KR_DOW = listOf("월", "화", "수", "목", "금", "토", "일")

private fun krDate(d: LocalDate): String =
    "${d.monthValue}월 ${d.dayOfMonth}일 (${KR_DOW[d.dayOfWeek.value - 1]})"

private fun krTime(hhmm: String): String {
    val t = runCatching { LocalTime.parse(hhmm) }.getOrNull() ?: return ""
    val ampm = if (t.hour < 12) "오전" else "오후"
    val h = if (t.hour % 12 == 0) 12 else t.hour % 12
    return "$ampm $h:%02d".format(t.minute)
}

/** 알림용 친근한 일시 표기 ("8월 10일 (월) 오후 6:00" / 하루 종일 / 여러 날 ~) */
private fun eventWhenText(e: CalendarEvent): String {
    val d1 = runCatching { LocalDate.parse(e.startDateIso) }.getOrNull() ?: return ""
    val d2 = runCatching { LocalDate.parse(e.endDateIso.ifBlank { e.startDateIso }) }.getOrNull() ?: d1
    return when {
        d2 != d1 -> "${krDate(d1)} ~ ${krDate(d2)}"
        e.allDay -> "${krDate(d1)} · 하루 종일"
        e.startTime.isNotBlank() -> "${krDate(d1)} ${krTime(e.startTime)}"
        else -> krDate(d1)
    }
}
