#!/usr/bin/env python3
"""给 frp-panel（Go 静态二进制）打 DNS 回退补丁。

背景
----
frp-panel 使用 Go 的纯 Go DNS 解析器。Linux 上它会读取 /etc/resolv.conf；
文件不存在时会硬编码回退到 127.0.0.1:53 和 [::1]:53。
Android 系统里既没有 /etc/resolv.conf，也没有本地 DNS 监听，
于是所有「域名」都会解析失败（用 IP 则不受影响）。

补丁
----
把二进制中内嵌的默认 nameserver 字符串 "127.0.0.1:53" 原地替换为公共 DNS
"223.5.5.5:53"。两者都是 12 字节，因此可以直接原地覆盖，不改变文件结构。

用法
----
    python3 tools/patch_frp_panel_dns.py <原始二进制> <输出文件>

注意：升级 frp-panel 版本后需要重新执行本脚本。
"""

import sys

OLD = b"127.0.0.1:53"
NEW = b"223.5.5.5:53"

assert len(OLD) == len(NEW), "替换必须等长，否则会破坏二进制结构"


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2

    src, dst = sys.argv[1], sys.argv[2]
    with open(src, "rb") as f:
        data = bytearray(f.read())

    offsets = []
    start = 0
    while True:
        idx = data.find(OLD, start)
        if idx == -1:
            break
        offsets.append(idx)
        start = idx + 1

    if not offsets:
        print("未找到默认回退地址，可能已补丁过或版本不同")
        return 1

    for off in offsets:
        data[off:off + len(OLD)] = NEW
        print(f"patched offset {off}")

    with open(dst, "wb") as f:
        f.write(data)

    print(f"done: {src} -> {dst}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())