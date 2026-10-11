package com.moko.downkyi.re.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cookieDataStore by preferencesDataStore(name = "cookies")

/**
 * Cookie 持久化。
 *
 * 存的是 `header` 字符串（`k1=v1; k2=v2` 形式），与 [com.moko.downkyi.re.core.network.BiliCookies.setHeader]
 * 互逆 —— 登录成功后落盘，启动时读回 restore，重启不丢登录态。
 */
class CookieStore(context: Context) {
    private val appContext = context.applicationContext
    private val key = stringPreferencesKey("header")

    val headerFlow: Flow<String> = appContext.cookieDataStore.data.map { it[key] ?: "" }

    suspend fun load(): String = headerFlow.first()

    suspend fun save(header: String) {
        appContext.cookieDataStore.edit { it[key] = header }
    }

    suspend fun clear() {
        appContext.cookieDataStore.edit { it.remove(key) }
    }
}
