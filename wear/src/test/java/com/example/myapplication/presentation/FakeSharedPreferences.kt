package com.example.myapplication.presentation

import android.content.SharedPreferences

/**
 * 纯 JVM 的内存版 [SharedPreferences]：单向服务 AgentPreferences 的 internal 接缝，
 * 让"记住并放行"能在单测里走真实的落盘/回读链路（真的编解码、真的 SharedPreferences 语义），
 * 只把 Android 平台的存储后端换成一张 Map —— 这是外部系统边界，不是被测逻辑。
 */
class FakeSharedPreferences : SharedPreferences {

    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String, defValue: String?): String? =
        values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    /** 一次 edit 内先攒改动，apply/commit 才真正落地 —— 与平台语义一致。 */
    private inner class Editor : SharedPreferences.Editor {

        private val pending = mutableMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = put(key, value)

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
            put(key, values?.toSet())

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = put(key, value)

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = put(key, value)

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = put(key, value)

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = put(key, value)

        override fun remove(key: String): SharedPreferences.Editor = put(key, null)

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearRequested) values.clear()
            pending.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
            pending.clear()
            clearRequested = false
        }

        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }
    }
}
