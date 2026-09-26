# ⌚ 小米表盘 ID 修改工具

> 一款运行在 **Android** 上的小米手环/手表表盘辅助工具，支持 **一键修改表盘 WatchFace ID** 与 **提取 Token / AuthKey**。

![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-green)
![Language](https://img.shields.io/badge/language-Kotlin-orange)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

## 📖 简介

基于 Kotlin + Jetpack Compose + Material 3 的原生 Android 应用，通过 Shizuku 或文件夹授权读取小米运动健康的数据目录，完成表盘 ID 覆写与设备密钥提取。全部操作本地完成，不联网上传任何数据。

## ✨ 功能

- 🔧 **表盘 ID 修改** — 自动定位 `.bin` / `.face` 文件中的 ID 字段并替换
- 🔑 **Token 提取** — 提取账号绑定设备的 AuthKey 与 MAC 地址
- 📲 **多设备适配** — 小米手环、Redmi Watch、Xiaomi Watch 等系列
- 🎨 **现代 UI** — Compose Material 3 界面，跟随系统深浅色
- 🚫 **纯本地运行** — 无广告、无联网、无数据采集

## 📱 环境要求

| 项目 | 要求 |
|---|---|
| 系统 | Android 7.0+ |
| 权限 | Shizuku 授权 或 SAF 文件夹授权 |
| 配套 App | 小米运动健康（已登录并绑定手环） |

## 📦 安装

前往 [Releases](https://github.com/wanfeng090525/XiaoMi-Clock-dial/releases) 下载最新 APK 安装即可。

自行构建：

bash
git clone https://github.com/wanfeng090525/XiaoMi-Clock-dial.git
cd XiaoMi-Clock-dial
./build.sh


产物输出在 `app/build/outputs/apk/` 目录下。

## 🚀 使用说明

### 一、修改表盘 ID

1. 在「小米运动健康」中下载任意一款官方免费表盘，随后删除；
2. 启动本工具，导入第三方表盘文件；
3. 点击 **自动检测 ID**，填入新 ID 后点击 **写入**；
4. 生成的表盘即可被小米运动健康识别推送。

> 💡 第三方表盘 ID 通常与官方冲突，替换为官方曾出现过且已被删除的表盘 ID 后才能正常同步。

### 二、提取 Token / AuthKey

1. 打开 **Token 提取** 页签，选择授权方式：
   - **Shizuku 授权**：启动 Shizuku 服务后授予权限；
   - **文件夹授权**：选中 `Android/data/com.mi.health` 目录授权。
2. 点击 **开始提取**，获取设备的 MAC 地址与 AuthKey：

| 字段 | 示例 |
|---|---|
| 设备名称 | Xiaomi Smart Band 9 |
| MAC 地址 | AA:BB:CC:DD:EE:FF |
| AuthKey | `a1b2c3d4e5f6...` |

3. 复制 AuthKey 到 Gadgetbridge、米坛表盘工具等客户端绑定即可。

> ⚠️ AuthKey 属于敏感凭据，请妥善保管，切勿泄露。

## ❓ FAQ

<details>
<summary><b>Shizuku 显示未启动？</b></summary>
Android 11+ 可通过无线调试激活；部分手机重启后需重新启动服务，或使用 Magisk 方案获得持久权限。
</details>

<details>
<summary><b>无法读取 Android/data 目录？</b></summary>
改用「文件夹授权」，在系统文件选择器中选中 com.mi.health 目录授予访问权限。
</details>

<details>
<summary><b>提取的 Key 不是最新的？</b></summary>
在手环端解绑后重新绑定一次，再回到工具重新提取。
</details>

<details>
<summary><b>推送后显示的还是原来的表盘名字？</b></summary>
正常现象 —— App 显示的是原官方表盘名，实际推送到设备上的已是你的第三方表盘。
</details>

<details>
<summary><b>会变砖吗？</b></summary>
不会。仅对表盘文件做字节级覆写，不涉及固件分区。建议操作前备份原文件。
</details>

## 🤝 贡献

欢迎提交 Issue / Pull Request，反馈时请附上设备型号、固件版本与问题截图。

## 📄 License

[MIT License](LICENSE)

## ⚠️ 免责声明

1. 本项目仅供学习研究与个人使用，禁止商业用途；
2. 相关操作可能违反小米服务条款，风险由使用者自行承担；
3. AuthKey 请妥善保管，谨防泄露造成安全隐患。
