# Wear ADB Tool Box (Kadb Desktop, Windows)

基于 [Kadb](https://github.com/flyfishxu/Kadb) 的 Windows 桌面 ADB 工具箱 —— **无需安装 adb 二进制文件**，直连 `adbd`。界面使用 Material 3 Expressive（MD3E）风格，视觉模板移植自 [OpenSource / MD3-ClickGui-NextGen](https://github.com/khiet2wggga4412-hub/OpenSource)。

使用 Compose Multiplatform Desktop + Kotlin 构建。

## 功能（覆盖 Kadb 全部 API）

| 功能 | 对应 Kadb API | 说明 |
| --- | --- | --- |
| TCP 直连 | `Kadb.create(host, port, …)` / `tryConnection` | 手动输入地址连接；模拟器 `127.0.0.1:5555` |
| USB 直连（桌面 JVM） | `KadbUsb.devices()` / `KadbUsb.create()` | 无 adb server，独立 ADB 通道 |
| 无线调试发现 | `KadbMdnsJvm`（kadb-mdns） | mDNS 扫描 `_adb-tls-connect` / `_adb-tls-pairing` / `_adb` |
| 无线配对（Android 11+） | `Kadb.pair(host, port, code)` | 输入配对码（可自定义主机名） |
| Shell | `shell()` / `openShell()` | 一次执行 + 输出查看 |
| 交互终端 | `openPtyShellSession()` | PTY 终端（支持 resize、ANSI 基础解析）；无 shell_v2 时回退 legacy |
| 文件推送/拉取 | `openSync().send()/recv()`、`push()/pull()` | 双栏管理器，递归目录、进度条、statV2/listV2 |
| APK 安装 | `install()` / `installMultiple()` | 单包/拆分包，选项 `-r -g -d -t` |
| 卸载/包列表 | `uninstall()` + `pm list packages` | 用户/系统/全部 |
| 端口转发 | `tcpForward(hostPort, targetPort)` | 多条并行，可视化启停 |
| root / unroot | `root()` / `unroot()` | adbd 重启 |
| 传输复位 | `resetConnection()` | 断开会话复用传输 |
| 主机密钥管理 | `KadbCert.*` + `OkioFilePrivateKeyStore` | 指纹查看、轮换、导入/导出、持久化（`%APPDATA%\WearADBToolBox\kadb\adbkey`） |
| 连接选项 | `KadbOptions(delayedAckMode, …)` | 超时与 burst（delayed_ack）策略 |

## 使用

1. 在 **设备** 页连接：mDNS 扫描 / USB 枚举 / 手动 TCP；或先在 **配对** 中完成无线配对。
2. 新设备首次连接时需在设备屏幕上“允许调试”或“允许 USB 调试”。
3. 之后即可使用 终端 / 文件 / 应用 / 转发 / 日志(logcat) 各功能。

### USB 注意
- java-does-usb 桌面后端使用 FFM；若控制台出现 `--enable-native-access` 提示仅为警告，不影响使用。
- USB 打开接口时如被占用，请退出正在运行的 `adb server`。

## CI 构建（GitHub Actions）

推送 `main` 或手动 `workflow_dispatch` 会自动在 `windows-latest`（Temurin JDK 25）上执行
`gradlew packageForCurrentOS packageUberJarForCurrentOS`，并在 Actions 页面附件中提供：

- `WearADBToolBox-1.0.0.msi`（安装包，WiX）
- 便携 ZIP（jpackage app-image）
- Uber Jar（`java -jar` 运行，需 JDK 25）

给 tag 打 `v*` 标签将自动创建 Release 并上传以上三个产物。

## 构建/运行要求

- JDK 25（kadb-jvm 的字节码目标为 JVM 25）
- Gradle Wrapper（9.7.1，随仓库分发）
- Windows 10/11（x64）

## 致谢

- [flyfishxu/Kadb](https://github.com/flyfishxu/Kadb)（Apache-2.0）—— 多平台 ADB 客户端
- [khiet2wggga4412-hub/OpenSource](https://github.com/khiet2wggga4412-hub/OpenSource)（MD3-ClickGui-NextGen）—— Material 3 界面风格模板
- [JmDNS](https://github.com/jmdns/jmdns)、[java-does-usb](https://github.com/manuelbl/JavaDoesUsb)、[Conscrypt](https://github.com/google/conscrypt)、[Bouncy Castle](https://www.bouncycastle.org/)
