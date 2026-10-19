# FrpTunnel

一个把 [frp-panel](https://github.com/VaalaCat/frp-panel) 客户端封装进 Android App 的小工具。

把一条 `frp-panel client ...` 命令粘贴进应用，点一下按钮，即可在内网穿透到公网，并在界面上实时查看运行日志。

## ✨ 特性

- **零外部依赖**：`frp-panel`（Go 静态编译的 `linux/arm64` 二进制）直接内嵌在 APK 里，安装即用，不需要 Termux、root 或任何本地文件。
- **粘贴即用**：支持整条命令（带 `./frp-panel` 前缀也行）、仅 `-s/-i`（自动补 `client` 与默认地址）等多种粘贴形式。
- **实时日志**：前台服务逐行读取子进程输出，自动剥离 ANSI 颜色码，按错误 / 成功着色并自动滚屏。
- **常驻保活**：以前台服务运行，避免后台被杀；随时可手动停止。

## 📱 使用

1. 安装 APK（目前仅支持 **arm64** 设备）。
2. 把收到的启动命令整条粘贴到输入框，例如：

   ```
   frp-panel client -s <client-secret> -i <client-id> --api-url http://server:9000 --rpc-url grpc://server:9001
   ```

3. 点击「打通隧道」，下方日志出现 `login to server success` / `start proxy success` 即表示隧道已建立。
4. 需要断开时点「停止」。

> 只有在 `local.properties` 或 CI Secrets 中配置了默认地址时，命令才可以省略 `--api-url` / `--rpc-url`。

## 🧱 工作原理

`frp-panel` 被放在 `app/src/main/jniLibs/arm64-v8a/libfrppanel.so`。Android 安装时会把原生库释放到可执行的 `nativeLibraryDir`，从而绕开 `/sdcard` 的 `noexec` 挂载以及应用私有目录的 W^X 限制。运行时以前台服务通过 `ProcessBuilder` 拉起该二进制，并实时转发输出。

## 🔒 隐私说明

仓库**不包含**任何私有服务器地址、客户端密钥或其他敏感信息：

- 默认服务地址不写死在代码里，而是构建时从本机 `local.properties`（已在 `.gitignore` 中）读取并注入 `BuildConfig`；公开仓库中该值为空。
- 使用时粘贴的命令中的服务器地址与密钥仅保存在设备本地，不会上传。

若在自己的 CI 中需要内置默认地址，可配置仓库 Secrets：

| Secret | 说明 |
| --- | --- |
| `FRP_DEFAULT_API_URL` | 默认 `--api-url`，例如 `http://host:9000` |
| `FRP_DEFAULT_RPC_URL` | 默认 `--rpc-url`，例如 `grpc://host:9001` |

## 🛠️ 构建

```bash
./gradlew assembleDebug     # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # 产物：app/build/outputs/apk/release/app-release.apk
```

- JDK 17+，Android SDK（compileSdk 35 / build-tools 35.0.0）。
- 可选：在 `local.properties` 中写入 `frp.defaultApiUrl` / `frp.defaultRpcUrl` 以内置默认地址。

推送到 `v*` 标签或手动触发 `workflow_dispatch` 时，GitHub Actions 会自动构建 release APK 并上传为产物（tag 构建还会附带发布到 Release）。

## 🩹 关于域名 / DNS

frp-panel 是 Go 静态二进制，使用纯 Go 的 DNS 解析器。它依赖 `/etc/resolv.conf`，
而 Android 既没有这个文件、也没有本地 DNS，因此**所有域名都会解析失败**
（报错形如 `lookup xxx on [::1]:53: read: udp ... connection refused`），只有 IP 地址能用。

本仓库已对内置二进制打了补丁：把其中硬编码的 DNS 回退地址 `127.0.0.1:53`
原地替换为公共 DNS `223.5.5.5:53`（等长替换，不改变文件结构）。
补丁脚本见 [`tools/patch_frp_panel_dns.py`](tools/patch_frp_panel_dns.py)，
升级 frp-panel 版本后需要重新执行。

## ⚠️ 限制

- 仅支持 **arm64-v8a** 设备。
- 该仓库体积较大（内嵌约 65 MB 的可执行文件）。
- 内置二进制为打过 DNS 补丁的版本，DNS 回退使用 `223.5.5.5`。
