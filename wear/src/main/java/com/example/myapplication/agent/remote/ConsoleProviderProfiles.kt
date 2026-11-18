package com.example.myapplication.agent.remote

import com.example.myapplication.agent.ProviderProfile
import com.example.myapplication.agent.ProviderProfileBook

/**
 * 网页提交的供应商档案行 → 账本。
 *
 * 抽成纯函数的原因：`ProviderProfileBook.add()` 在「已经 5 套」或「id 重复」时**原样返回**，
 * 不报错。旧实现直接把它当成功，于是：
 * - 网页加第 6 套 → 200 + `ok:true` → 账本里只有 5 套，**第 6 套静默消失**；
 * - 两行同名 → `previous` 让它们拿到同一个 id → 第二行被 `add` 丢掉，同样静默。
 *
 * 这不是"再补一个返回码"能修的（0.0.0.95 就只改了返回码，红队一条请求即证伪）：
 * 必须在这里就地检测"这一行有没有真的进账本"。
 *
 * @return 重建好的账本；任何一行落不下去就返回 null，调用方据此回 `rejected`。
 */
internal fun rebuildProviderBookFromWebRows(
    entries: List<*>,
    existing: ProviderProfileBook,
    newId: () -> String,
    fallbackApiKey: String
): ProviderProfileBook? {
    var book = ProviderProfileBook()
    val seenNames = mutableSetOf<String>()
    for (entry in entries) {
        val row = entry as? Map<*, *> ?: return null
        val name = (row["name"] as? String)?.trim().orEmpty()
        if (name.isEmpty()) return null
        // 同一份提交里出现同名行：名字在界面上就是主键，两行同名等于要求"两条记录一个身份"，拒绝
        if (!seenNames.add(name)) return null
        // 同名视为同一套（网页编辑既有行时 id 必须沿用，否则 apiKey 会丢）
        val previous = existing.profiles.firstOrNull { it.name == name }
        val profile = ProviderProfile(
            id = previous?.id ?: newId(),
            name = name,
            baseUrl = (row["baseUrl"] as? String).orEmpty(),
            apiKey = previous?.apiKey ?: fallbackApiKey,
            modelName = (row["model"] as? String).orEmpty()
        )
        val next = book.add(profile)
        if (next.profiles.size == book.profiles.size) {
            // 满 5 套 / id 重复：这一行根本没进账本
            return null
        }
        book = next
    }
    return book
}
