package com.moko.downkyi.re.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.File
import java.nio.ByteBuffer

/**
 * 用系统 MediaMuxer 把 B 站的 m4s 重新封装为可正常播放的 mp4。
 *
 * 关键设计：**输入文件里的所有音视频轨都会被原样搬运**，而不是只取第一条视频轨。
 * - durl 直链下载到的是一个完整 mp4，音视频在同一个文件里；若只 selectTrack
 *   第一条视频轨，音频会被静默丢掉（表现为「合并出来的视频没声音」）；
 * - 单独下载音频时，输出应当是 m4a，不能因为没有视频轨就整体失败；
 * - 视频 m4s + 音频 m4s 两个文件时，两条轨都会被搬到同一个 mp4 里。
 *
 * 全程不重新编码，只做封装，因此速度快、画质无损。
 */
object MediaMuxerHelper {

    /** 单帧缓冲初始 4MB */
    private const val INITIAL_BUFFER_SIZE = 4 shl 20

    /** 动态扩容上限 64MB，避免极端 8K 关键帧把内存吃穿 */
    private const val MAX_BUFFER_SIZE = 64 shl 20

    /**
     * MediaMuxer **从 Android 7.1（API 25 / Nougat MR1）起才支持把 B 帧封装进 MP4**。
     *
     * B 站视频普遍带 B 帧，而 B 帧在解码顺序里的 PTS 本来就是回退的
     * （例如 I(0) P(3) B(1) B(2)），所以 API 25+ 必须**原样写入真实 PTS**。
     * 早先的实现为了满足「时间戳必须单调」把回退的 PTS 改写成 last+1，
     * 结果帧的显示时刻被压平、显示顺序错乱，表现就是「合并出来的视频卡卡的」。
     *
     * 只有 API 24 需要退回单调处理——那里本来就不支持 B 帧，压平是唯一能封装成功的办法。
     */
    private val supportsBFrames = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1

    /** 一条待搬运的轨道：(所属输入, 输入轨下标) -> 输出轨下标 */
    private class TrackRef(val extractor: MediaExtractor, val source: Int) {
        var target: Int = -1
        var isVideo: Boolean = false
        var isAudio: Boolean = false
    }

