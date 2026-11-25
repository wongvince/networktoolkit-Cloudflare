# NetworkToolkit Windows x64 v1.4.0

专业中文网络工程师工具箱，目标平台 Windows 10/11 x64，JDK 17，Kotlin + Compose Desktop。

## 首版功能

- 仅筛选以太网 / Ethernet 网卡并通过下拉框选择；静态 IPv4 / 掩码 / 可选网关 / DNS 配置；IP 配置方案独立保存，不绑定网卡，应用时才选择当前网卡
- Ping、Tracert、ARP 扫描（/24 逐地址 ICMP 探测后读取 ARP 缓存）
- DNS 查询、TCP/UDP 合并测试页（左侧 TCP、右侧 UDP）
- TCP/UDP 服务端：TCP 客户端连接/断开状态及来源 IP:端口显示；UDP 来源活动状态显示
- Windows 路由表、IPv4 固定长度子网划分计算
- MAC OUI 厂商查询（内置常见 OUI 示例数据，可通过 `src/main/resources/oui.csv` 扩充 IEEE 数据）
- GitHub Releases 更新检查、便携版下载及 SHA-256 完整性校验；创建 v* 标签后由 GitHub Actions 自动构建并发布 Release
- Ed25519 离线设备绑定授权验证

## 界面与网络测试交互

- 窗口缩小时，左侧导航和右侧内容区域支持纵向滚动；TCP/UDP 测试左右分栏在窄窗口下可横向滚动。
- 网络适配器页仅显示名称以“以太网”或“Ethernet”开头的网卡，并使用下拉框选择，不显示 PowerShell 网卡读取执行结果。
- IPv4 子网页输入原始网段（例如 192.168.1.1/24）和目标前缀（例如 /26），计算可划分子网数量，并列出各子网的网络号、子网掩码、可用地址范围、广播地址和可用主机数。
- TCP 服务端持续跟踪每个客户端连接，显示来源 IP:端口及 `[已连接]` / `[断开连接]`。
- UDP 不存在 TCP 式连接状态；界面以最近 15 秒是否收到该来源的数据报显示活动/超时状态。

## 构建环境

- Windows 10/11 x64
- JDK 17
- Gradle 8.10 或兼容版本
- 首次构建需要网络下载 Gradle / Maven 依赖

在 PowerShell 中进入项目目录，执行：

```powershell
.\build-windows.ps1
```

也可以执行：

```powershell
gradle packageReleaseExe createDistributable
```

产物位于 `build/compose/binaries/`。

## GitHub Actions 自动构建

1. 将整个目录上传到 GitHub 仓库根目录。
2. 手动测试构建：打开 Actions，运行 `NetworkToolkit Windows x64 Build and Release` 的 **Run workflow**，构建结果会出现在 Artifacts。
3. 正式发布：将源码版本号（`Main.kt` 的 `APP_VERSION`、`build.gradle.kts` 的 `version` 和 `packageVersion`）统一更新，然后推送对应标签，例如 `v1.4.0`。
4. 推送 `v*` 标签会自动触发 Windows x64 构建，并将 `NetworkToolkit-Setup.exe`、便携版 ZIP 和 `SHA256SUMS.txt` 上传到 GitHub Releases。
5. 软件「版本更新」页面会查询 `wongvince/nettoo1` 的最新 Release；进入该页面时自动检查，也可手动检查。用户确认后可下载便携版并校验 SHA-256。

## 离线授权

许可证格式为 `base64url(payload).base64url(signature)`。payload 为：

`DEVICE_ID|EXPIRY_UTC_MILLIS|NetworkToolkit`

永久授权 expiry 使用 `0`。授权签发工具位于 `tools/LicenseIssuer.java`。

### 重要安全说明

当前源码中的公钥是本项目开发阶段的测试公钥，正式发放前必须由你自己生成新的 Ed25519 密钥对，并将新公钥 DER Base64 写入 `Main.kt` 的 `PUBLIC_KEY_B64`。私钥只保存在你自己的离线授权电脑，绝不能上传 GitHub、放进客户端或发给用户。每次更换公钥后都需要重新构建客户端。

生成密钥（需要 OpenSSL）：

```powershell
openssl genpkey -algorithm ED25519 -out license-private.pem
openssl pkey -in license-private.pem -pubout -out license-public.pem
openssl pkey -pubin -in license-public.pem -outform DER | openssl base64 -A
```

将最后输出的 Base64 字符串替换 `PUBLIC_KEY_B64`。签发工具编译：

