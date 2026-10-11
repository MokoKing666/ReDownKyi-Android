package com.moko.downkyi.re.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.moko.downkyi.re.core.designsystem.component.AppText
import com.moko.downkyi.re.core.designsystem.component.AppTextSecondary
import com.moko.downkyi.re.core.designsystem.component.GlassTopBar
import com.moko.downkyi.re.core.designsystem.component.TopBarBackButton
import com.moko.downkyi.re.core.designsystem.glass.GlassScaffold
import com.moko.downkyi.re.core.designsystem.theme.ReTheme
import com.moko.downkyi.re.data.AppContainer
import kotlinx.coroutines.delay

/**
 * 扫码登录页。
 *
 * 链路：生成二维码 → 轮询状态 → 成功后访问 loginUrl 吸收 Cookie →
 * 落盘持久化 → 返回。全程状态文案跟随轮询变化。
 *
 * 为什么不用 B 站 App 授权登录：那条路需要 B 站 App 预装且 Intent 可达，
 * 模拟器与多数国行 ROM 上都不成立；二维码扫描是覆盖面最广的路径。
 */
@Composable
fun LoginScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
) {
    var qrKey by remember { mutableStateOf<String?>(null) }
    var qrUrl by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<LoginStatus>(LoginStatus.Loading) }

    // 生成二维码
    LaunchedEffect(Unit) {
        status = LoginStatus.Loading
        runCatching { container.api.qrcodeGenerate() }
            .onSuccess { pair ->
                if (pair != null) {
                    qrKey = pair.first
                    qrUrl = pair.second
                    status = LoginStatus.Waiting
                } else {
                    status = LoginStatus.Failed("二维码生成失败")
                }
            }
            .onFailure { status = LoginStatus.Failed(it.message ?: "网络错误") }
    }

    // 轮询扫码状态
    LaunchedEffect(qrKey) {
        val key = qrKey ?: return@LaunchedEffect
        while (true) {
            delay(2000)
            val (code, loginUrl) = runCatching { container.api.qrcodePoll(key) }
                .getOrNull() ?: continue
            when (code) {
                0 -> {
                    status = LoginStatus.Success
                    if (loginUrl != null) {
                        runCatching { container.api.absorbLoginCookies(loginUrl) }
                    }
                    container.persistLogin()
                    delay(500)
                    onLoggedIn()
                    return@LaunchedEffect
                }

                86038 -> {
                    status = LoginStatus.Expired
                    return@LaunchedEffect
                }

                86090 -> status = LoginStatus.Waiting
                86101 -> status = LoginStatus.Scanned
            }
        }
    }

    GlassScaffold(
        modifier = Modifier.fillMaxSize(),
        background = {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(Modifier.padding(top = 96.dp))
                AppText("扫码登录", style = ReTheme.typography.title1)
                AppTextSecondary("用 B 站手机 App 扫描下方二维码", style = ReTheme.typography.subheadline)

                Box(
                    Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        qrUrl != null -> QrCode(content = qrUrl!!, expired = status is LoginStatus.Expired)
                        status is LoginStatus.Loading || status is LoginStatus.Failed ->
                            CircularProgressIndicator(color = ReTheme.colors.accent, strokeWidth = 2.5.dp)
                    }
                }

                AppTextSecondary(
                    when (status) {
                        LoginStatus.Loading -> "正在生成二维码…"
                        LoginStatus.Waiting -> "请使用 B 站 App 扫码"
                        LoginStatus.Scanned -> "已扫描，请在手机上确认登录"
                        LoginStatus.Expired -> "二维码已过期，刷新重试"
                        is LoginStatus.Success -> "登录成功"
                        is LoginStatus.Failed -> (status as LoginStatus.Failed).message
                    },
                    style = ReTheme.typography.footnote,
                )

                if (status is LoginStatus.Expired) {
                    // 过期后点击重试：重置 key 触发重新生成
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ReTheme.colors.accent.copy(alpha = 0.14f))
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { qrKey = null; qrUrl = null; status = LoginStatus.Loading },
                            ),
                    ) {
                        AppText("刷新二维码", color = ReTheme.colors.accent, style = ReTheme.typography.subheadline)
                    }
                    LaunchedEffect(Unit) {} // 占位避免 unused import；重置后 LaunchedEffect(Unit) 不会重跑
                }
            }
        },
        overlay = { backdrop ->
            GlassTopBar(
                backdrop = backdrop,
                title = "账号登录",
                modifier = Modifier.align(Alignment.TopCenter),
                leading = { TopBarBackButton(onClick = onBack) },
            )
        },
    )
}

/** 二维码 Canvas 绘制（zxing 生成矩阵 → 黑白方格）。 */
@Composable
private fun QrCode(content: String, expired: Boolean) {
    val matrix = remember(content) {
        val hints = mapOf(EncodeHintType.MARGIN to 0)
        QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 256, 256, hints)
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .background(if (expired) Color.White.copy(alpha = 0.6f) else Color.White),
    ) {
        drawQr(matrix, expired)
    }
}

private fun DrawScope.drawQr(matrix: com.google.zxing.common.BitMatrix, expired: Boolean) {
    val w = matrix.width
    val h = matrix.height
    val cell = minOf(size.width / w, size.height / h)
    val offsetX = (size.width - cell * w) / 2
    val offsetY = (size.height - cell * h) / 2
    val black = SolidColor(Color.Black)
    for (y in 0 until h) {
        for (x in 0 until w) {
            if (matrix[x, y]) {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(offsetX + x * cell, offsetY + y * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }
    if (expired) {
        drawRect(
            color = Color.White.copy(alpha = 0.65f),
            topLeft = Offset(0f, 0f),
            size = size,
        )
    }
}

sealed interface LoginStatus {
    data object Loading : LoginStatus
    data object Waiting : LoginStatus
    data object Scanned : LoginStatus
    data object Expired : LoginStatus
    data object Success : LoginStatus
    data class Failed(val message: String) : LoginStatus
}
