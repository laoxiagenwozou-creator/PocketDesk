# PocketDesk

PocketDesk 把兼容的 Android 手机变成蓝牙触控板、鼠标和键盘。它只发送输入，不传输远程画面，也不修改你使用的远程桌面软件。

它适合“手机控制平板或电脑”的场景：触控板移动指针，实体化的左右键和拖动锁处理点击与拖拽，键盘和扩展键发送快捷键，侧边滚动条与持续滚动拨杆则用于长页面。手机移动控制指针可以单独开关和调节灵敏度。

当前版本是公开测试版 `0.1.0-beta.1`。已验证的日常场景包括 OPPO Find N5 连接 OPPO Pad Mini，并配合 UU 远程使用；其他手机、主机和远程软件仍需分别测试。

[English](#english)

## 使用要求

- Android 9（API 28）或更新版本，设备必须支持 Android 的 Bluetooth HID Device 功能；仅有蓝牙并不保证兼容。
- 一台能接收蓝牙键鼠的电脑或平板。部分厂商系统可能限制手机作为 HID 设备或拒绝组合键鼠。
- 使用手机移动控制指针需要可用的陀螺仪与加速度传感器。
- 已在 OPPO Find N5 → OPPO Pad Mini → UU 远程的基本输入场景试用；不同手机、主机和远程软件需要分别验证。

## 安装与连接

1. 从 GitHub Releases 下载对应版本的 APK，并核对发布页给出的 SHA256。当前公开测试版为 `0.1.0-beta.1`。
2. 开启手机及目标设备的蓝牙，启动 PocketDesk 并授予附近设备权限。Android 系统可能还会询问通知权限。
3. 在目标设备的蓝牙设置中搜索并配对手机；如果找不到，点击 PocketDesk 顶部连接状态，在设备列表里选择“让电脑重新发现”。
4. 连接成功后，在触控板移动指针；右上角菜单可调整目标系统、速度并打开教程或诊断。

如果无法连接，先确认手机支持 HID Device、双方蓝牙已开启，并尝试在目标设备蓝牙设置中移除旧配对后重新配对。高级诊断中的 HID 描述符切换后，主机也必须删除原有配对再重新配对。

## 功能与限制

- 触控板支持单指移动、点击、双指滚动、鼠标按键和拖动锁；上方两侧的小圆环控制侧边滚动条。
- 菜单提供手机移动控制鼠标开关及独立灵敏度；此功能需要传感器，设备方向、稳定性和各主机表现仍需实测。
- 动作栏的扩展面板提供常用键、F1–F12 和持续滚动。切到后台或断开连接时应停止输入。
- 蓝牙 HID 发送按键位置，不能直接发送任意 Unicode；中文需要目标设备自己的输入法。横向滚动还取决于 HID 描述符和目标设备支持。
- 诊断页可查看连接状态；高级诊断包含设备信息和日志。分享前请自行检查日志中的设备名或地址等信息。
- 软件不提供远程画面串流，也不修改 UU 远程。具体功能、系统组合和手感以设备实测为准。

## 从源码构建

需要 JDK 17、Android SDK 34、Gradle 8.7；Android Gradle Plugin 8.5.2、Kotlin 2.0.21。联网环境下可使用 Gradle Wrapper：

```bash
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Windows 可使用 `gradlew.bat`。调试 APK 位于 `android/app/build/outputs/apk/debug/app-debug.apk`。可通过 `adb install -r <apk>` 更新同签名安装，覆盖安装不会主动清除应用数据；不同签名不能直接覆盖安装。

## 许可和贡献

PocketDesk 版权归 laoxiagenwozou-creator 所有，并以 GNU General Public License v3.0 授权。完整条款见 [LICENSE](LICENSE)。基于本项目分发的修改版本也必须以 GPL-3.0 提供相应源代码。[第三方许可与署名](THIRD_PARTY_NOTICES.md) 记录了独立适用的第三方组件。

贡献或反馈请附设备型号、Android 版本、目标系统、重现步骤和期望结果；分享诊断信息前请先检查其中的设备信息。数据处理方式见 [隐私说明](PRIVACY.md)。

正式签名通过未提交的 `android/keystore.properties` 配置。文件包含 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`，其中 `storeFile` 使用相对 `android/` 的路径。没有该文件时仍可构建调试包，但不能构建正式签名包。签名文件和密钥库都不得提交。

---

## English

PocketDesk turns a compatible Android phone into a Bluetooth touchpad, mouse, and keyboard. It sends input only: it does not stream the remote display or modify remote-desktop software.

Use the touchpad for pointer movement, the on-screen buttons and drag lock for clicks and dragging, and the keyboard or extra keys for shortcuts. Edge strips and the continuous-scroll lever handle long pages. Phone-motion pointer control has its own switch and sensitivity.

Version `0.1.0-beta.1` is a public beta. OPPO Find N5 to OPPO Pad Mini, including use through UU remote, is the tested daily setup. Other phones, hosts, and remote-desktop applications still need separate testing.

### Requirements and connection

Android 9 / API 28 or later and support for Android Bluetooth HID Device are required. The target computer or tablet must accept a Bluetooth keyboard and mouse; vendor restrictions may prevent a connection. Motion pointer control also needs a gyroscope and accelerometer. Basic input has been tried with OPPO Find N5 → OPPO Pad Mini → UU remote; other combinations need testing.

Install a trusted APK, turn on Bluetooth, open PocketDesk and grant the nearby-devices permission. Pair the phone from the host's Bluetooth settings. Tap the connection status and choose **Make discoverable** if the host cannot find the phone. The menu contains the target system, sensitivity, tutorial, diagnostics and language settings.

If connection fails, check HID support and Bluetooth, remove the old pairing on the host and pair again. Changing the HID descriptor also requires removing the old host pairing. A debug APK can only update an existing installation signed with the same key.

### Features and limitations

The touchpad supports pointer movement, clicks, two-finger scrolling, mouse buttons and drag lock. Small rings toggle the left and right scroll strips. The menu offers phone motion pointer control with a separate sensitivity. The Extra panel provides common keys, function keys and continuous scrolling.

HID sends key positions rather than arbitrary Unicode; use an input method on the host for Chinese. Horizontal scrolling depends on the descriptor and host. The diagnostics page can show device information and logs: review them before sharing. PocketDesk does not stream video or modify remote desktop software.

### Build and licensing

Use JDK 17, Android SDK 34 and Gradle 8.7, then run `cd android && ./gradlew assembleDebug testDebugUnitTest lintDebug` (Windows: `gradlew.bat`). The debug APK is at `android/app/build/outputs/apk/debug/app-debug.apk`.

PocketDesk is copyright laoxiagenwozou-creator and licensed under GPL-3.0. See [LICENSE](LICENSE). Modified versions distributed to others must also make their corresponding source available under GPL-3.0. Third-party components listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) retain their own licenses. See [privacy](PRIVACY.md) before sharing diagnostics.

Release signing reads `android/keystore.properties`, which must not be committed. It contains `storeFile` (relative to `android/`), `storePassword`, `keyAlias`, and `keyPassword`. A debug build works without it.
