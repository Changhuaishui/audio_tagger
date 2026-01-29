package com.example.tagger.data

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore 键定义
 */
object PreferencesKeys {
    /** 替换规则列表 (JSON 格式) */
    val REPLACEMENT_RULES = stringPreferencesKey("replacement_rules")

    /** 混淆映射记录 (JSON 格式) */
    val OBFUSCATION_MAPPINGS = stringPreferencesKey("obfuscation_mappings")

    /** 编号模式计数器 */
    val NUMBERED_COUNTER = intPreferencesKey("numbered_counter")

    /** 默认混淆模式 */
    val DEFAULT_OBFUSCATION_MODE = stringPreferencesKey("default_obfuscation_mode")

    /** 是否默认保存映射 */
    val SAVE_MAPPING_BY_DEFAULT = stringPreferencesKey("save_mapping_by_default")
}
