# Controller Monorepo

本仓库统一管理 Android 控制器与挖机 WebView 应用：

```text
app/                       Android 应用
web/excavator-web-app/     React + Vite WebView 应用
```

Web 源码和 Android 集成代码应在同一个分支、Commit 和 PR 中管理，不再从其他仓库手工复制
`dist` 到 `app/src/main/assets`。

## 环境要求

- JDK 17
- Node.js 22（或满足当前 Vite 版本要求的 Node.js）
- Android SDK / ADB
- npm

命令行运行 Gradle 前先确认 Java 版本：

```bash
java -version
```

本项目的 Gradle 8.5 不能使用本机默认的 JDK 23。macOS 可在当前终端切换到 JDK 17：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
```

如果出现以下错误，说明终端仍在使用 JDK 23：

```text
Unsupported class file major version 67
```

Android Studio 中可以在 `Settings > Build, Execution, Deployment > Build Tools > Gradle >
Gradle JDK` 选择 JDK 17 或 Android Studio Embedded JDK。

## 构建变体

| 变体 | Web 来源 | 用途 |
| --- | --- | --- |
| `devWebDebug` | 本机 Vite `http://127.0.0.1:5173` | 日常前端和 Android 联调 |
| `bundledWebDebug` | APK 内嵌 assets | 测试离线加载、预热、缓存及打包结果 |
| `bundledWebRelease` | APK 内嵌 assets | 正式发布 |

`devWebRelease` 已禁用，防止发布依赖开发电脑的 APK。

## 日常开发

### 1. 安装前端依赖

首次拉取仓库或 `package-lock.json` 变化后执行：

```bash
cd web/excavator-web-app
npm ci
```

### 2. 启动 Vite

```bash
cd web/excavator-web-app
npm run dev
```

保持该终端运行。Vite 提供 HMR，修改 `web/excavator-web-app/src` 后设备页面会自动更新。
首次启动可能进行一次依赖预优化，页面会比后续加载稍慢。

### 3. 连接 Android 设备

USB 调试可直接使用 ADB。无线调试先连接设备：

```bash
adb connect <设备IP>:<无线调试端口>
```

例如：

```bash
adb connect 192.168.20.126:45365
```

然后把设备的 5173 端口反向转发到电脑：

```bash
adb reverse tcp:5173 tcp:5173
adb reverse --list
```

WebView 固定访问 `127.0.0.1:5173`，因此不需要修改 Java 代码或提交局域网 IP。设备重启、ADB
重连或无线调试端口变化后，需要重新执行 `adb connect` 和 `adb reverse`。

### 4. 运行开发变体

在 Android Studio 的 `Build Variants` 中选择：

```text
devWebDebug
```

也可以使用命令行构建并安装：在项目根目录执行

```bash
./gradlew :app:installDevWebDebug
```

手动进入app的设置页或其他页面查看效果，此时web页面是reactive的，可以直接编辑web/excavator-web-app 项目即可

开发变体依赖正在运行的 Vite 服务。如果显示空白页，依次检查：

```bash
adb devices -l
adb reverse --list
curl http://127.0.0.1:5173/
```

还可以在电脑 Chrome 中打开 `chrome://inspect` 调试 WebView，或在 Android Studio Logcat
中搜索 `chromium`、`CONSOLE`、`net::ERR`、`WebAppPreloader`。

## 测试 APK 内嵌 Web

在提交或部署前，应至少运行一次和生产环境加载方式一致的 `bundledWebDebug`：

```bash
./gradlew :app:installBundledWebDebug
```

也可以在 Android Studio 中选择 `bundledWebDebug` 后运行。

Gradle 会自动完成：

1. `package-lock.json` 变化时执行 `npm ci`。
2. 执行 `npm run build:android`。
3. 将 `dist` 同步到 `app/build/generated/webAssets`。
4. 将生成资源打入 APK。

生成的 `node_modules`、`dist`、`app/build` 和 generated assets 均不提交 Git。不要再把 `dist`
手工复制回 `app/src/main/assets/web/excavator-web-app`。

`build:android` 不会修改 `package.json`。WebView 构建版本由 package version 加源码哈希组成，
源码变化后会自动使 APK 内 WebView 缓存失效。

> 注意：前端遗留命令 `npm run build` 会自动增加 package patch 版本。Android 构建和日常验证
> 应使用 Gradle 或 `npm run build:android`，避免无意改动版本文件。

## 正式部署

### 1. 更新版本

发布前检查并更新 `app/build.gradle` 中的：

```groovy
versionCode 1
versionName '1.0'
```

`versionCode` 每次发布必须递增。Web 依赖变化时，同时提交 `package.json` 和
`package-lock.json`。

### 2. 构建 Release

```bash
./gradlew :app:assembleBundledWebRelease
```

输出目录：

```text
app/build/outputs/apk/bundledWeb/release/
```

当前项目尚未配置 Release keystore，默认产物为：

```text
app-bundledWeb-release-unsigned.apk
```

unsigned APK 无法直接安装或发布。正式部署前必须配置 `signingConfigs.release`，并通过安全的
本地环境变量或 CI Secret 提供 keystore 与密码，不要把 keystore 密码提交到仓库。

### 3. CI 部署原则

CI 使用本 Monorepo 的干净 checkout，直接运行：

```bash
./gradlew :app:assembleBundledWebRelease
```

CI 不需要拉取第二个前端仓库，也不需要复制 `dist`。建议在发布流水线中依次执行前端 lint、
Android 单元测试、Release 构建和签名，并保存最终 APK/AAB 及对应 Commit SHA。

## 提交注意事项

- Android 与 Web 的配套修改放在同一个 Commit/PR。
- Web 依赖变化必须提交 `package-lock.json`。
- 不提交 `node_modules`、`dist`、`app/build` 或手工复制的 assets。
- 提交前至少验证 `devWebDebug` 和 `bundledWebDebug`。
- 当前目标设备的系统 WebView 为 Chrome/WebView 101；引入新前端语法或升级依赖后，应在真机上
  重新测试兼容性。
