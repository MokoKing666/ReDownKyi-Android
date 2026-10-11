package com.moko.downkyi.re.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 下载任务的持久化快照。
 *
 * 只持久化**未完成**的任务：重启后任务以「已暂停」状态回到列表，
 * 分片文件与断点签名都在，点「继续」即续传 —— 不会静默重跑。
 * 已完成的任务不在这里：产物由「媒体库」承载（扫描真实文件），
 * 两处职责不同，重复记录只会造成两边状态打架。
 *
 * 存储用每任务一个 JSON 文件而不是单文件全量：任务结束（发布/删除）
 * 只删自己的文件，互不干扰，也避免并发写同一个文件。
 */
@Serializable
data class PersistedStream(
    val kind: String,
    val path: String,
    val urls: List<String>,
)

/** 封面、弹幕这类随任务下载的小文件：重启后仍需随成品一起发布。 */
@Serializable
data class PersistedExtra(
    val path: String,
    val kind: String,
)

@Serializable
data class PersistedDownload(
    val id: String,
    val title: String,
    val subtitle: String,
    val outputPath: String? = null,
    val streams: List<PersistedStream>,
    /** 各流断点签名（按落盘路径）—— 没有它，重启后分片会因签名不匹配被全部作废。 */
    val signatures: Map<String, String> = emptyMap(),
    /** 发布用：文件名与保存位置。 */
    val publishFileName: String,
    val publishLocation: String,
    val extras: List<PersistedExtra> = emptyList(),
)

/**
 * 任务快照的读写。
 *
 * JSON 用 `ignoreUnknownKeys`：应用升级后新增字段时，旧快照仍能读入，
 * 不会出现"升级后所有任务消失"。
 */
class TaskPersistence(context: Context) {

    private val dir = File(context.filesDir, "tasks").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    fun save(download: PersistedDownload) {
        File(dir, "${download.id}.json").writeText(json.encodeToString(download))
    }

    fun load(id: String): PersistedDownload? =
        File(dir, "$id.json").takeIf { it.exists() }
            ?.let { file -> runCatching { json.decodeFromString<PersistedDownload>(file.readText()) }.getOrNull() }

    fun loadAll(): List<PersistedDownload> =
        dir.listFiles { f -> f.extension == "json" }
            .orEmpty()
            .mapNotNull { file ->
                runCatching { json.decodeFromString<PersistedDownload>(file.readText()) }
                    .getOrNull()
            }

    /** 更新断点签名（暂停时调用：签名随分片进度推进）。 */
    fun updateSignatures(id: String, signatures: Map<String, String>) {
        load(id)?.let { save(it.copy(signatures = signatures)) }
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }
}
