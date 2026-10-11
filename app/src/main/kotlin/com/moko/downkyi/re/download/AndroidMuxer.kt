package com.moko.downkyi.re.download

import com.moko.downkyi.re.core.download.Muxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [Muxer] 的 Android 实现：交给 [MediaMuxerHelper]。
 *
 * 为什么包一层而不直接用：下载编排层在纯 JVM 模块里单测，见不到
 * android.media —— 封装的**时机**（全部落盘之后、状态机切 Completed 之前）
 * 由编排层负责，封装的**做法**由这里负责，两层各自可替换。
 *
 * remux 是纯阻塞调用，必须在 IO 线程上跑。
 */
object AndroidMuxer : Muxer {

    override suspend fun mux(videoPath: String?, audioPath: String?, outputPath: String) {
        val ok = withContext(Dispatchers.IO) {
            MediaMuxerHelper.remux(videoPath, audioPath, outputPath)
        }
        if (!ok) {
            // 校验失败时 helper 已把残件删掉；这里只负责把失败说清楚
            throw IllegalStateException("封装失败：成品校验未通过，已放弃输出")
        }
    }
}
