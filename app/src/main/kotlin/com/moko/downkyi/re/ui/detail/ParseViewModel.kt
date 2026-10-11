package com.moko.downkyi.re.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moko.downkyi.re.core.model.AudioOption
import com.moko.downkyi.re.core.model.DashInfo
import com.moko.downkyi.re.core.model.QualityOption
import com.moko.downkyi.re.core.model.QualityPlanner
import com.moko.downkyi.re.core.model.SaveLocation
import com.moko.downkyi.re.core.model.VideoCodec
import com.moko.downkyi.re.core.model.VideoDetail
import com.moko.downkyi.re.core.network.ApiException
import com.moko.downkyi.re.data.ParseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 解析结果页的状态。
 *
 * 分**两次加载**，因为真实链路本来就是两段：先拿详情（标题、UP 主、分 P 列表），
 * 再按所选分 P 的 cid 拿播放信息（档位、编码、音轨）。合成一个 loading 会让
 * "详情已经拿到了、只是档位还在转"这种状态在界面上完全没有表达 —— 用户看到
 * 的是一整屏空白，然后突然全部出现。
 */
data class ParseUiState(
    val loading: Boolean = false,
    /** 致命错误：详情都没拿到，页面只能显示失败与重试。 */
    val message: String? = null,
    val detail: VideoDetail? = null,
    val dash: DashInfo? = null,
    /** [dash] 属于哪个分 P。播放地址按 cid 取，切换分 P 必须重新请求。 */
    val dashPageIndex: Int = 0,
    val loadingDash: Boolean = false,
    /** 非致命问题：详情可用，但拿不到可下载的流（例如只有 durl 的老视频）。 */
    val dashMessage: String? = null,
    val options: List<QualityOption> = emptyList(),
    val audioOptions: List<AudioOption> = emptyList(),
    /** 体积预估用的时长（当前分 P 的）。放在状态里，避免界面再算一次而算错。 */
    val durationMs: Long = 0L,
)

/** 用户在解析结果页做出的选择。「开始下载」把它交给下载链路。 */
data class ParseSelection(
    val pageIndices: List<Int>,
    val qn: Int,
    val codec: VideoCodec?,
    val audioId: Int?,
    val flags: Int,
    val saveLocation: SaveLocation,
)

/**
 * 解析结果页的 ViewModel。
 *
 * 它只做三件事：发起解析、按分 P 取播放信息、把错误翻译成人话。
 * 「选档」这类纯计算交给 [QualityPlanner]（在 core:model 里，可单测）。
 */
class ParseViewModel(private val repository: ParseRepository) : ViewModel() {

    private val _state = MutableStateFlow(ParseUiState())
    val state: StateFlow<ParseUiState> = _state.asStateFlow()

    private var currentInput: String = ""
    private var job: Job? = null

    /**
     * 加载一个链接。
     *
     * 同一个链接重复进入不重复请求：从解析结果页返回首页、再点一次解析，
     * 是使用中最常见的动作，每次都重来会让人以为"这个软件每次都要重新解析"。
     */
    fun load(input: String, force: Boolean = false) {
        if (!force && input == currentInput && _state.value.detail != null) return
        currentInput = input
        job?.cancel()
        _state.value = ParseUiState(loading = true)

        job = viewModelScope.launch {
            val detail = try {
                repository.detail(input)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = ParseUiState(message = describe(e))
                return@launch
            }
            _state.value = ParseUiState(detail = detail)
            loadDash(detail, pageIndex = 0)
        }
    }

    fun retry() {
        if (currentInput.isNotEmpty()) load(currentInput, force = true)
    }

    /** 切换分 P。档位与音轨需要按新的 cid 重新取。 */
    fun selectPage(index: Int) {
        val detail = _state.value.detail ?: return
        if (index == _state.value.dashPageIndex && _state.value.dash != null) return
        job?.cancel()
        job = viewModelScope.launch { loadDash(detail, index) }
    }

    private suspend fun loadDash(detail: VideoDetail, pageIndex: Int) {
        _state.value = _state.value.copy(
            loadingDash = true,
            dashPageIndex = pageIndex,
            dashMessage = null,
        )

        try {
            val dash = repository.dash(detail, pageIndex)
            // 时长优先用播放信息里的：它是**这一路流**的时长，而详情里的
            // duration 在多 P 视频上是整部的时长，拿它估体积会大出好几倍
            val duration = dash.durationMs.takeIf { it > 0L } ?: pageDuration(detail, pageIndex)

            _state.value = _state.value.copy(
                loadingDash = false,
                dash = dash,
                durationMs = duration,
                options = QualityPlanner.options(dash, duration),
                audioOptions = QualityPlanner.audioOptions(dash, duration),
                dashMessage = if (dash.hasVideo) null else NO_STREAM_MESSAGE,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.value = _state.value.copy(
                loadingDash = false,
                dash = null,
                options = emptyList(),
                audioOptions = emptyList(),
                dashMessage = describe(e),
            )
        }
    }

    /**
     * 把异常翻译成人话。
     *
     * 直接把 `[-412] 请求被拦截` 丢给用户是没用的：他既不知道 -412 是什么，
     * 也不知道下一步该做什么。**每个错误都必须带走一个动作**（去登录 / 过一会儿再试 / 换个链接）。
     */
    private fun describe(error: Throwable): String = when (error) {
        is ApiException -> when {
            error.isRiskControl ->
                "被风控拦截（-412）。稍等片刻再试，或登录账号后重试"

            error.needLogin ->
                "需要登录后才能继续：${error.message}"

            error.code == ApiException.CODE_NETWORK ->
                "网络请求失败：${error.message}"

            error.code == ApiException.CODE_MALFORMED ->
                "接口返回的数据看不懂：${error.message}"

            else -> error.message
        }

        else -> error.message ?: "解析失败"
    }

    private fun pageDuration(detail: VideoDetail, pageIndex: Int): Long =
        detail.pages.getOrNull(pageIndex)?.durationMs?.takeIf { it > 0L } ?: detail.durationMs

    companion object {
        /** 有 DASH 结构但没有视频流：老视频的 durl 直链，暂不支持。 */
        private const val NO_STREAM_MESSAGE =
            "这个视频没有可用的 DASH 流（老视频只有 durl 直链），当前版本还下载不了"

        fun factory(repository: ParseRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ParseViewModel(repository) }
        }
    }
}
