package com.moko.downkyi.re.download

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.moko.downkyi.re.core.model.SaveLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 成品的媒体种类，决定它最终落到哪个集合（视频 / 音频 / 图片 / 文件）。 */
enum class MediaKind(val extension: String) {
    VIDEO("mp4"),
    AUDIO("m4a"),
    COVER("jpg"),
    DANMAKU("xml"),
}

/**
 * 成品落盘：分片与封装在**工作目录**里进行，完成后按 [SaveLocation] 发布。
 *
 * 为什么不直接往目标位置写：
 *  - MediaStore 需要 IS_PENDING 两段式提交，半成品一旦被扫描到就是废文件；
 *  - 封装失败时残件留在工作目录，与「取消保留分片以便续传」语义一致，
 *    直接写目标位置会把「重试」和「垃圾文件」搅在一起。
 */
class DownloadStorage(private val context: Context) {

    /** 分片与封装的工作目录。任务内自包含，发布或失败后按需清理。 */
    fun workDir(taskId: String): File =
        File(context.getExternalFilesDir(null), "DownKyi/work/$taskId").apply { mkdirs() }

    /** 工作根目录（所有任务的工作目录之父），供工具箱扫描与清理。 */
    fun workRoot(): File =
        File(context.getExternalFilesDir(null), "DownKyi/work")

    /** 「应用目录」位置的最终落点（无需存储权限，卸载即删）。 */
    fun appMediaDir(kind: MediaKind): File {
        val type = when (kind) {
            MediaKind.VIDEO -> Environment.DIRECTORY_MOVIES
            MediaKind.AUDIO, MediaKind.DANMAKU -> Environment.DIRECTORY_DOWNLOADS
            MediaKind.COVER -> Environment.DIRECTORY_PICTURES
        }
        return File(context.getExternalFilesDir(type), "DownKyi").apply { mkdirs() }
    }

    /**
     * 把已完成的成品发布到 [location]，返回最终路径。
     *
     * CUSTOM 暂按「应用目录」处理：目录选择器（SAF）还没接入，
     * 而一个把文件写到未知路径的下载器比「先落到应用目录」糟糕得多 ——
     * 等选择器接上后这里会变成唯一的真分支。
     */
    suspend fun publish(file: File, fileName: String, kind: MediaKind, location: SaveLocation): String =
        withContext(Dispatchers.IO) {
            when (location) {
                SaveLocation.GALLERY -> insertViaMediaStore(file, fileName, kind)
                else -> moveToAppDir(file, fileName, kind)
            }
        }

    /** 清理工作目录。只在任务彻底结束（发布完成或用户放弃）时调用。 */
    suspend fun cleanup(taskId: String) = withContext(Dispatchers.IO) {
        workDir(taskId).deleteRecursively()
    }

    // -----------------------------------------------------------------------

    private fun moveToAppDir(file: File, fileName: String, kind: MediaKind): String {
        val target = File(appMediaDir(kind), uniqueName(appMediaDir(kind), fileName, kind))
        if (file.absolutePath != target.absolutePath) {
            if (!file.renameTo(target)) {
                // 跨卷时 renameTo 会失败，退回复制
                file.copyTo(target, overwrite = true)
                file.delete()
            }
        }
        return target.absolutePath
    }

    /**
     * MediaStore 两段式提交：IS_PENDING=1 插入、写完流、再置 0。
     *
     * 不先置 0 的话，扫描器可能永远收不到这个文件 —— 用户在相册里
     * 看到的会是「下载成功但找不到东西」。
     */
    private fun insertViaMediaStore(file: File, fileName: String, kind: MediaKind): String {
        val resolver = context.contentResolver
        val (collection, relativePath) = when (kind) {
            MediaKind.VIDEO ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                    "Movies/DownKyi"

            MediaKind.AUDIO, MediaKind.DANMAKU ->
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                    "Download/DownKyi"

            MediaKind.COVER ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                    "Pictures/DownKyi"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("无法写入系统媒体库（${relativePath}）")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: throw IllegalStateException("媒体库拒绝写入：$uri")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw IllegalStateException("保存到系统媒体库失败：${e.message}", e)
        } finally {
            file.delete()
        }
    }

    /** 文件名去非法字符；重名时追加序号而不是覆盖 —— 覆盖会悄悄毁掉旧下载。 */
    private fun uniqueName(dir: File, fileName: String, kind: MediaKind): String {
        val safe = fileName.replace(ILLEGAL, "_").trim().ifBlank { "未命名" }
        var candidate = "${safe}.${kind.extension}"
        var index = 2
        while (File(dir, candidate).exists()) {
            candidate = "$safe ($index).${kind.extension}"
            index++
        }
        return candidate
    }

    private companion object {
        val ILLEGAL = Regex("""[\\/:*?"<>|]""")
    }
}