```powershell
javac .\tools\LicenseIssuer.java
```

签发许可证：

```powershell
java -cp .\tools LicenseIssuer DEVICE_ID 0 .\license-private.pem
```

期限授权请将第二个参数换成 UTC Unix 毫秒时间戳。将输出的许可证字符串交给对应用户。

## 本机数据

IP 配置方案和许可证保存在用户目录 `.networktoolkit`。卸载软件不会自动删除该目录。

## 重要说明 / 首版限制

- 网卡信息读取、系统诊断依赖 Windows PowerShell / CMD。
- 修改 IP 会触发 UAC 管理员授权提示。请先确认选中的网卡名称和参数；错误配置可能导致网络中断。
- ARP 扫描只扫描用户明确填写的 IPv4 /24 网段；只在自己拥有或获准管理的网络中使用。
- UDP 无连接握手，发送成功不能证明远端 UDP 端口开放。
- MAC 厂商识别当前仅提供 OUI 查询入口，尚未内置完整 IEEE OUI 数据库；需后续加入本地 oui.csv 数据文件。
- 版本更新页默认查询 `wongvince/nettoo1`；进入页面时自动检查，支持手动检查、下载便携版 ZIP 和 SHA-256 校验。下载后需退出软件并解压覆盖便携版目录；当前不静默更新，也不自动覆盖正在运行的程序。
- Windows 安装包需在 Windows runner 上构建。本 ZIP 是完整源码项目，不是已编译 EXE；本执行环境没有 Windows 打包工具链，不能声称已产出并测试 Windows 安装包。

## 首次拿到项目后的建议顺序

1. 先把项目上传到自己的 GitHub 仓库，运行 Actions，下载 Windows x64 构建产物。
2. 首次测试建议使用虚拟机或备用电脑；不要直接在正在远程连接的电脑上测试 IP 修改功能。
3. 激活正式用户前，先生成自己的 Ed25519 密钥对、替换客户端公钥并重新构建。
4. 在 `src/main/resources/oui.csv` 中追加 `六位十六进制OUI,厂商名称`，重新构建即可更新本地厂商库。
5. 当前更新地址已配置为 `wongvince/nettoo1`。若迁移仓库，需要同步修改 `DEFAULT_RELEASE_API` 和 `RELEASE_PAGE_URL`。


## Windows x64 产物说明

GitHub Actions 工作流会生成并上传两个独立 Artifact：

- `NetworkToolkit-Windows-Setup`：下载后文件名为 `NetworkToolkit-Setup.exe`。
- `NetworkToolkit-Windows-Portable`：下载后解压 ZIP，运行其中的 `NetworkToolkit.exe`（无需安装）。

安装版和便携版均由 Compose Desktop 的 `jpackage` 打包流程生成；便携版会在 app image 中检查 `runtime` 目录，确保包含随应用分发的 Java Runtime。推送 `v*` 标签时工作流会自动创建 GitHub Release 并上传三个发布资产；手动运行工作流则只生成 Actions Artifacts。


## 1.3.0 离线试用授权（增强本地防重置）

- 首次启动自动开始 24 小时免费试用。
- 试用期内可使用全部功能；到期后所有工具页面锁定，仅保留软件授权页面。
- 继续沿用原有设备码绑定 Ed25519 离线许可证验证，不需要联网。
- 试用状态同时保存在 `%ProgramData%\NetworkToolkit\trial.dat` 和当前 Windows 用户注册表 `HKCU\Software\NetworkToolkit\TrialState`；两份记录必须同时存在且逐字节一致。
- 状态使用 Windows DPAPI `LocalMachine` 保护，并以设备 ID 作为附加熵；客户端不能再通过简单编辑文本文件伪造试用起始时间。
- 首次启动仅在文件与注册表两处都不存在时初始化；升级自 v1.2.0 时会先校验旧版 `trial.dat` 并迁移原始开始时间，保留已消耗的试用时长，不因升级重新获得 24 小时。任一新记录缺失、内容不一致、DPAPI 解密失败、设备 ID 不匹配或时间倒退，均按试用到期处理，不自动补建新记录。
- 每次启动及运行期间定期刷新最后运行时间；24 小时到期后所有工具锁定。原 Ed25519 离线许可证格式、公钥验签与设备绑定授权逻辑保持不变。
- 普通卸载/重装软件不会清除 ProgramData 与注册表记录；删除其中一份不会重置试用，而会触发锁定。切换 Windows 用户时，由于当前用户注册表记录缺失，也会锁定而不是重新开始。
- 安全边界：完全离线客户端无法保证绝对防绕过。拥有本机管理员/系统控制权的人仍可能同时删除两份记录、恢复系统快照/重装 Windows、修改系统时钟并回滚状态，或补丁修改程序。因此这是提高重置成本的本地防护，不是不可破解 DRM。正式商业发行若要做到跨重装/跨用户可靠防重置，需要在线首次登记或硬件/企业级可信计时与服务端策略。

