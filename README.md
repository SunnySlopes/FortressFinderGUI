# FortressFinderGUI

A GUI tool for searching Nether fortresses (crossings and large-span layouts) in **Minecraft Java Edition 1.18+**, powered by [cubiomes](https://github.com/Cubitect/cubiomes).

用于在 **Minecraft Java 版 1.18+** 中搜索下界要塞（十字路口 Cross / 大范围 Span）的 GUI 工具。

---

## Requirements / 环境要求

| 组件 | 版本 |
|------|------|
| JDK | **17+**（编译 Java、生成 JNI 头文件） |
| Gradle | 随项目 Wrapper（`gradlew`） |
| CMake | **3.15+** |
| C/C++ 工具链 | Windows 推荐 **MSYS2 MinGW-w64**；或 Visual Studio 2022（C++ 桌面开发） |

---

## Project layout / 项目结构

```
FortressFinderGUI/
  src/main/java/.../FortressFinderBridge.java   # JNI 接口
  src/main/java/.../FortressFinderFrame.java    # Swing UI
  src/main/java/.../FortressSearchRunner.java   # 后台搜索与进度
  fortress_finder_JNI.cpp                       # 要塞搜索 JNI 实现
  Thread.h
  jni/                                          # fortressFinderLibJ
  CMakeLists.txt                                # 引入 cmake/FetchCubiomes.cmake
  cmake/FetchCubiomes.cmake                     # 从 GitHub 拉取并编译 cubiomes_static
  build.gradle
```

**cubiomes** 来源（按优先级）：

1. **本地** `cubiomes/`（项目根目录，需含 `finders.c` 等源文件）— 离线打包、自定义版本时推荐  
2. 否则 CMake 从 GitHub 自动下载：https://github.com/Cubitect/cubiomes（固定 commit `e61f905`）→ `build/_deps/cubiomes-src/`

---

## Build guide / 编译打包指南

### Step 0 — 网络与 CMake / Network & CMake

无本地 `cubiomes/` 时，首次 configure 需能访问 GitHub。本地使用时将官方仓库克隆到 `cubiomes/` 即可，无需改 `GIT_TAG`。

### Step 1 — Build native library / 编译原生库

**Windows（推荐：Gradle 一键打包）**

```powershell
# MSYS2 需已安装：gcc、g++、ninja、cmake（ucrt64 环境）
# 与 build / buildAllJars 等价
.\gradlew buildDistribution --daemon
```

等价于依次执行：`clean` → `checkNativeToolchain` → `buildNative` → `prepareNativeResources` → `buildMainJar`（会先清空 `build/` 再完整重编）。  
输出 JAR：`build/libs/FortressFinder-1.0.0.jar`；中间产物：`build/native/libfortressFinderLibJ.dll`。

仅编译原生库（不打 JAR）时：

```powershell
.\gradlew buildNative prepareNativeResources --daemon
```

> **注意**：`gradlew buildNative` 固定使用 **Ninja**。若你曾按下方命令用过 **MinGW Makefiles** 或 **Visual Studio** 配置过同一 `build/` 目录，必须先删除缓存，否则会报 `generator does not match`：
>
> ```powershell
> Remove-Item -Recurse -Force build -ErrorAction SilentlyContinue
> .\gradlew buildNative --daemon
> ```

**Windows（手动 CMake，MinGW Makefiles）**

```powershell
# 与 Gradle 不要混用同一 build/ 目录，除非先 Remove-Item build
Remove-Item -Recurse -Force build -ErrorAction SilentlyContinue
cmake -B build -G "MinGW Makefiles" -DCMAKE_BUILD_TYPE=Release
cmake --build build --target fortressFinderLibJ
```

或使用预设（需已配置 `CMakePresets.json`）：

```powershell
cmake --preset mingw-release
cmake --build build --target fortressFinderLibJ
```

产物路径：

- Windows: `build/native/fortressFinderLibJ.dll`
- Linux: `build/native/libfortressFinderLibJ.so`

**Visual Studio**

```powershell
cmake -B build -G "Visual Studio 17 2022" -A x64
cmake --build build --config Release --target fortressFinderLibJ
```

**仅重新生成 JNI 头文件**（修改 `FortressFinderBridge.java` 后）：

```powershell
cmake --build build --target generate_jni_header
# 或
powershell -ExecutionPolicy Bypass -File scripts/generate-jni-header.ps1
```

### Step 2 — Package native into JAR resources / 打包进 JAR

`buildDistribution` 会自动把 `build/native/` 下的 DLL 收集进 JAR（路径 `native/windows/`）。仅当 **未** 执行 `buildNative`、需手动提供库时，才将 DLL 放到 `native/` 下作为后备来源（二者不会同时打入，避免重复）。

### Step 3 — Build executable JAR / 打可执行 JAR

```powershell
.\gradlew buildDistribution
# 或
.\gradlew build
.\gradlew buildAllJars
```

输出：`build/libs/FortressFinder-1.0.0.jar`（Main-Class：`sunnyslopes.fortressfinder.FortressFinderFrame`，内含 `native/windows/` 下的 DLL）。

**仅打 JAR（假定 DLL 已由 buildNative 生成）：**

```powershell
.\gradlew buildMainJar
```

### Step 4 — Run / 运行

```powershell
java -jar build\libs\FortressFinder-1.0.0.jar
```

或双击 JAR，选择 **Java 17+** 打开。

开发调试时也可指定本地 DLL：

```powershell
java -Djava.library.path=build\native -jar build\libs\FortressFinder-1.0.0.jar
```

---

## Pause / resume / stop / 暂停、继续与停止

### Native layer（`fortress_finder_JNI.cpp`）— implemented

- **Pause**：`FortressFinderBridge.pause()` sets `try_pause`; worker threads block in `checkProgress()` until resume or stop.
- **Resume**：clears `try_pause`, search continues.
- **Stop**：sets `try_stop`; search aborts and `fortressSearch` may return `null`.

### Java layer

`FortressSearchRunner` calls `FortressFinderBridge.pause()` / `resume()` / `stop()`. The UI shows “Remaining time: Paused” when `isPaused` is true (fortress progress has no `tryPause` index in the 4-int array).

### 中文摘要

- **原生**：协作式暂停，工作线程在 `checkProgress()` 检查点等待。
- **Java**：Runner 已接入 `FortressFinderBridge`；点击暂停后立即显示「已暂停」；已用时间扣除暂停时长。

---

## How to use / 使用说明

两个标签页：**单种子搜索**、**从种子列表搜索**。

### Parameters / 参数

| 参数 | 说明 | 默认 |
|------|------|------|
| 搜索模式 | Cross（十字路口）/ Span（大范围） | **Span** |
| Cross 过滤 | 二联/三联/四联（Double/Triple/Quad）→ 全部 / Triple+Quad / 仅 Quad | **二联 / Double** |
| Span 长边 / 短边下限 | 0–250；超出则提示并修正为 0 或 250 | **200** |
| MC 版本 | 1.18 / 1.19 / 1.20 / **1.21**（内部 `MC_1_21_3`） | **1.21** |
| MinX / MaxX / MinZ / MaxZ | 搜索矩形（块坐标），内部转换为中心点 + 半径 `r` | 见界面「重置」 |

Cross 过滤与 Span 边长在两个 Tab 中**始终显示**，不随搜索模式隐藏。

### Result format / 结果格式

- **Cross**：`/tp {x} {y} {z} 2crossings`（或 `3crossings` / `4crossings`）
- **Span**：`/tp {x} {y} {z} {longEdge}*{shortEdge}`

支持排序、导出；列表模式支持按形状/规模或距离排序。

---

## Troubleshooting / 常见问题

| 现象 | 处理 |
|------|------|
| `buildNative` / `cmake` exit value 1 | 向上滚动查看 **ninja/gcc 的真实报错**（Gradle 只显示最后一行）。执行 `.\gradlew buildNative --info` |
| `generator does not match`（Ninja vs MinGW Makefiles） | 删除 `build/` 后重试：`Remove-Item -Recurse -Force build`；不要混用手动 CMake 与 `gradlew buildNative` |
| `git-submodule` / `basename: command not found`（拉 cubiomes） | 确保已拉取最新代码（`GIT_SUBMODULES ""`）；或 `Remove-Item -Recurse -Force build` 后重试 |
| `UnsatisfiedLinkError: fortressFinderLibJ` | 先 `buildNative` + `prepareNativeResources` + `buildMainJar`，或 `-Djava.library.path=build/native` |
| 未找到 gcc / ninja | 安装 MSYS2 UCRT64：`pacman -S mingw-w64-ucrt-x86_64-toolchain mingw-w64-ucrt-x86_64-cmake mingw-w64-ucrt-x86_64-ninja`；运行 `.\gradlew checkNativeToolchain` |
| `No native directory found` | 未完成原生构建；完成 Step 1–2 |
| CMake 无法下载 cubiomes | 将 cubiomes 克隆到项目根 `cubiomes/`，或检查网络/代理后重试 |
| 点击暂停无效果 | 确认 `FortressSearchRunner` 调用 `FortressFinderBridge` |

---

## Core libraries / 核心依赖

- [cubiomes](https://github.com/Cubitect/cubiomes) — 下界结构与群系生成（CMake FetchContent，固定 master commit）
- 原生搜索逻辑在 `fortress_finder_JNI.cpp`（项目内维护）
