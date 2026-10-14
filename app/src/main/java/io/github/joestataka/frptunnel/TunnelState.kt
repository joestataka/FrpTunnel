package io.github.joestataka.frptunnel

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * UI 与服务共享的运行状态。服务和 Activity 处于同一进程，直接用一个单例持有即可。
 */
object TunnelState {

    private const val MAX_LINES = 3000

    /** 运行日志（已剔除 ANSI 颜色转义）。 */
    val logs = MutableStateFlow<List<String>>(emptyList())

    /** 隧道进程是否正在运行。 */
    val running = MutableStateFlow(false)

    /** 进程退出信息，null 表示仍在运行/尚未启动。 */
    val exitInfo = MutableStateFlow<String?>(null)

    @Synchronized
    fun append(line: String) {
        val clean = stripAnsi(line)
        val next = ArrayList<String>(logs.value.size + 1)
        next.addAll(logs.value)
        next.add(clean)
        logs.value = if (next.size > MAX_LINES) next.subList(next.size - MAX_LINES, next.size) else next
    }

    @Synchronized
    fun clear() {
        logs.value = emptyList()
    }

    /** 去除 ANSI 转义序列（frp-panel 日志带颜色码）。 */
    private fun stripAnsi(s: String): String {
        val noCsi = s.replace(Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]"), "")
        return noCsi.replace(Regex("\u001B[@-Z\\\\-_]"), "")
    }
}