# NetworkToolkit v1.4.0：Cloudflare 首次登记 + 24 小时离线试用

本版本在 v1.3.0 源码上增加 Cloudflare Workers + D1 试用登记服务。原有网络工具页面、IP 配置、更新页及正式 Ed25519 授权格式/公钥常量不作协议变更。**试用签名密钥与正式授权密钥完全分离。**

## 试用行为

1. 已有有效正式许可证：直接进入软件，不请求试用服务器。
2. 未激活且没有本地云端凭证：首次启动必须联网访问 Cloudflare Worker。
3. Worker 在 D1 中以设备指纹哈希作为唯一键；首次成功登记后签发 24 小时 Ed25519 试用凭证。
4. 客户端用独立的试用公钥验签，确认设备指纹和 24 小时时间窗，再把凭证写入 DPAPI LocalMachine 保护的 ProgramData 文件与 HKCU 注册表镜像。
5. 后续启动只在本地验签，不访问网络；有效凭证可离线使用到截止时间。
6. 删除/重装本地状态后，客户端必须重新联网登记；若原 24 小时窗口仍有效，D1 只允许恢复同一原始开始/截止时间的剩余凭证，不会延长试用；原窗口到期后返回 HTTP 409。
7. v1.2/v1.3 的本地试用记录不会被当作“新用户”自动换发云端 24 小时试用。升级时检测到旧记录且没有云端凭证，会要求正式授权。

### 设备识别边界

试用使用 `Win32_ComputerSystemProduct.UUID`（固件/SMBIOS UUID）生成 SHA-256 指纹，不使用 Windows `MachineGuid`，因此正常重装 Windows 后通常保持一致。原正式许可证仍使用原 `LicenseManager.deviceId()`，两套设备 ID 互不替代。若 BIOS UUID 缺失、为全零/全 F、或 WMI/CIM 不可用，试用登记会失败关闭，不会退回易变的电脑名。

硬件 UUID 可能因更换主板、固件/虚拟机配置变化而改变；虚拟机克隆也可能复制 UUID。它能显著提高普通重装绕过成本，但不是 TPM 级不可伪造身份。管理员可修改客户端二进制，因此客户端防护无法构成绝对 DRM。

**从 v1.3.0 离线版切换到 v1.4.0 的历史边界：** v1.3.0 没有云端登记历史。若升级时原来的 ProgramData/注册表记录仍在，v1.4.0 会识别并拒绝重新领取；但若用户在升级前已经同时清除了 v1.3.0 的全部本地记录，云端无法知道其过去曾经试用过，首次云端登记可能成为该设备的第一次云端记录。这是首次引入服务器登记时无法追溯补齐的历史数据，不应宣传为可追溯封堵所有旧版本重置。

## A. Cloudflare 首次配置（推荐全程使用网页 + GitHub Actions）

部署不需要在个人 Windows 电脑安装 Node.js、npm、OpenSSL 或 Wrangler。GitHub Actions 会在云端安装 Wrangler、测试 Worker、执行 D1 schema，并部署 Worker。你只需在 Cloudflare 网页创建一次 D1 数据库、生成一次签名密钥，并把配置放入 GitHub Actions。

### 1. 创建 Cloudflare D1 数据库（仅首次）

1. 登录 https://dash.cloudflare.com/ 。
2. 打开 **Workers & Pages → D1 SQL Database**（不同语言界面名称可能略有差异）。
3. 点击 **Create database**，名称填写 `networktoolkit-trials`。
4. 创建后复制数据库 UUID（Database ID），不要把它当作密码；后续作为 GitHub Actions Variable 保存。

### 2. 生成独立试用 Ed25519 密钥（一次性）

密钥必须是 Ed25519 PKCS#8 私钥，客户端使用对应的 DER/SPKI 公钥。可在可信的密钥生成环境运行项目中的 `cloudflare/generate-trial-keys.ps1`，也可以使用你已有的安全密钥管理流程。此步骤只需生成一次，不需要在每次部署时重新生成。

