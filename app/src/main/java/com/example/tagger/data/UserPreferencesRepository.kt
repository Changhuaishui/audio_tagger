package com.example.tagger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.tagger.model.ObfuscationMapping
import com.example.tagger.model.ObfuscationMode
import com.example.tagger.model.ReplacementRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

// 扩展属性，创建 DataStore 实例
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * 用户偏好设置仓库 - 持久化存储替换规则和混淆配置
 */
class UserPreferencesRepository(private val context: Context) {

    // ==================== 替换规则 ====================

    /**
     * 获取所有替换规则
     */
    val replacementRulesFlow: Flow<List<ReplacementRule>> = context.dataStore.data.map { prefs ->
        val json = prefs[PreferencesKeys.REPLACEMENT_RULES] ?: "[]"
        parseReplacementRules(json)
    }

    /**
     * 获取当前替换规则（同步获取）
     */
    suspend fun getReplacementRules(): List<ReplacementRule> {
        return replacementRulesFlow.first()
    }

    /**
     * 添加替换规则
     */
    suspend fun addReplacementRule(rule: ReplacementRule) {
        context.dataStore.edit { prefs ->
            val currentRules = parseReplacementRules(prefs[PreferencesKeys.REPLACEMENT_RULES] ?: "[]")
            val updatedRules = currentRules + rule
            prefs[PreferencesKeys.REPLACEMENT_RULES] = serializeReplacementRules(updatedRules)
        }
    }

    /**
     * 删除替换规则
     */
    suspend fun deleteReplacementRule(ruleId: String) {
        context.dataStore.edit { prefs ->
            val currentRules = parseReplacementRules(prefs[PreferencesKeys.REPLACEMENT_RULES] ?: "[]")
            val updatedRules = currentRules.filter { it.id != ruleId }
            prefs[PreferencesKeys.REPLACEMENT_RULES] = serializeReplacementRules(updatedRules)
        }
    }

    /**
     * 更新规则启用状态
     */
    suspend fun toggleRuleEnabled(ruleId: String) {
        context.dataStore.edit { prefs ->
            val currentRules = parseReplacementRules(prefs[PreferencesKeys.REPLACEMENT_RULES] ?: "[]")
            val updatedRules = currentRules.map { rule ->
                if (rule.id == ruleId) rule.copy(isEnabled = !rule.isEnabled) else rule
            }
            prefs[PreferencesKeys.REPLACEMENT_RULES] = serializeReplacementRules(updatedRules)
        }
    }

    /**
     * 保存所有替换规则
     */
    suspend fun saveReplacementRules(rules: List<ReplacementRule>) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.REPLACEMENT_RULES] = serializeReplacementRules(rules)
        }
    }

    // ==================== 混淆映射 ====================

    /**
     * 获取所有混淆映射
     */
    val obfuscationMappingsFlow: Flow<List<ObfuscationMapping>> = context.dataStore.data.map { prefs ->
        val json = prefs[PreferencesKeys.OBFUSCATION_MAPPINGS] ?: "[]"
        parseObfuscationMappings(json)
    }

    /**
     * 添加混淆映射
     */
    suspend fun addObfuscationMapping(mapping: ObfuscationMapping) {
        context.dataStore.edit { prefs ->
            val currentMappings = parseObfuscationMappings(prefs[PreferencesKeys.OBFUSCATION_MAPPINGS] ?: "[]")
            val updatedMappings = currentMappings + mapping
            prefs[PreferencesKeys.OBFUSCATION_MAPPINGS] = serializeObfuscationMappings(updatedMappings)
        }
    }

    /**
     * 清除所有混淆映射
     */
    suspend fun clearObfuscationMappings() {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.OBFUSCATION_MAPPINGS] = "[]"
        }
    }

    // ==================== 编号计数器 ====================

    /**
     * 获取并递增编号计数器
     */
    suspend fun getAndIncrementCounter(): Int {
        var result = 1
        context.dataStore.edit { prefs ->
            val current = prefs[PreferencesKeys.NUMBERED_COUNTER] ?: 1
            result = current
            prefs[PreferencesKeys.NUMBERED_COUNTER] = current + 1
        }
        return result
    }

    /**
     * 重置编号计数器
     */
    suspend fun resetCounter() {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.NUMBERED_COUNTER] = 1
        }
    }

    // ==================== JSON 序列化/反序列化 ====================

    private fun parseReplacementRules(json: String): List<ReplacementRule> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ReplacementRule(
                    id = obj.getString("id"),
                    findText = obj.getString("findText"),
                    replaceWith = obj.getString("replaceWith"),
                    isEnabled = obj.optBoolean("isEnabled", true)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeReplacementRules(rules: List<ReplacementRule>): String {
        val array = JSONArray()
        rules.forEach { rule ->
            val obj = JSONObject().apply {
                put("id", rule.id)
                put("findText", rule.findText)
                put("replaceWith", rule.replaceWith)
                put("isEnabled", rule.isEnabled)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseObfuscationMappings(json: String): List<ObfuscationMapping> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ObfuscationMapping(
                    obfuscatedName = obj.getString("obfuscatedName"),
                    originalName = obj.getString("originalName"),
                    originalTitle = obj.optString("originalTitle", ""),
                    originalArtist = obj.optString("originalArtist", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeObfuscationMappings(mappings: List<ObfuscationMapping>): String {
        val array = JSONArray()
        mappings.forEach { mapping ->
            val obj = JSONObject().apply {
                put("obfuscatedName", mapping.obfuscatedName)
                put("originalName", mapping.originalName)
                put("originalTitle", mapping.originalTitle)
                put("originalArtist", mapping.originalArtist)
                put("timestamp", mapping.timestamp)
            }
            array.put(obj)
        }
        return array.toString()
    }
}
