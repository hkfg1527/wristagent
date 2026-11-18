package com.example.myapplication

/**
 * 源码切片的公共入口：**先自证锚点，再切片**。
 *
 * 为什么需要它：`String.substringAfter` / `substringBefore` 在找不到分隔符时会**原样返回**，
 * 于是一段"只该有几十行"的函数体切片会静默变成"从锚点到文件尾的几百行" ——
 * 后面的 `contains` 断言全部退化成"整个文件里出现过某串"，守卫看起来绿、其实什么都没守。
 * 本项目已经因此踩过三次（0.0.0.90 / 0.0.0.96 / 0.0.0.100 审查各点名一次）。
 */
object TestSlices {

    /**
     * 取 [start] 到 [end] 之间的源码。
     *
     * 三条自证：两个锚点都必须存在、`end` 必须在 `start` 之后、切片长度不得超过全文的一半
     * （超过就说明终点锚其实没命中，救回来的还是整段尾巴）。
     */
    fun slice(source: String, start: String, end: String, label: String = "slice"): String {
        require(source.contains(start)) { "$label：起始锚点缺失 → $start" }
        require(source.contains(end)) { "$label：结束锚点缺失 → $end" }
        val from = source.indexOf(start)
        val to = source.indexOf(end, from + start.length)
        require(to > from) { "$label：锚点顺序异常（$start 应在 $end 之前）" }
        val body = source.substring(from, to)
        require(body.length < source.length / 2) {
            "$label：切片过长（${body.length} / ${source.length}）—— 终点锚大概没命中"
        }
        return body
    }
}