    fun remux(videoPath: String?, audioPath: String?, outputPath: String): Boolean {
        val inputs = listOfNotNull(
            videoPath?.takeIf { it.isNotEmpty() && File(it).exists() },
            audioPath?.takeIf { it.isNotEmpty() && File(it).exists() },
        ).distinct()
        if (inputs.isEmpty()) return false

        val extractors = mutableListOf<MediaExtractor>()
        var muxer: MediaMuxer? = null
        var muxerStopped = false
        try {
            val refs = mutableListOf<TrackRef>()
            // 视频轨限定为**全局一条**（所有输入一起算），而不是「每条输入一条」。
            // 这样无论进来的是「视频 m4s + 音频 m4s」还是「音视频同文件的 durl mp4」，
            // 成品里都不可能再出现第二条视频轨——那正是体积翻倍且播不了的成因。
            var videoTaken = false
            for (path in inputs) {
                val extractor = MediaExtractor()
                var accepted = 0
                try {
                    extractor.setDataSource(path)
                    for (index in 0 until extractor.trackCount) {
                        val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                        if (mime == null) continue
                        val isVideo = mime.startsWith("video/")
                        val isAudio = mime.startsWith("audio/")
                        if (!isVideo && !isAudio) continue

                        // ⚠️ 每条输入**只取第一条视频轨**，音频轨则全部保留。
                        //
                        // 这里曾经写成「所有音视频轨都搬」，本意是修 durl 直链
                        // （音视频在同一个 mp4 里）只取第一条视频轨会把音频丢掉的问题。
                        // 但用力过猛：杜比视界 Profile 7 是「基础层 BL + 增强层 EL」
                        // **两条视频轨**，两条都搬会得到——体积正好翻倍、
                        // 且系统播放器普遍播不了的**双轨 mp4**。
                        //
                        // 音频全留、视频只留第一条，这两个需求互不冲突：
                        // durl 的音频照样不会丢，多轨视频也不会被复制。
                        if (isVideo) {
                            if (videoTaken) continue
                            videoTaken = true
                        }
                        extractor.selectTrack(index)
                        val ref = TrackRef(extractor, index)
                        ref.isVideo = isVideo
                        ref.isAudio = isAudio
                        refs.add(ref)
                        accepted++
                    }
                    if (accepted > 0) {
                        extractors.add(extractor)
                    } else {
                        extractor.release()
                    }
                } catch (_: Throwable) {
                    try {
                        extractor.release()
                    } catch (_: Throwable) {
                        // 忽略
                    }
                }
            }
            if (refs.isEmpty()) return false

            val expectVideo = refs.any { it.isVideo }
            val expectAudio = refs.any { it.isAudio }

            File(outputPath).parentFile?.let { if (!it.exists()) it.mkdirs() }
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            for (ref in refs) {
                ref.target = muxer.addTrack(ref.extractor.getTrackFormat(ref.source))
            }
            muxer.start()

            var buffer = ByteBuffer.allocateDirect(INITIAL_BUFFER_SIZE)
            val info = MediaCodec.BufferInfo()
            val finished = BooleanArray(refs.size)
            val lastWritten = LongArray(refs.size) { Long.MIN_VALUE }
            // 交错用的进度键：记录该轨已读到的最大时间戳，保证单调
            val progress = LongArray(refs.size) { Long.MIN_VALUE }

            // 所有轨道的样本按时间戳交错写入；每条轨内部始终保持 extractor 的读取顺序
            // （也就是解码顺序），不会因为 PTS 回退而被重排
            while (true) {
                var pick = -1
                var pickTime = Long.MAX_VALUE
                for (index in refs.indices) {
                    if (finished[index]) continue
                    val time = refs[index].extractor.sampleTime
                    if (time < 0) {
                        finished[index] = true
                        continue
                    }
                    // 用「已读到的最大值」而不是当前 PTS 做交错依据：
                    // B 帧的 PTS 会回退，直接拿它比较会让交错顺序来回抖动
                    if (time > progress[index]) progress[index] = time
                    if (progress[index] < pickTime) {
                        pickTime = progress[index]
                        pick = index
                    }
                }
                if (pick < 0) break

                val ref = refs[pick]

                // ⚠️ 读到哪条轨，就必须先切到哪条轨。这一行是整个封装正确性的前提。
                //
                // MediaExtractor 的 sampleTime / readSampleData 读的都是**「当前选中轨」**，
                // 而 selectTrack 的作用正是「把该轨设为当前轨」。同一个 extractor 上
                // 选中多条轨时，当前轨永远停在**最后一次 selectTrack** 的那一条上——
                // 上面的初始化循环把每条轨都 select 了一遍，于是当前轨留在了最后一条。
                //
                // 不在这里重新切换的后果（实测踩过）：
                //   • 两条轨的 ref 都在读同一条轨的数据，同一条数据被写两遍
                //     → 成品体积正好翻倍
                //   • 轨道声明的格式来自 A 轨，写进去的样本却来自 B 轨
                //     → 格式与数据不一致，播放器直接打不开
                ref.extractor.selectTrack(ref.source)

                buffer.clear()
                val size: Int
                try {
                    size = ref.extractor.readSampleData(buffer, 0)
                } catch (tooSmall: IllegalArgumentException) {
                    // 极端 4K/8K 关键帧可能超过当前缓冲，按倍扩容后重试同一个样本
                    // （此时还没 advance，extractor 仍停在这一帧）
                    val current = buffer.capacity()
                    if (current >= MAX_BUFFER_SIZE) throw tooSmall
                    val grown = minOf(current * 2, MAX_BUFFER_SIZE)
                    buffer = ByteBuffer.allocateDirect(grown)
                    continue
                }
                if (size < 0) {
                    finished[pick] = true
                    continue
                }
                var time = ref.extractor.sampleTime
                if (!supportsBFrames) {
                    // 仅 API 24：MediaMuxer 还不支持 B 帧，只能把时间戳压成单调递增
                    if (time <= lastWritten[pick]) time = lastWritten[pick] + 1
                    lastWritten[pick] = time
                }

                info.offset = 0
                info.size = size
                info.presentationTimeUs = time
                info.flags = ref.extractor.sampleFlags
                muxer.writeSampleData(ref.target, buffer, info)
                ref.extractor.advance()
            }

            // 必须先 stop() 写出 moov，校验才有意义
            muxer.stop()
            muxerStopped = true

            // MediaMuxer 返回成功 ≠ 文件一定能播，这里再做一次轻量校验
            if (!verifyOutput(outputPath, expectVideo, expectAudio)) {
                File(outputPath).delete()
                return false
            }
            return true
        } catch (_: Throwable) {
            try {
                if (!muxerStopped) File(outputPath).delete()
            } catch (_: Throwable) {
                // 忽略
            }
            return false
        } finally {
            try {
                muxer?.release()
            } catch (_: Throwable) {
                // 忽略
            }
            for (extractor in extractors) {
                try {
                    extractor.release()
                } catch (_: Throwable) {
                    // 忽略
                }
            }
        }
    }

