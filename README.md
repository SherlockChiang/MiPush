# MiPushFramework的分应用模块

**本模块需要配合[MiPushFramework](https://github.com/NihilityT/MiPushFramework)使用**

`MiPushFramework`是一个能让不用MIUI的用户也能用上小米的系统推送服务的开源项目

默认情况下，其发送的通知是以“推送服务”的身份发送。

该模块借助 [LSPosed](https://github.com/LSPosed/LSPosed) 为 `MiPushFramework` 提供通知以目标应用发送的能力，
同时支持将应用运行环境伪装成小米设备，以此来实现无后台系统级别的推送通道。

### 安装步骤：
- 从[这里](https://github.com/NihilityT/MiPushFramework/releases/latest)下载并安装`MiPushFramework`，按照指引完成其初始化。

- 下载本模块的最新版本并安装，在 LSPosed 中启用 MiPush 模块，并勾选 「系统框架」、「推送服务」作用域，然后重启设备，[下载地址](https://github.com/NihilityT/MiPush/releases/latest)

- LSPosed 里 MiPush 模块里勾选你需要支持推送的目标应用（这一步目的是将应用环境伪装成小米设备，如果你使用了其他方式伪装设备，可以不进行这一步），然后重启一到两次目标应用使其注册上推送通道

- 杀掉应用测试推送是否生效（可以使用QQ、酷安测试）
　　
### 注意：
- 并不是所有应用都支持推送，目前测试已支持大部分应用，比如 QQ、酷安等

- **微信不支持**

- 请保证 `MiPushFramework` 在后台运行，不要禁用其自启权限和访问目标推送应用的权限

- 反馈问题或交流讨论可加入 [Telegram 群组](https://t.me/+SXl7v8t-lOa9KCAp)、[QQ群](https://jq.qq.com/?_wv=1027&k=P0EQCaUz)

- 通过GitHub反馈 `MiPushFramework` 的问题时请到[这里](https://github.com/NihilityT/MiPushFramework/issues)反馈

- 提建议时不要操之过急，否则会有反作用。

- 不要在交流群里挑起对立，因挑起对立导致大佬退群的，自己面壁思过看怎么挽回
### 反馈
[Github Issues](https://github.com/NihilityT/MiPush/issues)、[Telegram Group](https://t.me/+SXl7v8t-lOa9KCAp)、[QQ群](https://jq.qq.com/?_wv=1027&k=P0EQCaUz)

通过 GitHub 反馈 MiPushFramework 的问题时请到[这里](https://github.com/NihilityT/MiPushFramework/issues)反馈

### License
[GNU General Public License v3 (GPL-3)](http://www.gnu.org/copyleft/gpl.html).

有些狗不遵守开源协议（非本项目），请**务必**遵守开源协议 **（此话来自MiPushFramework的README.md）**
