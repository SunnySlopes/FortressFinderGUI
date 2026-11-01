# FortressFinder

A GUI tool for searching Nether fortresses (crossings and large-span layouts) in **Minecraft Java Edition 1.18+**, powered by [cubiomes](https://github.com/xpple/cubiomes).

用于在 **Minecraft Java 版 1.18+** 中搜索下界要塞（十字路口 Cross / 大范围 Span）的 GUI 工具。

---

## Architecture / 架构

```
FortressFinder/                       # main module (this repo)
  src/main/java/.../FortressFinderFrame.java    # Swing UI
  src/main/java/.../FortressSearchRunner.java   # background search + progress
  src/main/java/.../FortressFinderBridge.java   # JNI contract (Java side)
  build.gradle                                  # packaging / contract checks

FortressFinderBase/                   # git submodule: all native computation
  fortress_finder.h / .cpp                      # C++ engine (cubiomes-based)
  fortress_cli.cpp                              # standalone --option CLI
  Thread.h                                      # thread pool / result container
  jni/                                          # fortressFinderLibJ.dll
  cubiomes/                                     # nested git submodule
  build.gradle                                  # JNI DLL + CLI artifacts
```

The main module no longer owns any C++ or cubiomes checkout. It builds the
`FortressFinderBase` subproject and consumes two artifacts:

| Artifact | Produced by | Used for |
| --- | --- | --- |
| `build/native/fortressFinderLibJ.dll` | `:fortressfinderbase:copyNativeArtifacts` | JNI queries from the Swing GUI, embedded into the fat JAR |
| `build/cli/fortressfinder-cli.exe` | `:fortressfinderbase:copyCliArtifact` | standalone command-line searches with `--out txt` |

`cubiomes` is now a nested submodule of `FortressFinderBase`; the main module
never checks out or compiles its own copy.

---

## Requirements / 环境要求

| 组件 | 版本 |
|------|------|
| JDK | **17+**（编译 Java、生成 JNI 头文件） |
| Gradle | 随项目 Wrapper（`gradlew`） |
| C/C++ 工具链 | Windows 推荐 **MSYS2 UCRT64**（`gcc`/`g++`）；CMake 3.15+ 仅在独立构建 base 时需要 |

MSYS2 安装示例：

```powershell
pacman -S mingw-w64-ucrt-x86_64-toolchain
```

---

## Submodules / 子模块

首次克隆后初始化主模块及其嵌套子模块：

```powershell
git submodule update --init --recursive
```

这会同时拉取：

- `FortressFinderBase/`（原生计算库）
- `FortressFinderBase/cubiomes/`（cubiomes 嵌套子模块）

如果只拉取了主仓库，请先执行上述命令再构建；`checkFortressBaseSubmodule`
会校验关键文件和 base 的 `cubiomes` 嵌套子模块，缺失时给出修复命令。

---

## Build / 编译打包

### 一键构建主模块 + 子模块

```powershell
.\gradlew buildDistribution --daemon
```

依次执行：

1. `checkFortressBaseSubmodule` — 校验子模块和 `cubiomes` 已连接；
2. `verifyJniContract` — 用 `javac -h` 重新生成 JNI 头并与 base 中签入的头文件比对；
3. `:fortressfinderbase:copyNativeArtifacts` — 构建并复制 `fortressFinderLibJ.dll`；
4. `:fortressfinderbase:copyCliArtifact` — 构建独立 CLI `fortressfinder-cli.exe`；
5. `buildMainJar` — 生成内嵌 DLL 的 fat JAR；
6. `collectNativeCli` — 把 CLI 复制到主模块的 `build/cli/`。

产物（分别输出，不打 zip）：

| 产物 | 路径 |
| --- | --- |
| GUI fat JAR | `build/libs/FortressFinder-1.0.5.jar` |
| 独立 CLI（主模块副本） | `build/cli/fortressfinder-cli.exe` |
| JNI DLL | `FortressFinderBase/build/native/fortressFinderLibJ.dll` |
| 独立 CLI（base 原始产物） | `FortressFinderBase/build/cli/fortressfinder-cli.exe` |

只构建某一部分：

```powershell
.\gradlew buildNative --daemon       # 只构建 DLL + JAR 所需的原生资源
.\gradlew buildNativeCli --daemon    # 只构建独立 CLI
.\gradlew verifyJniContract          # 只检查 JNI 契约
```

### 独立构建 FortressFinderBase

base 也可以完全脱离主模块构建，详见
[FortressFinderBase/README.md](FortressFinderBase/README.md)：

```powershell
cd FortressFinderBase
powershell -ExecutionPolicy Bypass -File build_cli_msys2.ps1
.\build\cli\fortressfinder-cli.exe --help

powershell -ExecutionPolicy Bypass -File build_jni_msys2.ps1
```

---

## Run / 运行

GUI：

```powershell
java -jar build\libs\FortressFinder-1.0.5.jar
```

或双击 JAR，选择 **Java 17+** 打开。

独立 CLI（不需要 Java / DLL）：

```powershell
# cross 模式，半径 5000，txt 结果写入 cross.txt
.\build\cli\fortressfinder-cli.exe --seed 12345 --mode cross --radius 5000 --out cross.txt

# span 模式
.\build\cli\fortressfinder-cli.exe --seed 12345 --mode span --min-long 100 --min-short 60 `
    --min-height 20 --center-x 1000 --center-z -1000 --radius 8000 --out span.txt

# 显式矩形
.\build\cli\fortressfinder-cli.exe --seed 12345 --mode cross --min-x -10000 --max-x 10000 `
    --min-z -10000 --max-z 10000 --out cross_rect.txt
```

CLI 的完整参数、txt 列格式与版本名见
[FortressFinderBase/README.md](FortressFinderBase/README.md)。

---

## Contract / 主子模块契约

### JNI

`FortressFinderBridge` 的 native 方法签名保持不变；base 的
`jni/generated/sunnyslopes_fortressfinder_FortressFinderBridge.h` 是签入的
Java 契约头文件。`verifyJniContract` 会用当前 Java 源码重新生成头文件并逐行
比对，发现 drift 时直接失败。

返回布局：

- cross 模式：每命中 6 个 int — `chunkX, chunkZ, x, y, z, shapeCode`；
- span 模式：每命中 8 个 int — `chunkX, chunkZ, x, y, z, spanX, spanY, spanZ`；
- 进度：`[current, total, phase1, status]`。

### CLI

base 的 `fortressfinder-cli` 使用 `--` 长参数，报告同时写 stdout；
`--out <file>` 额外写入 UTF-8 txt。它直接链接同一个 C++ 引擎和嵌套 cubiomes，
不依赖主模块或 DLL。

---

## Pause / resume / stop / 暂停、继续与停止

Native 层维护全局 pause/stop 标志；GUI 通过
`FortressFinderBridge.pause()` / `resume()` / `stop()` 控制，
`FortressSearchRunner` 负责 Java 侧的定时进度轮询。CLI 为一次性运行，
不提供交互式暂停。

---

## Troubleshooting / 常见问题

| 现象 | 处理 |
|------|------|
| `FortressFinderBase submodule is not initialized` | `git submodule update --init --recursive` |
| `cubiomes submodule is not initialized` | `git -C FortressFinderBase submodule update --init --recursive` |
| `MSYS2 gcc/g++ not found` | 安装 MSYS2 UCRT64，或设置 `MSYS2_ROOT` |
| `JNI contract drift` | 修改 `FortressFinderBridge.java` 后，把 `javac -h` 生成的头文件复制到 `FortressFinderBase/jni/generated/` |
| `UnsatisfiedLinkError: fortressFinderLibJ` | 先 `.\gradlew buildNative`，或用 `-Djava.library.path=FortressFinderBase\build\native` |
| `buildMainJar` 找不到 DLL | 查看 `:fortressfinderbase:copyNativeArtifacts` 的 MSYS2 输出 |

---

## Core libraries / 核心依赖

- [cubiomes](https://github.com/xpple/cubiomes) — 下界结构与群系生成（`FortressFinderBase/cubiomes` 嵌套子模块）
- 原生搜索逻辑在 [FortressFinderBase/fortress_finder.cpp](FortressFinderBase/fortress_finder.cpp)（子模块内维护）