脚本输出：

- `TRIAL_SIGNING_PRIVATE_KEY_B64`：仅放入 GitHub Actions Secret，绝不能提交到 GitHub 文件、Issue、日志或 EXE。
- `TRIAL_PUBLIC_KEY_B64`：稍后写入 Windows 客户端 `Main.kt`，公钥可以公开。

建议把私钥另存一份离线加密备份。私钥丢失后，服务器不能再签发新凭证；更换密钥需要同步更新客户端公钥并重新构建客户端。

### 3. 创建 Cloudflare API Token（一次性）

在 Cloudflare Dashboard → **My Profile → API Tokens → Create Token** 创建专用 Token。只授予部署所需的账户级权限：Workers Scripts 编辑、D1 编辑，以及 Wrangler 所需的账户读取权限；资源范围限制到你的目标账户。不要使用 Global API Key。复制 Token，它只会显示一次。

### 4. 在 GitHub 配置 Secrets 和 Variables

打开仓库 `wongvince/nettoo1` → **Settings → Secrets and variables → Actions**。

在 **Secrets** 中新增：

| 名称 | 内容 |
|---|---|
| `CLOUDFLARE_API_TOKEN` | 第 3 步创建的 Cloudflare API Token |
| `TRIAL_SIGNING_PRIVATE_KEY_B64` | 第 2 步生成的试用私钥 Base64 |

在 **Variables** 中新增：

| 名称 | 内容 |
|---|---|
| `CLOUDFLARE_ACCOUNT_ID` | Cloudflare 账户 ID |
| `CLOUDFLARE_DATABASE_ID` | 第 1 步 D1 数据库 ID |

注意：Secret 与 Variable 名称必须完全一致。私钥只能放在 Secret，不能放在 Variable。

### 5. 手动首次部署

1. 打开 GitHub 仓库 → **Actions**。
2. 左侧选择 **NetworkToolkit Cloudflare Trial API Deploy**。
3. 点击 **Run workflow**，选择包含此 workflow 的分支（例如 `v1.4.0-cloud-trial` 或 `main`）。
4. 点击绿色 **Run workflow**。
5. 等待所有步骤变绿。日志中 `Deploy Worker` 步骤会显示 Worker URL，形式类似 `https://networktoolkit-trial-api.<账户子域>.workers.dev`。
6. 在浏览器打开 `https://你的Worker地址/health`，应返回 `ok: true`。

工作流会先执行 Worker 语法检查和自动测试，再执行可重复运行的 D1 建表语句，最后部署 Worker。若缺少 Secret/Variable、API Token 权限不足或 D1 ID 错误，工作流会失败并指出对应步骤。

### 6. 后续自动部署

自动部署触发条件是：向 `main` 分支推送/合并 `cloudflare/**` 或 `.github/workflows/deploy-cloudflare.yml` 的变更。其他文件（例如 Windows UI 或网络工具）变化不会触发 Cloudflare 部署。也可以随时回到 Actions 页面手动点击 **Run workflow**。

首次部署建议先从 `v1.4.0-cloud-trial` 分支手动运行，验收通过后再合并到 `main`。生产环境建议在 GitHub **Settings → Environments** 创建 `cloudflare-production` 环境，并按需配置 Required reviewers，避免未经审核的生产部署。

### 7. 本地备用部署（可选）

如果以后需要在本机调试，仍可进入 `cloudflare` 目录，使用 Node.js LTS、npm 和 Wrangler。常用命令：

```powershell
npm install
npm test
npx wrangler login
npx wrangler d1 execute networktoolkit-trials --remote --file=./schema.sql
npx wrangler deploy
```

本地部署前仍需把 `wrangler.toml` 中的 `REPLACE_WITH_D1_DATABASE_ID` 换成真实 D1 ID，并配置 Worker Secret。日常部署优先使用 GitHub Actions，避免个人电脑环境差异。

## B. 配置 Windows 客户端

打开 `src/main/kotlin/com/networktoolkit/Main.kt`，找到文件顶部：

```kotlin
private const val TRIAL_API_BASE_URL = "https://REPLACE-WITH-YOUR-WORKER.workers.dev"
private const val TRIAL_PUBLIC_KEY_B64 = "REPLACE_WITH_TRIAL_PUBLIC_KEY_DER_BASE64"
```

分别替换为：

