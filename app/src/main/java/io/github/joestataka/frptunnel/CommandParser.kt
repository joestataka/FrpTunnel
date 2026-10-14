package io.github.joestataka.frptunnel

/**
 * 把用户从网页/文档里粘贴的一条 frp-panel 命令，解析成可以直接喂给内置二进制的参数列表。
 *
 * 支持的粘贴形式：
 *  - ./frp-panel client -s xxx -i Joe --api-url http://... --rpc-url grpc://...
 *  - frp-panel client -s xxx -i Joe
 *  - /data/local/tmp/frp-panel client -s xxx -i Joe
 *  - client -s xxx -i Joe
 *  - -s xxx -i Joe                 （自动补 client 与默认 url）
 */
object CommandParser {

    // 可选的默认服务器地址，由构建时注入（见 app/build.gradle.kts）。
    // 公开仓库中为空字符串，因此不会泄露任何私有服务器信息；
    // 使用者可在 local.properties 或 CI Secrets 中自行配置。
    private val DEFAULT_API_URL = BuildConfig.FRP_DEFAULT_API_URL
    private val DEFAULT_RPC_URL = BuildConfig.FRP_DEFAULT_RPC_URL

    data class Result(val args: List<String>, val error: String? = null)

    fun parse(raw: String): Result {
        val normalized = raw.trim().replace('\r', ' ').replace('\n', ' ')
        if (normalized.isEmpty()) return Result(emptyList(), "命令为空，请先粘贴指令")

        val tokens = tokenize(normalized).toMutableList()
        if (tokens.isEmpty()) return Result(emptyList(), "命令为空，请先粘贴指令")

        // 1) 定位真正的子命令 client，丢弃它前面的二进制路径等噪音
        val clientIdx = tokens.indexOfFirst { it == "client" }
        if (clientIdx >= 0) {
            repeat(clientIdx) { tokens.removeAt(0) }
        } else {
            // 没有 client：若首个 token 不像参数（不以 '-' 开头），说明它是二进制名/路径，丢弃
            if (tokens.isNotEmpty() && !tokens[0].startsWith("-")) tokens.removeAt(0)
            tokens.add(0, "client")
        }

        // 2) 没有显式指定 api/rpc 地址时，补上构建时注入的默认地址（若存在）
        if (!hasAnyFlag(tokens, "--api-url", "-t", "--api-host")) {
            if (DEFAULT_API_URL.isBlank()) {
                return Result(emptyList(), "命令缺少 --api-url，且当前构建未内置默认地址")
            }
            tokens.add("--api-url")
            tokens.add(DEFAULT_API_URL)
        }
        if (!hasAnyFlag(tokens, "--rpc-url", "-r", "--rpc-host")) {
            if (DEFAULT_RPC_URL.isBlank()) {
                return Result(emptyList(), "命令缺少 --rpc-url，且当前构建未内置默认地址")
            }
            tokens.add("--rpc-url")
            tokens.add(DEFAULT_RPC_URL)
        }

        // 3) 必填项校验
        if (!hasAnyFlag(tokens, "-s", "--secret")) {
            return Result(emptyList(), "缺少客户端密钥：-s/--secret")
        }
        if (!hasAnyFlag(tokens, "-i", "--id")) {
            return Result(emptyList(), "缺少客户端 ID：-i/--id")
        }

        return Result(tokens)
    }

    private fun hasAnyFlag(tokens: List<String>, vararg flags: String): Boolean =
        tokens.any { token -> flags.any { f -> token == f || token.startsWith("$f=") } }

    /** 简易分词：按空白切分，同时支持单/双引号包裹的参数。 */
    private fun tokenize(input: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var quote = ' '
        for (c in input) {
            when {
                quote != ' ' -> if (c == quote) quote = ' ' else cur.append(c)
                c == '\'' || c == '"' -> quote = c
                c == ' ' || c == '\t' -> if (cur.isNotEmpty()) { out.add(cur.toString()); cur.clear() }
                else -> cur.append(c)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }
}
