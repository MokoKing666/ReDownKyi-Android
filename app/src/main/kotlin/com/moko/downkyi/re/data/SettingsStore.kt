package com.moko.downkyi.re.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moko.downkyi.re.core.model.SaveLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * 应用设置的真实存储。
 *
 * 本轮先接入「保存位置」—— 它直接决定下载产物的去向，是设置里
 * 唯一一个"改了立刻影响下一笔下载"的项。清晰度 / 编码 / 并发的
 * 修改 UI 与解析面板默认值的打通留待设置页重设计时一并做。
 */
class SettingsStore(private val context: Context) {

    private val saveLocationKey = stringPreferencesKey("save_location")
    private val defaultQnKey = stringPreferencesKey("default_qn")
    private val defaultCodecKey = stringPreferencesKey("default_codec")

    val saveLocationFlow: Flow<SaveLocation> =
        context.settingsDataStore.data.map { prefs ->
            SaveLocation.fromName(prefs[saveLocationKey])
        }

    /**
     * 默认清晰度（qn）。0 表示「智能选档」—— 解析页按分 P 可用档位自动选，
     * 这是默认值：多数用户不需要为每个视频手选清晰度。
     */
    val defaultQnFlow: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[defaultQnKey]?.toIntOrNull() ?: 0
    }

    /** 默认编码："auto" | "AVC" | "HEVC"。 */
    val defaultCodecFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[defaultCodecKey] ?: "auto"
    }

    suspend fun setSaveLocation(location: SaveLocation) {
        context.settingsDataStore.edit { prefs ->
            prefs[saveLocationKey] = location.name
        }
    }

    suspend fun setDefaultQn(qn: Int) {
        context.settingsDataStore.edit { prefs -> prefs[defaultQnKey] = qn.toString() }
    }

    suspend fun setDefaultCodec(codec: String) {
        context.settingsDataStore.edit { prefs -> prefs[defaultCodecKey] = codec }
    }
}