- `TRIAL_API_BASE_URL`：A-6 部署成功后显示的 Worker 根地址，不要在末尾加 `/`。
- `TRIAL_PUBLIC_KEY_B64`：A-1 脚本输出的 `TRIAL_PUBLIC_KEY_B64` Base64 内容。

不要把私钥粘贴到 `Main.kt`。然后确认 `build.gradle.kts` 的版本和包版本为 `1.4.0`。

## C. 本地构建和发布

Windows 10/11 x64、JDK 17、Gradle 8.10.2 环境下：

```powershell
gradle packageReleaseExe createDistributable
```

产物位于 `build/compose/binaries/`。也可把完整项目推送到 `wongvince/nettoo1`，运行现有 Windows 构建 workflow 的手动构建，确认无误后再推送 `v1.4.0` 标签触发 Release。Cloudflare 部署由独立的 `deploy-cloudflare.yml` 负责，两条 workflow 相互独立。

**重要：** GitHub Actions 可以自动部署 Worker 和初始化 D1 表，但不会替你创建 Cloudflare 账户/D1 数据库，也不会自动把 Worker URL、公钥写入 Windows 客户端。首次仍需按 A 节配置 Cloudflare 资源与 GitHub Secrets/Variables，并按 B 节把 Worker URL、公钥填入客户端后再构建。源码包内没有预置你的账户 URL、D1 ID 或任何私钥。

## D. 首次试用验收清单

建议使用一台测试电脑和一个全新的测试 VM/设备，按顺序验证：

1. 新设备首次联网启动：界面进入工具页，显示剩余试用时间；D1 新增一条设备记录。
2. 断开网络，关闭并重新打开软件：仍可进入工具页，不发起网络登记请求。
3. 将系统时间调回过去后启动：本地检测回拨并锁定。
4. 删除本地 ProgramData/注册表试用缓存后重启并联网：服务器返回 409，不能重新领取。
5. 同一台电脑卸载/重装软件后启动：D1 仍拒绝第二次领取。
6. 24 小时后：试用凭证过期，所有工具页锁定。
7. 使用原正式授权签发工具签发的许可证：仍按原正式 Ed25519 流程激活。

不要在唯一生产电脑上先做破坏性测试。测试时不要手动删除 D1 中的记录，除非你明确要把那台测试设备重置为“可重新试用”。

## E. Cloudflare 免费额度与运营提醒

Cloudflare 免费计划适合低频的首次登记场景，但不是无限资源。官方当前文档列出 Workers Free 100,000 requests/day；D1 Free 每日行读取/写入有额度、每库 500 MB，超出每日额度后 D1 查询会失败直到额度重置。请在 Cloudflare Dashboard 监控用量；如果新用户登记量增长或遭遇滥用，再考虑升级/增加服务端风控。参考：

- Workers Limits: https://developers.cloudflare.com/workers/platform/limits/
- D1 Limits: https://developers.cloudflare.com/d1/platform/limits/
- D1 Pricing: https://developers.cloudflare.com/d1/platform/pricing/

## F. 数据与隐私

D1 保存的是 64 位十六进制 SHA-256 设备指纹、首次签发时间和到期时间，不保存用户姓名、Windows 用户名、IP 地址或原始 BIOS UUID。该哈希仍属于可长期关联同一设备的假名化标识，应在你的隐私说明中披露用途、保留期限和联系渠道。当前示例没有提供用户自助删除/申诉页面；如果要提供设备更换或误判申诉，需要增加人工后台流程。

## G. 当前限制

- 首次登记必须联网；如果 Cloudflare 服务不可用、设备被网络策略拦截或 D1 达到免费额度，首次试用无法开始。已有有效凭证不受影响，仍可离线使用到期。
- 服务器只允许每个硬件 UUID 指纹领取一次；更换主板/固件 UUID 后可能被视为新设备，虚拟机克隆也可能共享同一设备身份。
- 本地时间回拨可检测，但离线运行无法从密码学上完全阻止用户向前/向后调整时钟；DPAPI 和双记录增加篡改成本，不能替代可信硬件时钟。
- 高权限用户仍可补丁修改客户端跳过校验。正式商业发行如需更强控制，应考虑 TPM 证明、代码签名、服务端授权检查或周期性联网复核。
- 这是可部署的服务端/客户端源码模板；必须按 A、B 填入你自己的数据库 ID、Worker URL 和试用公钥后才能用于实际登记。当前交付环境没有你的 Cloudflare 账户权限，不能代你创建资源或声称已上线。
