package com.moko.downkyi.re.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.moko.downkyi.re.ui.LibraryItemUi
import com.moko.downkyi.re.ui.LibraryKind
import com.moko.downkyi.re.ui.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 单个已扫描文件：UI 条目 + 原始大小（聚合总量用）。 */
private data class Scanned(val item: LibraryItemUi, val sizeBytes: Long)

/**
 * 媒体库的真实数据源：扫描本应用发布到系统媒体库的文件。
 *
 * 只扫三个自有目录（Movies/DownKyi、Download/DownKyi、Pictures/DownKyi），
 * 不碰用户其它文件。视频/图片按集合直接判定类型；下载集合里混杂多种
 * 文件，按扩展名细分（xml/ass → 弹幕，srt/vtt → 字幕，m4a/mp3 → 音频）。
 */
class MediaLibraryRepository(private val context: Context) {

    data class Snapshot(
        val items: List<LibraryItemUi>,
        val totalBytes: Long,
    )

    suspend fun scan(): Snapshot = withContext(Dispatchers.IO) {
        val scanned = buildList {
            addAll(queryCollection(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), LibraryKind.Video))
            addAll(queryCollection(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), LibraryKind.Image))
            addAll(queryCollection(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), null))
        }
        Snapshot(
            items = scanned.map { it.item }.sortedByDescending { it.id },
            totalBytes = scanned.sumOf { it.sizeBytes },
        )
    }

    /**
     * 查询一个媒体集合里属于本应用的文件。
     *
     * [forcedKind] 非空时类型由集合决定（视频/图片集合）；
     * 为空时（下载集合混杂多种文件）按扩展名细分。
     */
    private fun queryCollection(uri: Uri, forcedKind: LibraryKind?): List<Scanned> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        val result = mutableListOf<Scanned>()

        runCatching {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->

                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathCol) ?: continue
                    if (!path.contains("DownKyi")) continue

                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    val dateModifiedSec = cursor.getLong(dateCol)
                    val kind = forcedKind ?: kindByName(name)
                    result += Scanned(
                        item = LibraryItemUi(
                            id = "m-${cursor.getLong(idCol)}",
                            title = name.substringBeforeLast('.'),
                            subtitle = subtitle(path, dateModifiedSec),
                            sizeText = formatBytes(size),
                            kind = kind,
                        ),
                        sizeBytes = size,
                    )
                }
            }
        }
        // 查询失败（最常见的是媒体权限未授予）时返回空列表，
        // 由调用方的空态与「需要登录/授权」提示呈现
        return result
    }

    /** 副标题：所在目录 + 修改日期。清晰度已在文件名里，不再重复。 */
    private fun subtitle(path: String, dateModifiedSec: Long): String {
        val dir = path.trimEnd('/').substringAfterLast('/').ifBlank { "DownKyi" }
        val date = if (dateModifiedSec > 0) {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(dateModifiedSec * 1000))
        } else {
            null
        }
        return listOfNotNull(dir, date).joinToString(" · ")
    }

    private fun kindByName(name: String): LibraryKind = when (name.substringAfterLast('.').lowercase()) {
        "xml", "ass" -> LibraryKind.Danmaku
        "srt", "vtt" -> LibraryKind.Subtitle
        "m4a", "mp3", "flac", "aac" -> LibraryKind.Audio
        "jpg", "jpeg", "png", "webp" -> LibraryKind.Image
        "mp4", "mkv", "m4s" -> LibraryKind.Video
        else -> LibraryKind.Other
    }
}