    /**
     * 封装后的轻量校验。
     *
     * 设计原则（这条很重要，之前踩过）：
     * **只有「能确证坏了」才判失败。拿不准的一律放过。**
     *
     * 因为调用方在校验失败时会把产物删掉——基于猜测删除用户花了半小时下载、
     * 刚封装好的文件，比放过一个可能有问题但多半能播的文件糟糕得多。
     */
    private fun verifyOutput(path: String, expectVideo: Boolean, expectAudio: Boolean): Boolean {
        val file = File(path)
        if (!file.exists() || file.length() < 1024) return false

        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            if (extractor.trackCount <= 0) return false

            var hasVideo = false
            var hasAudio = false
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) hasVideo = true
                if (mime.startsWith("audio/")) hasAudio = true
            }
            // 这两条才是真正有价值的检查：它们能抓到「只搬了第一条视频轨、
            // 音频被静默丢掉」这类封装错误，而且不会误判。
            if (expectVideo && !hasVideo) return false
            if (expectAudio && !hasAudio) return false

            hasReadableSample(extractor)
        } catch (_: Throwable) {
            false
        } finally {
            try {
                extractor.release()
            } catch (_: Throwable) {
                // 忽略
            }
        }
    }

    /**
     * 能否从输出文件里读出至少一个样本。
     *
     * 关键点：`readSampleData` 在缓冲不够时抛异常，而**抛异常恰恰证明样本是存在的**，
     * 只是当前缓冲装不下。所以这里把「缓冲不足」当成好消息处理，逐级放大后重试；
     * 真到放不下（超大关键帧）就直接认定通过，而不是把成品删掉。
     */
    private fun hasReadableSample(extractor: MediaExtractor): Boolean {
        val trackCount = extractor.trackCount
        if (trackCount <= 0) return false

        // 从 KEY_MAX_INPUT_SIZE 拿提示，拿不到就起步 1MB
        var capacity = 1 shl 20
        try {
            val format = extractor.getTrackFormat(0)
            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                capacity = maxOf(capacity, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
        } catch (_: Throwable) {
            // 拿不到提示不影响，用默认值
        }

        extractor.selectTrack(0)
        repeat(5) {
            try {
                return extractor.readSampleData(ByteBuffer.allocateDirect(capacity), 0) >= 0
            } catch (_: IllegalArgumentException) {
                // 缓冲装不下这个样本 —— 说明样本存在，放大后重试
                capacity *= 4
            } catch (_: Throwable) {
                return true
            }
        }
        return true
    }
}
