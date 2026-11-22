# TIMEMANAGER_BETA1_PREPARATION_REPORT

日期为 2026-11-16。Beta 1 功能冻结，分支为 feat/todo-experience。Owner 自行完成安装和真机验收，当前未宣称真机通过。

## Source state

开始 HEAD 为 f708dc0，工作区包含已交付的 UI 与图标改动，暂存区为空。所有改动均可对应此前任务，未发现未知文件。Phase 1～5 的已有提交保持不变。

本地 Beta checkpoint 收录本报告与现有改动，完整提交 ID 随项目外 Beta APK 的 build-info 文件记录。提交后再执行 assembleDebug 核对相同源码状态，APK 不进入 Git。

## 本次整理范围

- 保留 Phase 1～5 的已提交功能，检查现有 WorkManager 任务的五分钟准备、实际开始后的三十分钟静心、唯一任务、取消、权限和模式冲突保护。
- 收录首页模式入口、待办标题、统计标题的功能 PNG 与共享 FeatureArtwork。
- 收录新版暂停 PNG、Session 两个次要按钮的自适应并排布局、保持原结构的确认页莲花替换。
- 收录已确认 Launcher 母版、五档传统密度图标、API 26+ 自适应资源、相关渲染测试与既有实施文档。
- 新增 [owner 真机清单](BETA1_DEVICE_ACCEPTANCE.md) 与 [问题模板](BETA1_ISSUES.md)，初始结果均为未测试。
- 本次准备没有修改运行源码、业务规则、版本、签名或构建配置，也没有新增功能。

## 本轮重新执行的验证

执行 :app:testDebugUnitTest --rerun :app:assembleDebug --console=plain，实际重新运行全部 Debug 测试任务。

结果为 654 tests / 69 suites，failures 0、errors 0、skipped 0。Debug 构建通过，git diff --check 通过。

| 覆盖项 | 主机证据 |
| --- | --- |
| Daily / Session / 04:00 | 完整 Rules、额度、触发状态、SessionFrictionManager、使用日相关测试均通过 |
| 延迟静心 | DelayedDeepFocusSchedulerTest 13 项通过，含唯一任务、取消、权限变化、模式冲突、过期、实际起算完整时长、调度器重建和重复执行保护 |
| Session UI | SessionTodoUiTest 24 项通过，含按钮禁用时机、正常与重入语义、并排与纵排、确认/取消/失败、每日额度界面回归 |
| Deep Focus UI | DeepFocusTodosUiTest 16 项通过，含完整待办、滚动、长按确认、完成状态、独立 Compose 宿主及主题以外的原有交互 |
| Todo | TodoHomeSectionTest 13 项及 Repository 测试通过，含前三项、更多数量、增删完成、结转、长标题 |
| Theme | AppearancePreferencesTest 2 项、ThemeSwitchUiTest 8 项通过 |
| Launcher | API 26 / 35 共 4 项通过，加载与渲染使用实际资源 |

## APK identity

- applicationId 为 com.fphoenix28.timemanager.debug。
- 名称为 TimeManager Dev。
- versionName 为 0.9.0-dev，versionCode 为 3。
- aapt2 确认 debuggable 及上述元数据。
- apksigner 验证通过，v2 签名有效，单 signer，证书身份为 Android Debug。
- 按 owner 要求将 Beta1-debug APK 保存到项目外构建目录，完整路径、大小、校验值和 checkpoint 仅在本地交付记录中报告。

## 未完成的验收

本地测试覆盖模拟调度和界面，不能证明 vivo 后台真实延迟、OEM 无障碍和全屏悬浮、锁屏恢复、系统设置及必要电话界面在设备上均通过。

Owner 需要重点执行 P0，尤其是准备后自动启动完整三十分钟、普通 HOME/返回不能绕过静心、长按三秒结束后立即恢复、权限变化或模式冲突时安全放弃启动。

已有 Dev 安装可能保留之前显式设置的测试参数，更新 APK 不会重置用户数据。正常参数验收前由 owner 检查现有 Dev 测试工具与每应用设置。

Android 单色主题图标、主动通知升级、锁屏待办和课堂模式继续暂缓。

本轮手机操作、Release 构建、push、merge、master 和版本号修改均为 NO。正式安装包和签名文件保持原状。

## 2026-11-22 Launcher 迭代

测试包为 `Beta1/TimeManager-Beta1-launcher-final-debug.apk`，保存在项目外的 Android 构建目录。完整本地路径与验证结果见同目录的 `TimeManager-Beta1-launcher-final-debug.build-info.json`。

| 项目 | 值 |
| --- | --- |
| APK 源码提交 | `3a2d4adc783501ec4970483ddc578a32c0216323` |
| applicationId | `com.fphoenix28.timemanager.debug` |
| versionName / versionCode | `0.9.0-dev` / `3` |
| 大小 | 15,814,865 字节 |
| SHA-256 | `d2b3adccd86d7846f318faa63ffeb7a2389abbc8b5bbcd255e8138a90cc3fee2` |

最终 Launcher 图标从已核验母版去除最外围金色圆角方框与描边光晕，保留内部金色圆形时钟及刻度、星芒、沙漏、莲花和光轨。深紫黑背景延伸至系统遮罩边缘。两份自适应 XML 共用同一张位图，五档密度的备用图标使用同一构图。

在源码提交上重新执行完整 Debug 测试，654 项通过，其中 4 项为 API 26 和 35 的 Launcher 资源加载及遮罩渲染。Debug 构建、APK v2 签名验证和 `git diff --check` 通过。Gradle 确认现有 Debug 构建仍有效，交付 APK 与构建输出逐字节一致。包内 11 张图标的像素均与当前源码资源一致。随后更新的测试记录只含文档。

圆形、圆角方形、超椭圆和 48dp 的主机渲染已检查。vivo X200s 上的桌面遮罩、日常壁纸下的辨识度及其余 Beta 1 真机项目待 owner 验收，结果由 owner 填入[真机清单](BETA1_DEVICE_ACCEPTANCE.md)。
