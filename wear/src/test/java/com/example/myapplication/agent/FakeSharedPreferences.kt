package com.example.myapplication.agent

import android.content.SharedPreferences

/**
 * 内存版 SharedPreferences：只覆盖 AgentPreferences 用到的布尔 / 字符串 / 数值读写。
 *
 * 单独成文件而不是内嵌在某个测试类里：同包的多个测试类都要用它，
 * 内嵌会导致"每个测试类各声明一份"的重复类型，或者被别处 import 时找不到。
 */
internal class FakeSharedPreferences(
    private val map: MutableMap<String, Any?> = mutableMapOf()
) : SharedPreferences {

    override fun getAll(): MutableMap<String, *> = map.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues

    override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = also { pending[key!!] = value }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = also { pending[key!!] = values }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = also { pending[key!!] = value }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = also { pending[key!!] = value }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = also { pending[key!!] = value }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = also { pending[key!!] = value }

        override fun remove(key: String?): SharedPreferences.Editor = also { removals += key!! }

        override fun clear(): SharedPreferences.Editor = also { clearAll = true }

        override fun commit(): Boolean {
            flush()
            return true
        }

        override fun apply() = flush()

        private fun flush() {
            if (clearAll) map.clear()
            removals.forEach { map.remove(it) }
            pending.forEach { (key, value) -> map[key] = value }
        }
    }
}
