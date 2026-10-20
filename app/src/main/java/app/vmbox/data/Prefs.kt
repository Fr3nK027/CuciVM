package app.vmbox.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

data class AppPrefs(
    val theme: String = "system",      // system | dark | light
    val style: String = "slate",       // slate | wallpaper
    val homeLayout: String = "pager",  // pager | grid
    val previewMode: Boolean = false,
)

class Prefs(private val context: Context) {
    private val theme = stringPreferencesKey("theme")
    private val style = stringPreferencesKey("style")
    private val layout = stringPreferencesKey("home_layout")
    private val preview = booleanPreferencesKey("preview_mode")

    val flow = context.store.data.map {
        AppPrefs(it[theme] ?: "system", it[style] ?: "slate", it[layout] ?: "pager", it[preview] ?: false)
    }

    suspend fun setTheme(v: String) = context.store.edit { it[theme] = v }
    suspend fun setStyle(v: String) = context.store.edit { it[style] = v }
    suspend fun setLayout(v: String) = context.store.edit { it[layout] = v }
    suspend fun setPreview(v: Boolean) = context.store.edit { it[preview] = v }
}
