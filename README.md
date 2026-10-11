# Re：哔哩下载姬（Re: Bilibili Downkyi）

以 Kotlin + Jetpack Compose 重写的 B 站视频下载器，界面采用 Apple HIG 风格的
Liquid Glass 设计语言（基于官方 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 实现）。

> 本应用仅提供视频解析与本地下载能力，不提供任何内容存储服务。
> 所有内容版权归原作者所有，仅供个人学习交流，请支持原始发布者。

## 功能

**解析与下载**
- 链接解析：BV 号 / av 号 / ep、ss 号 / 收藏夹 / 合集 / 短链，自动识别
- 清晰度与编码选择：智能选档（按分 P 可用档位）+ 手动指定，支持 AVC / HEVC / AV1 与 Hi-Res / 杜比音频
- 多分 P 批量下载，DASH 音视频分离下载后无损封装（MediaMuxer）
- 分片并发下载、断点续传（签名校验）、失败重试与 CDN 备用地址回退
- 封面 / 弹幕（XML）/ 字幕随任务一起落盘

**任务管理**
- 任务持久化：进程重启后未完成任务以「已暂停」恢复，分片与断点签名一并还原
- 暂停 / 继续 / 重试 / 删除，实时进度与体积显示
- 完成后发布到系统相册（MediaStore，两段式提交）或应用目录，支持一键打开

**媒体库**
- 扫描系统媒体库中本应用发布的文件（视频 / 音频 / 图片 / 弹幕 / 字幕），彩色类型徽章 + 体积统计
- 工具箱：缓存占用分析清理

**账号与浏览**
- 扫码登录（二维码轮询），登录态持久化（重启不丢）
- 站内搜索、观看历史、稍后再看、我的收藏夹

**界面**
- Liquid Glass：底部悬浮 Dock、下载页分段切换、主操作按钮
- Dock 玻璃气泡可拖动切换导航，导航项位置恒定
- 首页入口网格、下载页任务列表、媒体库文件列表、设置分组
- 浅色 / 深色主题，连续曲率圆角

## 技术架构

```
app/                    应用层：UI（Compose）、页面路由、数据装配
├── ui/                 首页 / 下载 / 媒体库 / 我的 + 二级页（解析、搜索、登录、工具箱…）
├── data/               仓储：解析、下载编排接线、媒体库扫描、设置与 Cookie 持久化
└── download/           封装（MediaMuxer）与成品发布（MediaStore / 应用目录）

core/
├── model/              纯数据模型：VideoDetail、Dash、DownloadFlags、MediaItem…
├── network/            B 站接口层：BiliApi / BiliHttp / WBI 签名 / Cookie / 解析器
├── download/           纯 JVM 下载引擎：SegmentDownloader（分片 / 续传 / 校验）、下载编排与任务持久化
└── designsystem/       设计系统：颜色 / 排版 / 形状 / 间距 token + Liquid Glass 组件
```

约定：
- `core:*` 不依赖 Android UI；`core:download` 为纯 JVM，可在无设备环境下跑单测
- 分层规则详见各模块源码注释（每一处设计取舍都有说明）

## 构建与运行

要求：
- JDK 17+（构建脚本会优先使用 `JAVA_HOME`，未设置时回退到脚本内的默认路径）
- Android SDK：`compileSdk 37` / `minSdk 33`
- 在 `local.properties` 中填写 `sdk.dir`（该文件不入版本库）

```bash
# 构建 debug APK
./gradlew :app:assembleDebug        # Windows: gradlew.bat :app:assembleDebug

# 单元测试（下载引擎与模型层，纯 JVM）
./gradlew :core:download:test :core:model:test
```

### 真机辅助脚本（Windows）

仓库根目录保留了开发期使用的脚本，便于真机验证：

| 脚本 | 用途 |
|---|---|
| `build.bat` | 编译并输出 APK 路径 |
| `verify.bat` | 构建 → 安装（绕过部分 ROM 的安装拦截）→ 启动 → 截图 |
| `shot.bat` / `crop.ps1` | 截图与裁剪 |
| `dumpui.ps1` | 解析 uiautomator 控件树，打印文字与坐标（自动化定位用） |
| `testreport.ps1` / `failures.ps1` | 汇总单测结果 |

## 开源依赖与致谢

| 依赖 | 用途 | 许可证 |
|---|---|---|
| [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（`io.github.kyant0:backdrop`） | Liquid Glass 渲染（背景采样 / 折射 / 高光 / 阴影） | Apache-2.0 |
| [kyant/shapes](https://github.com/Kyant0/Shapes)（`io.github.kyant0:shapes`） | 连续曲率圆角 | Apache-2.0 |
| [Coil 3](https://github.com/coil-kt/coil) | 列表封面异步加载 | Apache-2.0 |
| [ZXing](https://github.com/zxing/zxing)（core） | 登录二维码生成 | Apache-2.0 |
| AndroidX / Jetpack Compose / OkHttp / kotlinx.serialization / DataStore | 基础框架 | Apache-2.0 |

`core/designsystem/.../liquidglass/` 下为 AndroidLiquidGlass 官方示例组件的移植版本，
文件头保留了原始出处、许可证与移植改动说明（仅包名、主题色参数化与两处对齐修正）。

## 第三方名称声明

本项目为个人学习作品，与哔哩哔哩（bilibili）官方无关。
「哔哩哔哩」「Bilibili」等名称与商标归其所有者所有。

## 许可证

[Apache License 2.0](LICENSE)
