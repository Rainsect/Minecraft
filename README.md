# AutoMC

**把「一串操作」做成可复用的自动化项目 —— Minecraft 1.12.2 · Forge · 纯客户端**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.12.2-3C8527?logo=minecraft&logoColor=white)](#环境要求)
[![Forge](https://img.shields.io/badge/Forge-14.23.5.2847-f0933b)](#环境要求)
[![Side](https://img.shields.io/badge/side-client--only-4c8eda)](#它是什么)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

按一下按键，自动发消息、自动走路、自动点界面、自动交任务——不用记命令，不用写脚本，像搭积木一样把动作排成一条流程。

> 当前最新版本：**v1.2.10**，从 [Releases](https://github.com/Rainsect/Minecraft/releases) 下载。

---

## 它是什么

AutoMC 是一个**纯客户端**的自动化模组。一个「项目」= 一串按顺序执行的动作；项目可以挂事件自动触发（进服、定时、血量低于…），也可以绑一个按键手动触发。

```
项目：自动卖货
├─ 右键            ← 打开面前的箱子 / 商店界面
├─ 延迟 1000ms
├─ 点击坐标        ← Shift+左键 点界面上的指定位置，把物品卖掉
├─ 延迟 500ms
└─ ESC            ← 关掉界面（按键动作）
```

- **不改服务端、不发额外封包**，所有操作都是模拟你本人在点鼠标 / 敲键盘
- 支持条件判断、循环、跳转、并行，能做稍微复杂的逻辑
- 界面全自绘，支持半透明与主题色，低分辨率窗口自动切紧凑布局

> 服务端不需要安装本模组；也正因如此，操作速度受服务端反作弊与网络延迟影响，请自行把握节奏（见[已知限制](#已知限制)）。

## 功能一览

| 模块 | 说明 |
|---|---|
| 项目列表 | 卡片式管理，每个项目可单独启用 / 禁用、绑定按键、运行 |
| 动作编辑 | 24 种动作（动作 / 流程 / 移动 三类各 8 种），行内 ↑↓ 排序，可单独禁用某一行动作 |
| 事件触发 | 18 种事件，进服 / 定时 / 血量 / 背包 / 坐标 / 聊天关键词…任一命中即启动项目 |
| 条件表达式 | `血量<10`、`物品:diamond>=64`、`距离:100,64,200<=5` 这类表达式 |
| 点击坐标 | 在窗口指定像素位置点一下，全部走游戏内部，支持 Shift+左键 / 右键，可「⌖ 拾取坐标」自动填参 |
| 按键绑定 | 每个项目绑一个键，游戏里按一下即执行 |
| 界面设置 | 不透明度 35–100%、8 种主题色（蓝绿紫橙青粉黑灰）、实时预览 |

## 环境要求

| 项 | 版本 |
|---|---|
| Minecraft | **1.12.2** |
| Forge | **14.23.5.2847**（其他 1.12.2 版 Forge 一般也可运行） |
| OptiFine | 可选 |
| Java | 8 |

其他游戏版本未适配。

## 安装

1. 安装 Minecraft 1.12.2 与 Forge 14.23.5.2847
2. 从 [Releases](https://github.com/Rainsect/Minecraft/releases) 下载 `automc-<version>.jar`，放进 `.minecraft/mods/`
3. 启动游戏，进入世界后按 **F8** 打开界面

首次启动会在 `config/automc.json` 生成配置；旧版的 `autocommandzh.json` / `endframe.json` 会自动迁移。

## 三分钟上手

1. **F8** 打开列表 → 点「＋ 新建项目」
2. 在编辑器里点「＋ 添加动作」，选一个动作（比如「发送聊天」），在底部参数区填内容
3. 回到列表，点卡片右侧「开关」启用，再点「运行」——或者给它绑一个按键

需要「进服就自动跑」的话，在动作列表最上面加一条事件（如「进服」），事件命中时整个项目会自动启动。

> 「完成」按钮才会把编辑结果写回配置；编辑器里直接按 ESC 等于放弃本次修改。

## 事件（18 种）

| 事件 | 触发条件 |
|---|---|
| 进服 / 死亡 | 进入世界、血量归零 |
| 定时 | 每 N 秒 |
| 血量低于 / 饥饿低于 | 数值阈值 |
| 背包物品 | 指定物品达到数量 |
| 经验等级 | 达到等级 |
| 切换维度 / 受伤 / 手持变化 | 状态变化时 |
| 聊天含关键词 | 收到含指定词的聊天 |
| 附近掉落物 / 附近玩家 | 范围内出现目标 |
| 到达坐标 / Y 低于 | 位置条件 |
| 游戏时间 | 游戏内时刻（如 13000 夜晚） |
| 耐久偏低 | 手持物品耐久低于百分比 |
| 界面状态 | 背包 / 箱子 / 商贩 / 聊天等界面打开时触发，可填类名，沿触发或持续 |

## 动作（24 种）

| 分类 | 动作 |
|---|---|
| 动作 | 发送消息、按键、点击坐标、攻击最近实体、切换快捷栏、丢弃手中、使用手中物品、自动钓鱼 |
| 流程 | 延迟、循环开始、循环结束、跳转、等待条件、结束脚本、并行开始、并行结束 |
| 移动 | 走到坐标、走到方块、捡掉落物、停止移动、跳跃、潜行、转向、看向最近玩家 |

### 点击坐标怎么用

在**当前窗口**的指定像素位置点一下（左上角为 `(0,0)`，按窗口像素填，不乘 GUI 缩放）：

| 参数 | 选项 |
|---|---|
| X / Y | 窗口像素坐标 |
| 按钮 | `左键` ／ `右键` ／ `Shift+左键` ／ `Shift+右键` |

- 全部走游戏内部：世界里没有打开界面时按原版左/右键逻辑；容器界面点在槽位上时走 `windowClick`（Shift+左键 = 快速移动）；其它界面调用界面自己的点击处理
- 不碰系统键鼠，窗口在前台还是后台行为一致，也不会影响其他程序
- 编辑器右上角点「**⌖ 拾取坐标**」，在背包 / 箱子 / 商人等界面上点一下目标位置，坐标会自动填回参数

## 条件表达式

用在「跳转」「等待条件」里：

```
血量<10          饥饿<=6          经验>=30
y<0              时间>=13000
物品:diamond>=64
距离:100,64,200<=5
手持耐久<10
```

比较符：`>` `<` `>=` `<=` `=` `==` `!=`

## 快捷键与命令

| 操作 | 说明 |
|---|---|
| `F8` | 打开界面（只负责打开，关闭按 ESC） |
| `ESC` | 编辑器里 = 放弃本次修改；列表里 = 关闭界面 |
| 项目绑定键 | 编辑器右上角点「绑定按键」→ 按任意键；右键该按钮 = 解除绑定 |
| `/amc gui` | 打开界面 |
| `/amc list` | 列出项目 |
| `/amc run <序号\|项目名>` | 运行项目 |
| `/amc stop` / `/amc status` | 停止 / 状态 |
| `/amc npc` / `/amc slot` | 附近 NPC / 鼠标悬停槽位号 |

## 从源码构建

需要 **JDK 8**（ForgeGradle 2.3 不支持更高版本）。

```bash
git clone https://github.com/Rainsect/Minecraft.git
cd Minecraft
./gradlew build          # Windows: gradlew.bat build
```

产物在 `build/libs/automc-<version>.jar`。

- 首次构建会下载 Minecraft 1.12.2、Forge 与 MCP 映射，耗时较长
- 在 IDE 里开发：先 `./gradlew setupDecompWorkspace`，再导入工程
- 运行游戏调试：`./gradlew runClient`
- 版本号、目标 Forge 版本、MCP 映射都在 [`gradle.properties`](gradle.properties) 里改
- 「走到坐标 / 走到方块」想用 Baritone 寻路的话，把 `baritone-api-forge-1.2.19.jar` 放到 `libs/`；没有这个 jar 也能正常编译，只是运行时回退到内置走路

## 项目结构

```
AutoMC/
├─ src/main/java/com/rain/automc/     源码（MCP 名，可直接编译）
│  ├─ AutoMCMod.java                  模组入口，注册事件总线
│  ├─ ClientTickHandler.java          客户端主循环：F8、引擎与事件侦测的驱动
│  ├─ Engine.java                     脚本解释器：逐行执行动作，并行段按 Lane 同时跑
│  ├─ ScriptChain / ScriptAction      项目与动作的数据模型
│  ├─ EventScanner.java               事件侦测：轮询与即时分支
│  ├─ Cond.java                       条件表达式求值
│  ├─ Catalog.java                    动作 / 事件的唯一定义源
│  ├─ ConfigManager.java              配置读写与旧版迁移
│  ├─ PlayerActions.java              动作原语：移动、视角、物品
│  ├─ ClickActions.java               左右键、快捷栏
│  ├─ AutoInput.java / KeyRouter.java 按键模拟与路由：直接走游戏内 API，前后台一致
│  ├─ ScreenClicker.java              「点击坐标」：槽位 windowClick + 界面点击反射
│  ├─ ClickPicker.java                编辑器里的「⌖ 拾取坐标」
│  ├─ AutoFishing.java                自动钓鱼：抛竿、等鱼漂下沉、收竿
│  ├─ Walker.java / EFWalker.java     内置行走模块
│  ├─ BaritoneWalker.java             装了 Baritone 时交给 Baritone 寻路
│  ├─ NpcTrader.java                  物品识别与附近 NPC 信息（/amc npc）
│  ├─ AMCClientCommand.java           /amc 命令
│  ├─ GuiBase.java                    自绘控件基类
│  └─ GuiAutoMC / GuiProject / GuiPicker / GuiSettings   四个界面
├─ src/main/resources/mcmod.info      模组元信息
├─ build.gradle / gradle.properties   构建脚本与版本参数
└─ gradlew / gradlew.bat              Gradle Wrapper
```

## 已知限制

- 只做**客户端模拟操作**，操作速度受服务端反作弊与延迟影响，建议动作之间留 300ms 以上延迟
- 「点击坐标」按窗口像素取位，窗口大小改变后需重新拾取
- 界面按 854×480（GUI 缩放 2）设计，更小的逻辑分辨率会自动切紧凑布局
- 1.12.2 Forge 专用，未适配其他版本
- 循环嵌套上限 16 层，并行段上限 16 条

## 更新日志

各版本更新内容见 [Releases](https://github.com/Rainsect/Minecraft/releases) 中对应版本的说明。

## 许可证

[MIT](LICENSE)

## 免责声明

仅供个人学习与单机 / 私人服务器使用。请遵守所在服务器的规则，因使用本模组导致的封禁或损失由使用者自行承担。
