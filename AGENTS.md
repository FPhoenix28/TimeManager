# 项目协作规则

我们不是写安全论文。保持实现和校验与任务风险相称，避免过度防御、反复罗列极小概率情况；不生成散列校验或相关算法。

## 真机操作：SAFE DEVICE TESTING BY DEFAULT

涉及 ADB、Android Studio 真机运行、设备自动化、instrumentation 或测试 cleanup 前，完整阅读 [DEVICE_SAFETY.md](DEVICE_SAFETY.md)。该文件是设备操作分类、内部 Preflight 和破坏性操作批准流程的权威规则。

- **正常测试直接执行**：内部核实目标是开发用 vivo X200s，包名来自实际构建配置，且不清除用户数据、不影响其它 App 后，可直接覆盖安装 `install -r`、启动/停止或 force-stop TimeManager、测试 Activity/Service/Overlay/Accessibility、旋转、截图、读取状态和日志，以及操作 TimeManager 本轮明确创建的 app-specific 测试文件。无需逐项请求确认，也不必展示每一步 Preflight。
- **LOCK DEVICE SERIAL / EXACT APPLICATION ID**：ADB 操作显式指定已核实 serial；包相关写操作仅限项目最终 applicationId。设备或包名不确定时停止，不能从应用显示名称猜测，也不能默认授权测试/helper 包。
- **日常测试硬边界**：NO AUTO UNINSTALL、NO AUTO PM CLEAR、NO USER DATA DELETE、NO BULK PACKAGE MODIFICATION、NO OTHER APP MODIFICATION、NO DESTRUCTIVE FALLBACK。手机用户数据是 PERSISTENT USER DATA。
- **破坏性操作先批准**：卸载、清数据、用户文件删除、wipe、批量包/权限修改或其它 App 写操作不属于正常测试授权。先停止并展示 COMMAND、TARGET、DATA IMPACT，取得针对该操作的明确批准；普通“继续测试”不适用。清空 TimeManager 数据仍需明确回复“确认清空TimeManager数据”。
- **覆盖安装失败即停止**：报告错误，不自动卸载重装、清数据或升级为破坏性重试。测试结束、失败或取消也不得触发 APK 卸载/数据清理。
- **核实测试框架的隐式动作**：这台长期使用的 vivo X200s 不运行任何已知会卸载目标 APK 或清数据的 instrumentation cleanup；当前默认 `connectedDebugAndroidTest` 属于禁用流程。普通 build → install -r → launch → logcat/rotate/UI 验证可以自动进行。
- **可逆设置要恢复**：记录本轮临时显示/测试设置，结束时恢复；不把 Debug 参数写入真实用户配置，也不清理既有应用数据或整个用户目录。

## 历史事故记录

保留 [2026-09-30 事故报告](docs/DEVICE_INCIDENT_REPORT-2026-09-30.md)，结论仍为 ROOT CAUSE NOT PROVEN，默认 connected 测试 cleanup 是最强候选。历史暂停记录不再作为正常开发测试的持续阻塞；当前执行策略以以上规则和 DEVICE_SAFETY.md 为准。
