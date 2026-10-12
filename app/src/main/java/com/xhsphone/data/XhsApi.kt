package com.xhsphone.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

val XhsJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Serializable
data class ApiEnvelope(
    val success: Boolean = false,
    val code: Int = 0,
    val msg: String? = null,
    val data: JsonObject? = null
)

@Serializable
data class FeedNote(
    @SerialName("id") val id: String = "",
    @SerialName("note_id") val noteId: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("desc") val desc: String = "",
    @SerialName("type") val type: String = "normal",
    @SerialName("user") val user: FeedUser? = null,
    @SerialName("image_list") val imageList: List<FeedImage> = emptyList(),
    @SerialName("video") val video: FeedVideo? = null,
    @SerialName("interact_info") val interact: InteractInfo? = null,
    @SerialName("xsec_token") val xsecToken: String = ""
) {
    val displayId: String get() = noteId.ifEmpty { id }
}

@Serializable
data class FeedUser(
    @SerialName("user_id") val userId: String = "",
    @SerialName("nickname") val nickname: String = "",
    @SerialName("avatar") val avatar: String = "",
    @SerialName("image") val image: String = ""
) {
    val avatarUrl: String get() = avatar.ifEmpty { image }
}

@Serializable
data class FeedImage(
    @SerialName("url") val url: String = "",
    @SerialName("width") val width: Int = 0,
    @SerialName("height") val height: Int = 0,
    @SerialName("trace_id") val traceId: String = ""
) {
    // 小红书图片 URL 带感叹号后缀控制尺寸
    val thumbUrl: String get() = if (url.contains("!")) url else "$url!nd_dft_wlteh"
}

@Serializable
data class FeedVideo(
    @SerialName("consumer") val consumer: VideoConsumer? = null
)

@Serializable
data class VideoConsumer(
    @SerialName("origin_video_key") val originKey: String = "",
    @SerialName("video_key") val videoKey: String = ""
)

@Serializable
data class InteractInfo(
    @SerialName("liked") val liked: Boolean = false,
    @SerialName("collected") val collected: Boolean = false,
    @SerialName("like_count") val likeCount: String = "0",
    @SerialName("collected_count") val collectedCount: String = "0",
    @SerialName("comment_count") val commentCount: String = "0",
    @SerialName("share_count") val shareCount: String = "0"
)

/**
 * 小红书网页版 API 客户端。接口路径来自对 xhsTV 0.2.10 的逆向。
 */
class XhsApi(private val session: XhsSession) {

    private val base = XhsSession.API_BASE

