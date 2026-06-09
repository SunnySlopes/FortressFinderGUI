# FortressFinder

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
FortressFinder/
  cubiomes/                                     # git submodule（cubiomes 源码）
  src/main/java/.../FortressFinderBridge.java   # JNI 接口
  src/main/java/.../FortressFinderFrame.java    # Swing UI
  src/main/java/.../FortressSearchRunner.java   # 后台搜索与进度
  fortress_finder_JNI.cpp                       # 要塞搜索 JNI 实现
  Thread.h
  jni/                                          # fortressFinderLibJ（显式编译 cubiomes/*.c）
  CMakeLists.txt                                # add_subdirectory(jni)
  build.gradle
```

**cubiomes** 以 git submodule 放在项目根目录 `cubiomes/`，由 `jni/CMakeLists.txt` 显式编译进 `fortressFinderLibJ`，不通过 FetchContent 或 `add_subdirectory`。

首次克隆后初始化 submodule：

```powershell
git submodule update --init --recursive
```

---

## Build guide / 编译打包指南

### Step 1 — Build native library / 编译原生库

**Windows（推荐：Gradle 一键打包）**

```powershell
# MSYS2 需已安装：gcc、g++、ninja、cmake（ucrt64 环境）
.\gradlew buildDistribution --daemon
```

等价于依次执行：`clean` → `checkNativeToolchain` → `buildNative` → `prepareNativeResources` → `buildMainJar`。  
输出 JAR：`build/libs/FortressFinder-1.0.1.jar`；中间产物：`build/native/fortressFinderLibJ.dll`。

仅编译原生库（不打 JAR）时：

```powershell
.\gradlew buildNative prepareNativeResources --daemon
```

> **注意**：`gradlew buildNative` 固定使用 **Ninja**。若你曾用手动 CMake 配置过同一 `build/` 目录，必须先删除缓存：
>
> ```powershell
> Remove-Item -Recurse -Force build -ErrorAction SilentlyContinue
> .\gradlew buildNative --daemon
> ```

**仅重新生成 JNI 头文件**（修改 `FortressFinderBridge.java` 后）：

```powershell
cmake --build build --target generate_jni_header
# 或
powershell -ExecutionPolicy Bypass -File scripts/generate-jni-header.ps1
```

### Step 2 — Run / 运行

```powershell
java -jar build\libs\FortressFinder-1.0.1.jar
```

或双击 JAR，选择 **Java 17+** 打开。

开发调试时也可指定本地 DLL：

```powershell
java -Djava.library.path=build\native -jar build\libs\FortressFinder-1.0.1.jar
```

---

## Pause / resume / stop / 暂停、继续与停止

### Native layer（`fortress_finder_JNI.cpp`）

- **Pause**：`FortressFinderBridge.pause()` sets `try_pause`; worker threads block in `checkProgress()` until resume or stop.
- **Resume**：clears `try_pause`, search continues.
- **Stop**：sets `try_stop`; search aborts and `fortressSearch` may return `null`.

### Java layer

`FortressSearchRunner` calls `FortressFinderBridge.pause()` / `resume()` / `stop()`.

---

## Troubleshooting / 常见问题

| 现象 | 处理 |
|------|------|
| `cubiomes not found` | 执行 `git submodule update --init --recursive` |
| `buildNative` / `cmake` exit value 1 | 向上滚动查看 **ninja/gcc 的真实报错**。执行 `.\gradlew buildNative --info` |
| `generator does not match` | 删除 `build/` 后重试 |
| `UnsatisfiedLinkError: fortressFinderLibJ` | 先 `buildNative` + `prepareNativeResources` + `buildMainJar`，或 `-Djava.library.path=build/native` |
| 未找到 gcc / ninja | 安装 MSYS2 UCRT64：`pacman -S mingw-w64-ucrt-x86_64-toolchain mingw-w64-ucrt-x86_64-cmake mingw-w64-ucrt-x86_64-ninja` |

---

## Core libraries / 核心依赖

- [cubiomes](https://github.com/Cubitect/cubiomes) — 下界结构与群系生成（根目录 submodule，显式编译）
- 原生搜索逻辑在 `fortress_finder_JNI.cpp`（项目内维护）