    private fun get(path: String, params: Map<String, String> = emptyMap()): String {
        val url = (base + path).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> url.addQueryParameter(k, v) }
        val req = Request.Builder().url(url.build()).get().build()
        session.client.newCall(req).execute().use { resp ->
            return resp.body?.string().orEmpty()
        }
    }

    private fun post(path: String, json: String): String {
        val body = json.toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(base + path).post(body).build()
        session.client.newCall(req).execute().use { resp ->
            return resp.body?.string().orEmpty()
        }
    }

    /** 首页推荐流 */
    fun homeFeed(cursor: String = ""): Pair<List<FeedNote>, String> {
        val params = mutableMapOf(
            "oid" to "homefeed_recommend",
            "sid" to "",
            "note_index" to "0",
            "unread" to "false"
        )
        if (cursor.isNotEmpty()) params["cursor"] = cursor
        val raw = get("/api/sns/web/v1/homefeed", params)
        val env = XhsJson.decodeFromString<ApiEnvelope>(raw)
        val dataObj = env.data ?: return emptyList<FeedNote>() to ""
        val items = dataObj["items"] as? JsonArray ?: return emptyList<FeedNote>() to ""
        val notes: List<FeedNote> = items.mapNotNull { el ->
            runCatching {
                val obj = el.jsonObject["note_card"]!!.jsonObject
                XhsJson.decodeFromJsonElement(FeedNote.serializer(), obj)
            }.getOrNull()
        }
        val nextCursor = dataObj["cursor"]?.jsonPrimitive?.content.orEmpty()
        val hasMore = dataObj["has_more"]?.jsonPrimitive?.booleanOrNull ?: false
        return notes to if (hasMore) nextCursor else ""
    }

    /** 笔记详情 */
    fun noteDetail(noteId: String, xsecToken: String = ""): FeedNote? {
        val params = mutableMapOf("source_note_id" to noteId)
        if (xsecToken.isNotEmpty()) {
            params["xsec_token"] = xsecToken
            params["xsec_source"] = "pc_feed"
        }
        val raw = get("/api/sns/web/v1/feed", params)
        val env = runCatching { XhsJson.decodeFromString<ApiEnvelope>(raw) }.getOrNull()
            ?: return null
        val dataObj = env.data ?: return null
        val items = dataObj["items"] as? JsonArray ?: return null
        val obj = items.firstOrNull()?.jsonObject ?: return null
        val card = obj["note_card"]?.jsonObject ?: return null
        return runCatching { XhsJson.decodeFromJsonElement(FeedNote.serializer(), card) }.getOrNull()
    }

    /** 评论列表 */
    fun comments(noteId: String, xsecToken: String = "", cursor: String = ""): Pair<List<XhsComment>, String> {
        val params = mutableMapOf(
            "note_id" to noteId,
            "top_comment_id" to "",
            "image_formats" to "jpg,webp,avif"
        )
        if (xsecToken.isNotEmpty()) {
            params["xsec_token"] = xsecToken
            params["xsec_source"] = "pc_comment"
        }
        if (cursor.isNotEmpty()) params["cursor"] = cursor
        val raw = get("/api/sns/web/v2/comment/page", params)
        val env = runCatching { XhsJson.decodeFromString<ApiEnvelope>(raw) }.getOrNull()
            ?: return emptyList<XhsComment>() to ""
        val dataObj = env.data ?: return emptyList<XhsComment>() to ""
        val list = dataObj["comments"] as? JsonArray ?: return emptyList<XhsComment>() to ""
        val comments: List<XhsComment> = list.mapNotNull {
            runCatching { XhsJson.decodeFromJsonElement(XhsComment.serializer(), it) }.getOrNull()
        }
        val nextCursor = dataObj["cursor"]?.jsonPrimitive?.content.orEmpty()
        val hasMore = dataObj["has_more"]?.jsonPrimitive?.booleanOrNull ?: false
        return comments to if (hasMore) nextCursor else ""
    }

    /** 搜索笔记 */
    fun searchNotes(keyword: String, page: Int = 1): List<FeedNote> {
        val json = """{"keyword":"$keyword","page":$page,"page_size":20,"search_id":"${java.util.UUID.randomUUID()}","sort":"general","note_type":0}"""
        val raw = post("/api/sns/web/v2/search/notes", json)
        val env = runCatching { XhsJson.decodeFromString<ApiEnvelope>(raw) }.getOrNull()
            ?: return emptyList()
        val dataObj = env.data ?: return emptyList()
        val items = dataObj["items"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { el ->
            runCatching {
                val obj = el.jsonObject["note_card"]!!.jsonObject
                XhsJson.decodeFromJsonElement(FeedNote.serializer(), obj)
            }.getOrNull()
        }
    }

    /** 当前登录用户信息 */
    fun selfInfo(): XhsMe? {
        val raw = runCatching { get("/api/sns/web/v2/user/me") }.getOrNull() ?: return null
        val env = runCatching { XhsJson.decodeFromString<ApiEnvelope>(raw) }.getOrNull()
            ?: return null
        if (!env.success) return null
        return runCatching {
            XhsJson.decodeFromJsonElement(XhsMe.serializer(), env.data!!)
        }.getOrNull()
    }
}

@Serializable
data class XhsComment(
    val id: String = "",
    val content: String = "",
    val user: FeedUser? = null,
    @SerialName("like_count") val likeCount: String = "0",
    @SerialName("sub_comment_count") val subCommentCount: String = "0",
    @SerialName("create_time") val createTime: Long = 0
)

@Serializable
data class XhsMe(
    @SerialName("user_id") val userId: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val desc: String = ""
)
