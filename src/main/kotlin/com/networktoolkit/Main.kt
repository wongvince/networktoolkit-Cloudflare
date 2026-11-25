package com.networktoolkit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.*
import java.io.File
import java.net.*
import java.awt.Desktop
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.Executors

private val Navy = Color(0xFF102B49)
private val Panel = Color(0xFF173B5E)
private val Accent = Color(0xFF55A9F5)
private val Light = Color(0xFFEAF2FA)

private const val APP_VERSION = "1.4.0"
private const val DEFAULT_RELEASE_API =
    "https://api.github.com/repos/wongvince/nettoo1/releases/latest"
private const val RELEASE_PAGE_URL = "https://github.com/wongvince/nettoo1/releases/latest"

// Set this to your deployed Cloudflare Worker URL before building the client.
private const val TRIAL_API_BASE_URL = "https://networktoolkit-trial-api.huangsxint.workers.dev"
// Replace with the Base64 DER/SPKI public key generated for the TRIAL signing key only.
private const val TRIAL_PUBLIC_KEY_B64 = "MCowBQYDK2VwAyEAQQq5wfOrcc0ahLR/Ins1ypYtaVl9zBadbWH0iNk/lK8="

private const val PUBLIC_KEY_B64 =
    "MCowBQYDK2VwAyEABLHkBjACjcxTjOw2P6yQUuTOau4hTVzp6/1NgtH9he4="

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "NetworkToolkit 网络工程师工具箱 v$APP_VERSION"
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Accent,
                background = Navy,
                surface = Panel
            )
        ) {
            ToolkitApp()
        }
    }
}

@Composable
private fun ToolkitApp() {
    var activated by remember { mutableStateOf(LicenseManager.isActivated()) }
    var trialStatus by remember { mutableStateOf(if (activated) null else TrialManager.evaluate()) }
    val trialActive = !activated && trialStatus?.active == true
    val canUseApp = activated || trialActive
    var tab by remember {
        mutableStateOf(if (canUseApp) "网络适配器" else "软件授权")
    }

    LaunchedEffect(activated) {
        while (!activated) {
            delay(60_000)
            trialStatus = TrialManager.evaluate()
            if (trialStatus?.active != true) tab = "软件授权"
        }
    }

    var licenseText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val tabs = listOf(
        "网络适配器",
        "Ping",
        "Tracert",
        "ARP 扫描",
        "DNS 查询",
        "TCP/UDP 测试",
        "TCP/UDP 服务端",
        "路由表",
        "IPv4 子网",
        "MAC 厂商",
        "版本更新",
        "软件授权"
    )

    Row(Modifier.fillMaxSize().background(Navy)) {
        Column(
            Modifier
                .width(218.dp)
                .fillMaxHeight()
                .background(Color(0xFF0B2036))
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            Text("NETWORK", color = Accent)
            Text(
                "TOOLKIT",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )
            Text("网络工程师工具箱", color = Light)

            Spacer(Modifier.height(22.dp))

            tabs.forEach { item ->
                if (canUseApp || item == "软件授权") {
                    NavigationDrawerItem(
                        label = { Text(item) },
                        selected = tab == item,
                        onClick = { tab = item },
                        modifier = Modifier.padding(vertical = 2.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Panel,
                            selectedTextColor = Color.White,
                            unselectedTextColor = Light
                        )
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "Windows x64 · v$APP_VERSION",
                color = Color(0xFF9BB5CE)
            )
            Text(
                when {
                    activated -> "授权：已激活"
                    trialActive -> "试用：${formatTrialRemaining(trialStatus!!.remainingMillis)}"
                    else -> "试用已结束 · 需要授权"
                },
                color = if (activated || trialActive) Color(0xFF7FE0A7)
                else Color(0xFFFF8A80)
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp)
        ) {
            Text(
                tab,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(Modifier.height(14.dp))

            if (!canUseApp && tab != "软件授权") {
                Text(
                    (trialStatus?.reason?.takeIf { it.isNotBlank() }
                        ?: "首次启动需要联网登记试用；试用到期后请完成正式授权。"),
                    color = Color(0xFFFF8A80)
                )
            } else {
                if (trialActive && tab != "软件授权") {
                    Text(
                        "免费试用剩余时间：${formatTrialRemaining(trialStatus!!.remainingMillis)}。试用结束后所有功能将锁定。",
                        color = Color(0xFFFFC56B)
                    )
                }
                when (tab) {
                    "软件授权" -> LicensePage(
                        licenseText,
                        { licenseText = it },
                        message,
                        trialStatus?.reason.orEmpty(),
                        { code ->
                            message = LicenseManager.activate(code)
                            activated = LicenseManager.isActivated()
                            if (activated) {
                                trialStatus = null
                                tab = "网络适配器"
                            }
                        }
                    )

                    "网络适配器" -> AdapterPage()

                    "Ping" -> CommandPage(
                        "目标地址",
                        "192.168.1.1",
                        "执行 Ping"
                    ) {
                        "ping -n 4 -w 1000 $it"
                    }

                    "Tracert" -> CommandPage(
                        "目标地址",
                        "www.example.com",
                        "开始追踪"
                    ) {
                        "tracert -d -h 20 -w 1000 $it"
                    }

                    "ARP 扫描" -> ArpPage()
                    "DNS 查询" -> DnsPage()
                    "TCP/UDP 测试" -> TcpUdpPage()
                    "TCP/UDP 服务端" -> LocalServerPage()
                    "路由表" -> OutputPage("读取 Windows 路由表", "route print")
                    "IPv4 子网" -> SubnetPage()
                    "MAC 厂商" -> MacPage()
                    "版本更新" -> UpdatePage()
                }
            }
        }
    }
}

/* =========================
   授权
   ========================= */

@Composable
private fun LicensePage(
    code: String,
    onCode: (String) -> Unit,
    message: String,
    trialReason: String,
    activate: (String) -> Unit
) {
    val device = remember { LicenseManager.deviceId() }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoCard(
            "离线设备绑定授权",
            "将以下设备识别码提供给授权签发方。授权方签发与本机设备码绑定的许可证。"
        )

        OutlinedTextField(
            device,
            {},
            readOnly = true,
            label = { Text("本机设备识别码") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                java.awt.datatransfer.StringSelection(device),
                null
            )
        }) {
            Text("复制设备识别码")
        }

        OutlinedTextField(
            code,
            onCode,
            label = { Text("许可证内容 / 授权码") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Button(onClick = { activate(code) }) {
            Text("验证并激活")
        }

        if (trialReason.isNotBlank()) {
            Text(trialReason, color = Color(0xFFFF8A80))
        }
        Text(message, color = Color(0xFFFFC56B))

        Text(
            "许可证验证采用 Ed25519 签名。授权私钥只保存在授权签发方。",
            color = Light
        )
    }
}

/* =========================
   网络适配器
   ========================= */

private data class AdapterItem(
    val name: String
)

private data class IpProfile(
    val name: String,
    val ip: String,
    val mask: String,
    val gateway: String,
    val dns: String
)

@Composable
private fun AdapterPage() {
    var adapters by remember { mutableStateOf<List<AdapterItem>>(emptyList()) }
    var selected by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    var ip by remember { mutableStateOf("") }
    var mask by remember { mutableStateOf("255.255.255.0") }
    var gateway by remember { mutableStateOf("") }
    var dns by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("") }
    var profiles by remember { mutableStateOf(ProfileStore.load()) }

    val scope = rememberCoroutineScope()

    fun refreshAdapters() {
        scope.launch {
            loading = true
            val names = getAdapterNames()
            adapters = names.map { AdapterItem(it) }
            if (selected !in names) selected = names.firstOrNull().orEmpty()
            loading = false
            notice = if (names.isEmpty()) "没有发现网络适配器，请检查 Windows 网络设置。" else "已发现 ${names.size} 个网络适配器。"
        }
    }

    LaunchedEffect(Unit) { refreshAdapters() }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            Modifier.weight(1.15f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("选择网络适配器", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text("点击网卡卡片进行选择", color = Light)
                }
                OutlinedButton(onClick = { refreshAdapters() }, enabled = !loading) {
                    Text(if (loading) "刷新中…" else "刷新网卡")
                }
            }

            var adapterMenuExpanded by remember { mutableStateOf(false) }
            if (adapters.isEmpty()) {
                InfoCard("未发现以太网适配器", "仅显示名称以“以太网”或“Ethernet”开头的网卡。请检查网卡是否已启用，然后点击刷新网卡。")
            } else {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { adapterMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selected.ifBlank { "请选择以太网适配器" })
                            Text("▼")
                        }
                    }
                    DropdownMenu(
                        expanded = adapterMenuExpanded,
                        onDismissRequest = { adapterMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.95f)
                    ) {
                        adapters.forEach { adapter ->
                            DropdownMenuItem(
                                text = { Text(adapter.name) },
                                onClick = {
                                    selected = adapter.name
                                    adapterMenuExpanded = false
                                    notice = "已选择：${adapter.name}"
                                }
                            )
                        }
                    }
                }
            }

            Divider(color = Accent)
            Text("静态 IPv4 配置", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text("当前网卡：${selected.ifBlank { "尚未选择" }}", color = Accent)
            Field("IPv4 地址", ip) { ip = it }
            Field("子网掩码", mask) { mask = it }
            Field("默认网关（可留空）", gateway) { gateway = it }
            Field("首选 DNS（可留空）", dns) { dns = it }

            Button(
                enabled = selected.isNotBlank(),
                onClick = {
                    scope.launch {
                        val result = applyStaticIp(selected, ip, mask, gateway, dns)
                        notice = if (result.startsWith("配置失败")) result.lineSequence().first()
                        else "配置请求已提交。请确认 Windows 管理员权限提示，并刷新网卡验证。"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("应用静态 IP（需要管理员权限）") }

            Text("提示：修改 IP 前请确认配置正确。错误的 IP 或网关可能导致网络中断。", color = Color(0xFFFFC56B))
        }

        Column(
            Modifier.weight(0.85f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("保存的配置方案", color = Accent, style = MaterialTheme.typography.titleLarge)
            Field("方案名称", profileName) { profileName = it }
            Button(
                onClick = {
                    if (profileName.isNotBlank() && validIpv4(ip) && validMask(mask)) {
                        val profile = IpProfile(profileName.trim(), ip.trim(), mask.trim(), gateway.trim(), dns.trim())
                        profiles = profiles + profile
                        ProfileStore.save(profiles)
                        notice = "配置方案已保存（不绑定网络适配器）：${profile.name}"
                        profileName = ""
                    } else notice = "请填写方案名称、有效 IP 和子网掩码。"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存当前配置") }

            profiles.forEachIndexed { index, profile ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(profile.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text("${profile.ip} / ${profile.mask}\n网关：${profile.gateway.ifBlank { "未设置" }}  DNS：${profile.dns.ifBlank { "未设置" }}", color = Light)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                ip = profile.ip
                                mask = profile.mask
                                gateway = profile.gateway
                                dns = profile.dns
                                notice = "已载入方案。选择上方以太网适配器后，可应用此配置。"
                            }) { Text("载入方案") }
                            Button(onClick = {
                                ip = profile.ip
                                mask = profile.mask
                                gateway = profile.gateway
                                dns = profile.dns
                                if (selected.isBlank()) {
                                    notice = "方案已载入，请先选择一个以太网适配器，再点击应用静态 IP。"
                                } else {
                                    scope.launch {
                                        val result = applyStaticIp(selected, profile.ip, profile.mask, profile.gateway, profile.dns)
                                        notice = if (result.startsWith("配置失败")) result.lineSequence().first()
                                        else "已向当前网卡 $selected 提交配置，请确认管理员权限提示。"
                                    }
                                }
                            }) { Text("应用到当前网卡") }
                            OutlinedButton(onClick = {
                                profiles = profiles.toMutableList().also { it.removeAt(index) }
                                ProfileStore.save(profiles)
                                notice = "已删除配置方案。"
                            }) { Text("删除") }
                        }
                    }
                }
            }
            if (notice.isNotBlank()) {
                Text(notice, color = if (notice.contains("失败") || notice.contains("请填写") || notice.contains("请先")) Color(0xFFFFC56B) else Color(0xFF8BE0B0))
            }
        }
    }
}

private suspend fun getAdapterNames(): List<String> =
    withContext(Dispatchers.IO) {
        val script = """
            Get-NetAdapter |
            Sort-Object ifIndex |
            ForEach-Object { ${'$'}_.Name }
        """.trimIndent()

        runPowerShell(script)
            .lineSequence()
            .map { it.trim() }
            .filter {
                it.isNotBlank() &&
                !it.startsWith("WARNING", true) &&
                (it.startsWith("以太网", true) || it.startsWith("Ethernet", true))
            }
            .distinct()
            .toList()
    }

private suspend fun getAdapterInfo(alias: String): String {
    val safe = alias.replace("'", "''")

    return runPowerShell(
        """
        Get-NetIPConfiguration -InterfaceAlias '$safe' |
        Format-List InterfaceAlias,InterfaceDescription,IPv4Address,IPv4DefaultGateway,DNSServer

        Get-NetAdapter -Name '$safe' |
        Format-List Name,Status,LinkSpeed,MacAddress,InterfaceDescription
        """.trimIndent()
    )
}

private suspend fun applyStaticIp(
    alias: String,
    ip: String,
    mask: String,
    gateway: String,
    dns: String
): String = withContext(Dispatchers.IO) {
    if (
        alias.isBlank() ||
        !validIpv4(ip) ||
        !validMask(mask) ||
        (gateway.isNotBlank() && !validIpv4(gateway)) ||
        (dns.isNotBlank() && !validIpv4(dns))
    ) {
        return@withContext "配置失败：请检查网卡、IP、掩码、网关和 DNS。"
    }

    val prefix = maskToPrefix(mask) ?: return@withContext "子网掩码无效。"
    val safe = alias.replace("'", "''")

    val script = """
        ${'$'}ErrorActionPreference = 'Stop'
        ${'$'}a = '$safe'

        Set-NetIPInterface -InterfaceAlias ${'$'}a -AddressFamily IPv4 -Dhcp Disabled

        Get-NetIPAddress -InterfaceAlias ${'$'}a -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { ${'$'}_.PrefixOrigin -eq 'Manual' } |
        Remove-NetIPAddress -Confirm:${'$'}false -ErrorAction SilentlyContinue

        New-NetIPAddress -InterfaceAlias ${'$'}a -IPAddress '$ip' -PrefixLength $prefix
    """.trimIndent() +
        if (gateway.isNotBlank()) {
            "\nSet-NetIPInterface -InterfaceAlias ${'$'}a -AddressFamily IPv4 -Dhcp Disabled\n" +
                "Set-NetRoute -InterfaceAlias ${'$'}a -DestinationPrefix '0.0.0.0/0' -NextHop '$gateway' -ErrorAction SilentlyContinue"
        } else {
            ""
        } +
        if (dns.isNotBlank()) {
            "\nSet-DnsClientServerAddress -InterfaceAlias ${'$'}a -ServerAddresses '$dns'"
        } else {
            ""
        }

    try {
        val encoded = Base64.getEncoder().encodeToString(
            script.toByteArray(Charsets.UTF_16LE)
        )

        val elevateScript = """
            ${'$'}p = Start-Process powershell.exe -Verb RunAs -Wait -PassThru -ArgumentList @(
                '-NoProfile',
                '-ExecutionPolicy',
                'Bypass',
                '-EncodedCommand',
                '$encoded'
            )
            if (${'$'}p.ExitCode -ne 0) {
                exit ${'$'}p.ExitCode
            }
        """.trimIndent()

        val result = runPowerShell(elevateScript)

        if (result.contains("Access is denied", true)) {
            "配置失败：权限不足，请使用管理员账户。"
        } else {
            "已提交管理员权限配置请求。\n" +
                "请确认 Windows 用户账户控制（UAC）提示。\n" +
                "完成后请点击刷新，确认 IP 是否已经生效。\n$result"
        }
    } catch (e: Exception) {
        "配置失败：${e.message}"
    }
}

@Composable
private fun OutputPage(title: String, command: String) {
    var output by remember { mutableStateOf("点击按钮读取结果。") }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            enabled = !running,
            onClick = {
                scope.launch {
                    running = true
                    output = runCommand(command)
                    running = false
                }
            }
        ) {
            Text(if (running) "正在读取……" else title)
        }
        OutputBox(output)
    }
}

/* =========================
   Ping / Tracert
   ========================= */

@Composable
private fun CommandPage(
    label: String,
    initial: String,
    button: String,
    command: (String) -> String
) {
    var target by remember { mutableStateOf(initial) }
    var output by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Field(label, target) { target = it }

    Button(
        onClick = {
            scope.launch {
                output = if (validHost(target)) {
                    runCommand(command(target))
                } else {
                    "目标地址格式无效。"
                }
            }
        }
    ) {
        Text(button)
    }

    OutputBox(output)
}

/* =========================
   ARP 扫描
   ========================= */

@Composable
private fun ArpPage() {
    var ip by remember { mutableStateOf("192.168.1.1") }
    var mask by remember { mutableStateOf("255.255.255.0") }
    var output by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Field("本机 / 目标网段 IPv4 地址", ip) { ip = it }
    Field("子网掩码", mask) { mask = it }

    Text(
        "根据 IP 和掩码计算扫描范围。扫描指定网段内的单播地址，不扫描广播地址。",
        color = Light
    )

    Button(
        enabled = !scanning,
        onClick = {
            scope.launch {
                if (!validIpv4(ip) || !validMask(mask)) {
                    output = "IP 地址或子网掩码无效。"
                } else {
                    scanning = true
                    output = scanArpSubnet(ip, mask)
                    scanning = false
                }
            }
        }
    ) {
        Text(if (scanning) "正在扫描……" else "开始 ARP 扫描")
    }

    OutputBox(output)
}

private suspend fun scanArpSubnet(
    ip: String,
    mask: String
): String = withContext(Dispatchers.IO) {
    val prefix = maskToPrefix(mask) ?: return@withContext "掩码无效。"

    val ipNum = ipv4ToLong(ip)
    val maskNum = ipv4ToLong(mask)
    val network = ipNum and maskNum
    val broadcast = network or (maskNum xor 0xFFFFFFFFL)

    val start = if (prefix >= 31) network else network + 1
    val end = if (prefix >= 31) broadcast else broadcast - 1

    val count = end - start + 1

    if (count <= 0) return@withContext "没有可扫描的地址。"
    if (count > 4096) {
        return@withContext "扫描范围过大：$count 个地址。\n请使用更小的子网掩码范围。"
    }

    val alive = java.util.Collections.synchronizedList(
        mutableListOf<String>()
    )

    val executor = Executors.newFixedThreadPool(32)

    try {
        val tasks = (start..end).map { address ->
            executor.submit {
                val target = longToIpv4(address)

                try {
                    val process = ProcessBuilder(
                        "ping.exe",
                        "-n", "1",
                        "-w", "350",
                        target
                    ).redirectErrorStream(true).start()

                    val response = process.inputStream
                        .bufferedReader(Charsets.UTF_8)
                        .readText()

                    val exit = process.waitFor()

                    if (
                        exit == 0 &&
                        response.contains("TTL=", true)
                    ) {
                        alive.add(target)
                    }
                } catch (_: Exception) {
                }
            }
        }

        tasks.forEach { it.get() }

        val arp = runCommand("arp -a")

        val result = buildString {
            appendLine("扫描网段：$ip / $mask")
            appendLine("CIDR：/$prefix")
            appendLine("扫描地址数量：$count")
            appendLine("发现响应设备：${alive.size}")
            appendLine()

            appendLine("响应设备：")
            alive.sorted().forEach {
                appendLine(it)
            }

            appendLine()
            appendLine("Windows ARP 表：")
            append(arp)
        }

        result
    } finally {
        executor.shutdownNow()
    }
}

/* =========================
   DNS
   ========================= */

@Composable
private fun DnsPage() {
    var host by remember { mutableStateOf("example.com") }
    var output by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Field("域名", host) { host = it }

    Button(
        onClick = {
            scope.launch {
                output = if (validHost(host)) {
                    runCommand("nslookup $host")
                } else {
                    "域名格式无效。"
                }
            }
        }
    ) {
        Text("查询 DNS")
    }

    OutputBox(output)
}

/* =========================
   TCP / UDP 测试（左右分栏）
   ========================= */

@Composable
private fun TcpUdpPage() {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TcpTestPanel()
        UdpTestPanel()
    }
}

@Composable
private fun TcpTestPanel() {
    var host by remember { mutableStateOf("192.168.1.1") }
    var port by remember { mutableStateOf("80") }
    var output by remember { mutableStateOf("填写目标地址和端口后开始测试。") }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.width(390.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TCP 测试", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text("检测目标 TCP 端口是否能够建立连接", color = Light)
            Field("目标 IP / 域名", host) { host = it }
            Field("TCP 端口", port) { port = it }
            Button(
                enabled = !testing,
                onClick = {
                    scope.launch {
                        val p = port.toIntOrNull()
                        if (!validHost(host) || p == null || p !in 1..65535) {
                            output = "主机地址或端口格式无效。"
                        } else {
                            testing = true
                            output = tcpConnectTest(host, p)
                            testing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (testing) "正在连接…" else "测试 TCP") }
            OutputBox(output)
        }
    }
}

@Composable
private fun UdpTestPanel() {
    var host by remember { mutableStateOf("127.0.0.1") }
    var port by remember { mutableStateOf("53") }
    var output by remember { mutableStateOf("UDP 不建立 TCP 式连接；发送成功不代表远端端口开放。") }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.width(390.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("UDP 测试", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text("向目标 UDP 端口发送探测数据报", color = Light)
            Field("目标 IP / 域名", host) { host = it }
            Field("UDP 端口", port) { port = it }
            Button(
                enabled = !testing,
                onClick = {
                    scope.launch {
                        val p = port.toIntOrNull() ?: 0
                        testing = true
                        output = if (validHost(host)) udpProbe(host, p) else "目标主机格式无效。"
                        testing = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (testing) "正在发送…" else "发送 UDP 探测") }
            OutputBox(output)
        }
    }
}

private suspend fun tcpConnectTest(host: String, port: Int): String =
    withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        try {
            Socket().use { socket -> socket.connect(InetSocketAddress(host, port), 1500) }
            val ms = (System.nanoTime() - start) / 1_000_000
            "TCP 连接成功\n目标：$host:$port\n耗时：${ms} ms"
        } catch (e: Exception) {
            val ms = (System.nanoTime() - start) / 1_000_000
            "TCP 连接失败\n目标：$host:$port\n耗时：${ms} ms\n原因：${e.message}"
        }
    }

private suspend fun udpProbe(host: String, port: Int): String =
    withContext(Dispatchers.IO) {
        if (port !in 1..65535) return@withContext "端口范围必须是 1-65535。"
        try {
            DatagramSocket().use { socket ->
                val data = "NetworkToolkit UDP probe".toByteArray()
                val packet = DatagramPacket(data, data.size, InetAddress.getByName(host), port)
                socket.send(packet)
            }
            "UDP 数据报已发送至 $host:$port。\n注意：UDP 无连接，发送成功不能证明远端端口开放。"
        } catch (e: Exception) {
            "UDP 探测失败：${e.message}"
        }
    }

/* =========================
   TCP / UDP 模拟服务端
   ========================= */

@Composable
private fun LocalServerPage() {
    var protocol by remember { mutableStateOf("TCP") }
    var portText by remember { mutableStateOf("5566") }
    var status by remember { mutableStateOf("未启动") }
    var output by remember { mutableStateOf("输入端口并启动服务端。") }
    var tcpServer by remember { mutableStateOf<ServerSocket?>(null) }
    var udpServer by remember { mutableStateOf<DatagramSocket?>(null) }
    var serverJob by remember { mutableStateOf<Job?>(null) }
    val tcpClientStates = remember { mutableStateMapOf<String, String>() }
    val udpClientLastSeen = remember { mutableStateMapOf<String, Long>() }
    val clientSockets = remember { mutableSetOf<Socket>() }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (isActive) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    fun stopServer() {
        try { tcpServer?.close() } catch (_: Exception) { }
        try { udpServer?.close() } catch (_: Exception) { }
        tcpClientStates.keys.toList().forEach { tcpClientStates[it] = "断开连接" }
        clientSockets.toList().forEach { try { it.close() } catch (_: Exception) { } }
        clientSockets.clear()
        serverJob?.cancel()
        tcpServer = null
        udpServer = null
        serverJob = null
        status = "已停止"
        output += "\n服务端已停止。"
    }

    DisposableEffect(Unit) {
        onDispose {
            try { tcpServer?.close() } catch (_: Exception) { }
            try { udpServer?.close() } catch (_: Exception) { }
            clientSockets.toList().forEach { try { it.close() } catch (_: Exception) { } }
            serverJob?.cancel()
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("TCP / UDP 服务端", color = Accent, style = MaterialTheme.typography.titleLarge)
        Text("监听本机所有 IPv4 网卡，其他设备可通过本机局域网 IP 和端口连接。", color = Light)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = protocol == "TCP", onClick = { protocol = "TCP" }, enabled = status != "运行中" && status != "启动中", label = { Text("TCP 服务端") })
            FilterChip(selected = protocol == "UDP", onClick = { protocol = "UDP" }, enabled = status != "运行中" && status != "启动中", label = { Text("UDP 服务端") })
            Spacer(Modifier.width(8.dp))
            Text("监听端口", color = Light)
            Box(Modifier.width(150.dp)) { Field("端口", portText) { portText = it.filter(Char::isDigit).take(5) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = status != "运行中" && status != "启动中",
                onClick = {
                    val listenPort = portText.toIntOrNull()
                    if (listenPort == null || listenPort !in 1..65535) {
                        output = "端口必须在 1-65535 之间。"
                    } else {
                        status = "启动中"
                        tcpClientStates.clear()
                        udpClientLastSeen.clear()
                        serverJob = scope.launch(Dispatchers.IO) {
                            try {
                                if (protocol == "TCP") {
                                    val server = ServerSocket()
                                    server.reuseAddress = true
                                    server.bind(InetSocketAddress("0.0.0.0", listenPort))
                                    withContext(Dispatchers.Main) {
                                        tcpServer = server
                                        status = "运行中"
                                        output = "TCP 服务端已启动，监听 0.0.0.0:$listenPort"
                                    }
                                    while (isActive) {
                                        val client = server.accept()
                                        val peer = "${client.inetAddress.hostAddress}:${client.port}"
                                        withContext(Dispatchers.Main) {
                                            clientSockets.add(client)
                                            tcpClientStates[peer] = "已连接"
                                            output = (output + "\n$peer [已连接]").takeLast(6000)
                                        }
                                        launch(Dispatchers.IO) {
                                            try {
                                                client.use { socket ->
                                                    val input = socket.getInputStream()
                                                    val buffer = ByteArray(2048)
                                                    while (isActive) {
                                                        val length = input.read(buffer)
                                                        if (length < 0) break
                                                        if (length > 0) {
                                                            val received = String(buffer, 0, length, Charsets.UTF_8)
                                                            withContext(Dispatchers.Main) {
                                                                output = (output + "\n[$peer] $received").takeLast(6000)
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (_: Exception) {
                                            } finally {
                                                withContext(Dispatchers.Main) {
                                                    clientSockets.remove(client)
                                                    tcpClientStates[peer] = "断开连接"
                                                    output = (output + "\n$peer [断开连接]").takeLast(6000)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val server = DatagramSocket(InetSocketAddress("0.0.0.0", listenPort))
                                    withContext(Dispatchers.Main) {
                                        udpServer = server
                                        status = "运行中"
                                        output = "UDP 服务端已启动，监听 0.0.0.0:$listenPort"
                                    }
                                    while (isActive) {
                                        val buffer = ByteArray(4096)
                                        val packet = DatagramPacket(buffer, buffer.size)
                                        server.receive(packet)
                                        val peer = "${packet.address.hostAddress}:${packet.port}"
                                        val data = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                                        withContext(Dispatchers.Main) {
                                            udpClientLastSeen[peer] = System.currentTimeMillis()
                                            output = (output + "\n$peer [收到 UDP 数据]：$data").takeLast(6000)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    if (status == "运行中" || status == "启动中") {
                                        status = "已停止"
                                        output = (output + "\n服务端异常：${e.message}").takeLast(6000)
                                    }
                                }
                            }
                        }
                    }
                }
            ) { Text("启动服务端") }
            Button(onClick = { stopServer() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B3434))) { Text("停止服务端") }
            Text("当前状态：$status", color = if (status == "运行中") Color(0xFF8BE0B0) else Light)
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (protocol == "TCP") "TCP 客户端连接状态" else "UDP 来源活动状态", color = Color.White, style = MaterialTheme.typography.titleMedium)
                if (protocol == "TCP") {
                    if (tcpClientStates.isEmpty()) Text("暂无客户端连接", color = Light)
                    tcpClientStates.entries.toList().reversed().forEach { (peer, state) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(peer, color = Light, modifier = Modifier.weight(1f))
                            Text("[$state]", color = if (state == "已连接") Color(0xFF8BE0B0) else Color(0xFFFFC56B))
                        }
                    }
                } else {
                    if (udpClientLastSeen.isEmpty()) Text("尚未收到 UDP 数据报", color = Light)
                    udpClientLastSeen.entries.toList().reversed().forEach { (peer, lastSeen) ->
                        val active = nowMs - lastSeen < 15000
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(peer, color = Light, modifier = Modifier.weight(1f))
                            Text(if (active) "[已连接*]" else "[断开连接*]", color = if (active) Color(0xFF8BE0B0) else Color(0xFFFFC56B))
                        }
                    }
                    Text("* UDP 本身无连接机制：此处以最近 15 秒是否收到该 IP:端口的数据作为活动状态。", color = Color(0xFFFFC56B))
                }
            }
        }
        Text("TCP：客户端连接后持续显示 IP:端口，断开时更新为 [断开连接]。UDP：收到数据报后记录来源；由于 UDP 无连接，使用 15 秒活动超时显示状态。", color = Light)
        OutputBox(output)
    }
}

/* =========================
   IPv4 子网计算
   ========================= */

@Composable
private fun SubnetPage() {
    var baseCidr by remember { mutableStateOf("192.168.1.1/24") }
    var targetPrefix by remember { mutableStateOf("26") }
    var output by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("IPv4 子网划分", color = Accent, style = MaterialTheme.typography.titleLarge)
        Text(
            "输入现有网段（例如 192.168.1.1/24），再输入要划分的目标前缀长度（例如 26），系统将计算子网数量及每个子网的地址范围。",
            color = Light
        )
        Field("原始 IPv4 网段（例如 192.168.1.1/24）", baseCidr) { baseCidr = it }
        Field("目标子网前缀长度（例如 26）", targetPrefix) { targetPrefix = it }
        Button(
            onClick = {
                output = calculateSubnetDivision(
                    baseCidr.trim(),
                    targetPrefix.toIntOrNull() ?: -1
                )
            }
        ) { Text("计算子网划分") }
        if (output.isNotBlank()) OutputBox(output)
    }
}

private fun calculateSubnetDivision(baseCidr: String, targetPrefix: Int): String {
    val parts = baseCidr.split("/")
    if (parts.size != 2 || !validIpv4(parts[0])) {
        return "原始网段格式无效，请按 192.168.1.1/24 的格式输入。"
    }
    val basePrefix = parts[1].toIntOrNull()
        ?: return "原始网段前缀无效，请输入 0 至 32 之间的整数。"
    if (basePrefix !in 0..32) return "原始网段前缀必须在 0 至 32 之间。"
    if (targetPrefix !in 0..32) return "目标前缀必须在 0 至 32 之间。"
    if (targetPrefix < basePrefix) {
        return "目标前缀 /$targetPrefix 小于原始前缀 /$basePrefix，不能在该原始网段内这样划分。"
    }

    val baseMask = if (basePrefix == 0) 0L
        else (0xFFFFFFFFL shl (32 - basePrefix)) and 0xFFFFFFFFL
    val baseNetwork = ipv4ToLong(parts[0]) and baseMask
    val baseBroadcast = baseNetwork or (baseMask xor 0xFFFFFFFFL)
    val subnetCount = 1L shl (targetPrefix - basePrefix)
    val blockSize = 1L shl (32 - targetPrefix)
    val targetMask = if (targetPrefix == 0) 0L
        else (0xFFFFFFFFL shl (32 - targetPrefix)) and 0xFFFFFFFFL
    val usableHosts = when {
        targetPrefix >= 31 -> 0L
        else -> blockSize - 2
    }

    val result = StringBuilder()
    result.appendLine("原始网段：${longToIpv4(baseNetwork)}/$basePrefix")
    result.appendLine("目标前缀：/$targetPrefix")
    result.appendLine("子网掩码：${longToIpv4(targetMask)}")
    result.appendLine("可划分子网数量：$subnetCount 个")
    result.appendLine("每个子网地址总数：$blockSize 个")
    result.appendLine("每个子网可用主机数：$usableHosts 个")
    result.appendLine("原始网段范围：${longToIpv4(baseNetwork)} - ${longToIpv4(baseBroadcast)}")
    result.appendLine()

    val displayCount = minOf(subnetCount, 512L)
    for (index in 0 until displayCount) {
        val network = baseNetwork + index * blockSize
        val broadcast = network + blockSize - 1
        result.appendLine("子网 ${index + 1}：${longToIpv4(network)}/$targetPrefix")
        result.appendLine("网络号：${longToIpv4(network)}")
        result.appendLine("子网掩码：${longToIpv4(targetMask)}")
        if (targetPrefix <= 30) {
            result.appendLine("可用地址范围：${longToIpv4(network + 1)} - ${longToIpv4(broadcast - 1)}")
        } else {
            result.appendLine("可用地址范围：无（/31、/32 按点到点或单地址网段处理）")
        }
        result.appendLine("广播地址：${longToIpv4(broadcast)}")
        result.appendLine("可用主机数：$usableHosts")
        if (index != displayCount - 1) result.appendLine()
    }
    if (subnetCount > displayCount) {
        result.appendLine()
        result.appendLine("提示：子网总数为 $subnetCount 个。为避免结果过长，当前仅列出前 $displayCount 个子网。")
    }
    return result.toString().trim()
}

/* =========================
   MAC 厂商
   ========================= */

@Composable
private fun MacPage() {
    var mac by remember { mutableStateOf("") }
    var output by remember {
        mutableStateOf("内置常见厂商 OUI 示例库。")
    }

    Field("MAC 地址", mac) { mac = it }

    Button(
        onClick = { output = MacVendor.lookup(mac) }
    ) {
        Text("查询厂商")
    }

    OutputBox(output)
}

private object MacVendor {
    private val vendors = mapOf(
        "000C29" to "VMware, Inc.",
        "001C42" to "Apple, Inc.",
        "001B63" to "Apple, Inc.",
        "F4F5D8" to "Apple, Inc.",
        "3C5AB4" to "Apple, Inc.",
        "001A11" to "Google, Inc.",
        "001B21" to "Intel Corporate",
        "001D7E" to "Hewlett Packard",
        "001E68" to "Dell Inc.",
        "001C23" to "Cisco Systems, Inc.",
        "001B0C" to "Cisco Systems, Inc.",
        "001D09" to "Cisco Systems, Inc.",
        "001A2B" to "Huawei Technologies",
        "00259E" to "Huawei Technologies",
        "001E10" to "TP-Link Technologies",
        "001D0F" to "TP-Link Technologies",
        "C0A0BB" to "TP-Link Technologies",
        "001E58" to "ASUSTek Computer",
        "001F1F" to "ASUSTek Computer",
        "001D60" to "Samsung Electronics",
        "001E7D" to "Samsung Electronics"
    )

    fun lookup(mac: String): String {
        val hex = mac.filter { it.isLetterOrDigit() }.uppercase()

        if (
            hex.length != 12 ||
            !hex.all { it in "0123456789ABCDEF" }
        ) {
            return "MAC 地址格式无效。示例：00:1A:2B:3C:4D:5E"
        }

        val oui = hex.take(6)

        val external = try {
            MacVendor::class.java.getResourceAsStream("/oui.csv")
                ?.bufferedReader()
                ?.useLines { lines ->
                    lines.map { it.split(",", limit = 2) }
                        .firstOrNull {
                            it.size == 2 &&
                                it[0].trim().equals(oui, true)
                        }
                        ?.get(1)
                        ?.trim()
                }
        } catch (_: Exception) {
            null
        }

        val vendor = external ?: vendors[oui]
            ?: "未在本地 OUI 数据库中找到。"

        return """
            MAC 地址：${hex.chunked(2).joinToString(":")}
            OUI：${oui.chunked(2).joinToString(":")}
            厂商：$vendor
        """.trimIndent()
    }
}

/* =========================
   版本更新
   ========================= */

private data class ReleaseInfo(
    val tag: String,
    val pageUrl: String,
    val notes: String,
    val portableUrl: String,
    val checksumUrl: String
)

@Composable
private fun UpdatePage() {
    var result by remember { mutableStateOf("当前版本：v$APP_VERSION\n点击“检查更新”获取 GitHub Releases 最新版本。") }
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun check() {
        scope.launch {
            checking = true
            release = null
            val checked = fetchLatestRelease(DEFAULT_RELEASE_API)
            release = checked.first
            result = checked.second
            checking = false
        }
    }

    LaunchedEffect(Unit) { check() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(
            "GitHub 自动更新",
            "仓库：wongvince/nettoo1\n启动此页面时自动检查，也可以手动检查。发现新版后由你确认下载；下载完成后退出软件并解压覆盖便携版目录。"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { check() }, enabled = !checking && !downloading) {
                Text(if (checking) "检查中…" else "检查更新")
            }
            OutlinedButton(onClick = {
                try { Desktop.getDesktop().browse(URI(RELEASE_PAGE_URL)) }
                catch (_: Exception) { result = "无法打开浏览器，请手动访问：$RELEASE_PAGE_URL" }
            }) { Text("打开 GitHub Releases") }
        }
        OutputBox(result)
        release?.let { info ->
            InfoCard("最新版本：${info.tag}", info.notes.ifBlank { "此版本没有填写更新说明。" })
            Button(
                enabled = !downloading && info.portableUrl.isNotBlank() && info.checksumUrl.isNotBlank(),
                onClick = {
                    scope.launch {
                        downloading = true
                        result = downloadAndVerifyPortable(info)
                        downloading = false
                    }
                }
            ) { Text(if (downloading) "正在下载并校验…" else "下载便携版更新（SHA-256 校验）") }
            if (info.portableUrl.isBlank()) Text("该 Release 没有便携版 ZIP 资产。", color = Color(0xFFFFC56B))
        }
        Text("安装版可在 GitHub Releases 下载；便携版下载后请先退出软件，再将 ZIP 解压覆盖原程序目录。请勿删除用户目录下的 .networktoolkit 数据文件夹。", color = Light)
    }
}

private suspend fun fetchLatestRelease(api: String): Pair<ReleaseInfo?, String> =
    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(api).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "NetworkToolkit/$APP_VERSION")
            val status = connection.responseCode
            if (status !in 200..299) return@withContext null to "检查失败：GitHub API 返回 HTTP $status。请确认仓库公开且已发布 Release。"
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            fun field(name: String): String = Regex("\"$name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .find(body)?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\r", "")?.replace("\\\"", "\"") ?: ""
            val tag = field("tag_name")
            if (tag.isBlank()) return@withContext null to "无法读取最新版本号。请先在 GitHub 创建正式 Release。"
            val page = field("html_url").ifBlank { RELEASE_PAGE_URL }
            val notes = field("body").take(1800)
            val assetsBlock = Regex("\"assets\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1).orEmpty()
            fun assetUrl(fileName: String): String {
                val assetPattern = Regex("\\{\\s*\"url\"\\s*:\\s*\"[^\"]+\".*?\"name\"\\s*:\\s*\"([^\"]+)\".*?\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
                return assetPattern.findAll(assetsBlock)
                    .firstOrNull { it.groupValues[1] == fileName }
                    ?.groupValues?.get(2).orEmpty()
            }
            val portableName = "NetworkToolkit-Windows-x64-Portable.zip"
            val info = ReleaseInfo(tag, page, notes, assetUrl(portableName), assetUrl("SHA256SUMS.txt"))
            val latestVersion = tag.removePrefix("v").removePrefix("V")
            val message = if (latestVersion == APP_VERSION) "当前已是最新版本：v$APP_VERSION" else "发现新版本：$tag\n当前版本：v$APP_VERSION\nRelease：$page\n\n请确认后下载更新。"
            info to message
        } catch (e: Exception) {
            null to "检查更新失败：${e.message ?: e.javaClass.simpleName}\n请检查网络连接，或稍后重试。"
        } finally {
            connection?.disconnect()
        }
    }

private suspend fun downloadAndVerifyPortable(info: ReleaseInfo): String = withContext(Dispatchers.IO) {
    try {
        val downloads = File(System.getProperty("user.home"), "Downloads")
        if (!downloads.exists()) downloads.mkdirs()
        val zip = File(downloads, "NetworkToolkit-${info.tag}-Windows-x64-Portable.zip")
        val checksumFile = File(downloads, "NetworkToolkit-${info.tag}-SHA256SUMS.txt")
        fun download(url: String, target: File) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "NetworkToolkit/$APP_VERSION")
            try {
                if (conn.responseCode !in 200..299) error("下载失败，HTTP ${conn.responseCode}")
                conn.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            } finally { conn.disconnect() }
        }
        download(info.portableUrl, zip)
        download(info.checksumUrl, checksumFile)
        val checksumLine = checksumFile.readLines().firstOrNull { it.contains("NetworkToolkit-Windows-x64-Portable.zip") }
            ?: error("SHA256SUMS.txt 中没有找到便携版 ZIP 的校验值")
        val expected = Regex("(?i)^([0-9a-f]{64})").find(checksumLine.trim())?.groupValues?.get(1)
            ?: error("SHA256SUMS.txt 中的 SHA-256 格式无效")
        val actual = MessageDigest.getInstance("SHA-256").digest(zip.readBytes())
            .joinToString("") { "%02x".format(it) }
        if (!actual.equals(expected, ignoreCase = true)) {
            zip.delete()
            error("SHA-256 校验失败，已删除下载文件。请重新检查更新。")
        }
        Desktop.getDesktop().open(downloads)
        "下载完成，SHA-256 校验通过。\n文件：${zip.absolutePath}\n已打开下载文件夹。请退出软件后解压 ZIP，并覆盖旧版程序目录。用户数据和授权保存在用户目录 .networktoolkit，不要删除该目录。"
    } catch (e: Exception) {
        "更新下载失败：${e.message ?: e.javaClass.simpleName}"
    }
}

/* =========================
   通用命令执行
   ========================= */

private suspend fun runCommand(command: String): String =
    withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(
                "cmd.exe",
                "/c",
                "chcp 65001>nul & $command"
            )
                .redirectErrorStream(true)
                .start()

            val text = process.inputStream
                .bufferedReader(Charsets.UTF_8)
                .readText()

            process.waitFor()

            text.ifBlank { "命令已执行，无输出。" }
        } catch (e: Exception) {
            "执行失败：${e.message}"
        }
    }

private suspend fun runPowerShell(script: String): String =
    withContext(Dispatchers.IO) {
        try {
            val fullScript = """
                [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding
                ${'$'}OutputEncoding = New-Object System.Text.UTF8Encoding
                $script
            """.trimIndent()

            val encoded = Base64.getEncoder().encodeToString(
                fullScript.toByteArray(Charsets.UTF_16LE)
            )

            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-EncodedCommand",
                encoded
            )
                .redirectErrorStream(true)
                .start()

            val text = process.inputStream
                .bufferedReader(Charsets.UTF_8)
                .readText()

            process.waitFor()

            text.ifBlank { "命令已执行，无输出。" }
        } catch (e: Exception) {
            "执行失败：${e.message}"
        }
    }

/* =========================
   输入组件
   ========================= */

@Composable
private fun Field(
    label: String,
    value: String,
    change: (String) -> Unit
) {
    OutlinedTextField(
        value,
        change,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun OutputBox(text: String) {
    OutlinedTextField(
        value = text,
        onValueChange = {},
        readOnly = true,
        label = { Text("执行结果") },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 130.dp, max = 360.dp),
        minLines = 5
    )
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Panel
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
            Text(body, color = Light)
        }
    }
}

/* =========================
   IP / 掩码工具
   ========================= */

private fun validIpv4(value: String): Boolean {
    return try {
        val parts = value.split(".")
        parts.size == 4 && parts.all {
            it.isNotEmpty() &&
                it.toInt() in 0..255
        }
    } catch (_: Exception) {
        false
    }
}

private fun validMask(value: String): Boolean =
    maskToPrefix(value) != null

private fun maskToPrefix(value: String): Int? {
    if (!validIpv4(value)) return null

    val bits = value.split(".")
        .joinToString("") {
            it.toInt().toString(2).padStart(8, '0')
        }

    if (!Regex("1*0*").matches(bits)) return null

    return bits.count { it == '1' }
}

private fun ipv4ToLong(value: String): Long {
    return value.split(".").fold(0L) { result, part ->
        (result shl 8) or part.toLong()
    }
}

private fun longToIpv4(value: Long): String {
    return (0..3).joinToString(".") {
        ((value shr (24 - 8 * it)) and 255).toString()
    }
}

private fun validHost(value: String): Boolean {
    if (value.isBlank() || value.length > 253) return false

    if (validIpv4(value)) return true

    return value.matches(
        Regex(
            "(?i)^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$"
        )
    )
}

/* =========================
   配置方案存储
   ========================= */

private object ProfileStore {
    private val file = File(
        System.getProperty("user.home"),
        ".networktoolkit/profiles.tsv"
    )

    fun load(): List<IpProfile> {
        return try {
            file.readLines().mapNotNull { line ->
                val parts = line.split("\t")
                when (parts.size) {
                    5 -> IpProfile(parts[0], parts[1], parts[2], parts[3], parts[4])
                    // 兼容旧版六列格式：第二列原为网卡名称，读取时忽略，不再绑定网卡。
                    6 -> IpProfile(parts[0], parts[2], parts[3], parts[4], parts[5])
                    else -> null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(list: List<IpProfile>) {
        file.parentFile.mkdirs()

        file.writeText(
            list.joinToString("\n") {
                listOf(
                    it.name,
                    it.ip,
                    it.mask,
                    it.gateway,
                    it.dns
                ).joinToString("\t")
            },
            Charsets.UTF_8
        )
    }
}

/* =========================
   授权管理
   ========================= */

private data class TrialStatus(
    val active: Boolean,
    val remainingMillis: Long,
    val reason: String = ""
)

private fun formatTrialRemaining(millis: Long): String {
    val totalMinutes = (millis.coerceAtLeast(0L) + 59_999L) / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "${hours}小时${minutes}分钟"
}

/**
 * Cloud-registered 24-hour trial. The signed server token is verified locally and
 * then cached in DPAPI-protected ProgramData + HKCU records. No network call is
 * made while a valid token is present. Missing local state never silently re-registers
 * if evidence of a previous v1.2/v1.3 local trial exists.
 */
private object TrialManager {
    private const val TRIAL_MILLIS = 24L * 60L * 60L * 1000L
    private const val REG_KEY = "HKCU\\Software\\NetworkToolkit"
    private const val REG_VALUE = "TrialState"
    private val legacyFile = File(System.getProperty("user.home"), ".networktoolkit/trial.dat")
    private val stateFile = File(
        System.getenv("ProgramData")?.takeIf { it.isNotBlank() } ?: "C:\\ProgramData",
        "NetworkToolkit\\trial.dat"
    )

    @Synchronized
    fun evaluate(): TrialStatus {
        if (!System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)) {
            return TrialStatus(false, 0L, "云端试用仅支持 Windows 10/11。")
        }
        return try {
            val fileExists = stateFile.isFile
            val registryValue = readRegistry()
            val registryExists = registryValue != null

            if (fileExists || registryExists) {
                if (!fileExists || !registryExists) return TrialStatus(false, 0L, "本地试用凭证记录不完整，已锁定。")
                val fileBlob = stateFile.readText(Charsets.US_ASCII).trim()
                if (fileBlob.isBlank() || !constantTimeEquals(fileBlob, registryValue!!)) {
                    return TrialStatus(false, 0L, "本地试用凭证记录不一致，已锁定。")
                }
                val plain = dpapi("unprotect", fileBlob)
                    ?: return TrialStatus(false, 0L, "本地试用凭证无法解密，已锁定。")
                val fields = plain.split('|')
                if (fields.size != 3 || fields[0] != "NTTRIALCACHE1") {
                    return TrialStatus(false, 0L, "本地试用凭证格式无效，已锁定。")
                }
                val token = fields[1]
                val lastSeen = fields[2].toLongOrNull()
                    ?: return TrialStatus(false, 0L, "本地时间记录无效，已锁定。")
                val claims = verifyToken(token)
                    ?: return TrialStatus(false, 0L, "云端签名凭证无效，已锁定。")
                val deviceHash = TrialDevice.deviceHash()
                    ?: return TrialStatus(false, 0L, "无法读取稳定设备标识，无法验证试用。")
                if (!constantTimeEquals(claims.deviceHash, deviceHash)) {
                    return TrialStatus(false, 0L, "试用凭证与当前设备不匹配。")
                }
                val now = System.currentTimeMillis()
                if (now < lastSeen || now < claims.issuedAt) {
                    return TrialStatus(false, 0L, "检测到系统时间回拨，试用已锁定。")
                }
                if (now >= claims.expiresAt || claims.expiresAt - claims.issuedAt != TRIAL_MILLIS) {
                    return TrialStatus(false, 0L, "24 小时免费试用已到期。")
                }
                if (!persist(token, now)) return TrialStatus(false, 0L, "无法更新本地防篡改记录。")
                return TrialStatus(true, claims.expiresAt - now)
            }

            // Never convert a previous offline trial into a fresh cloud trial on upgrade.
            val oldRegistry = readLegacyRegistry()
            if (legacyFile.exists() || oldRegistry != null) {
                return TrialStatus(false, 0L, "检测到旧版本试用记录。升级不会重新赠送试用，请输入正式授权。")
            }
            registerOnline()
        } catch (e: Exception) {
            TrialStatus(false, 0L, "试用登记失败：${e.message ?: "请检查网络后重新启动"}")
        }
    }

    private data class Claims(val deviceHash: String, val issuedAt: Long, val expiresAt: Long)

    private fun registerOnline(): TrialStatus {
        if (TRIAL_API_BASE_URL.contains("REPLACE-WITH-YOUR-WORKER") ||
            TRIAL_PUBLIC_KEY_B64.startsWith("REPLACE_")) {
            return TrialStatus(false, 0L, "尚未配置 Cloudflare 试用服务，请先完成服务端部署和客户端公钥配置。")
        }
        val deviceHash = TrialDevice.deviceHash()
            ?: return TrialStatus(false, 0L, "无法读取稳定设备标识；请检查 Windows CIM/WMI 服务。")
        val url = URL("${TRIAL_API_BASE_URL.trimEnd('/')}/v1/trial/register")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val body = "{\"deviceId\":\"$deviceHash\"}"
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status == 409) return TrialStatus(false, 0L, "这台电脑已经领取过免费试用。卸载或重装不能重新领取，请输入正式授权。")
            if (status !in 200..299) return TrialStatus(false, 0L, "云端登记失败（HTTP $status）：${jsonString(response, "error") ?: "请稍后重试"}")
            val token = jsonString(response, "token")
                ?: return TrialStatus(false, 0L, "服务器响应缺少试用凭证。")
            val claims = verifyToken(token)
                ?: return TrialStatus(false, 0L, "服务器返回的试用凭证签名无效。")
            if (!constantTimeEquals(claims.deviceHash, deviceHash)) return TrialStatus(false, 0L, "服务器凭证设备码不匹配。")
            val now = System.currentTimeMillis()
            if (claims.issuedAt > now + 5 * 60_000L || claims.expiresAt - claims.issuedAt != TRIAL_MILLIS || now >= claims.expiresAt) {
                return TrialStatus(false, 0L, "服务器返回的试用凭证时间范围无效。")
            }
            if (!persist(token, now)) return TrialStatus(false, 0L, "试用凭证已签发，但无法保存本地记录；请检查系统权限。")
            return TrialStatus(true, claims.expiresAt - now)
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyToken(token: String): Claims? {
        return try {
        val parts = token.split('.')
        if (parts.size != 2) return null
        val payloadBytes = Base64.getUrlDecoder().decode(parts[0])
        val signatureBytes = Base64.getUrlDecoder().decode(parts[1])
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(TRIAL_PUBLIC_KEY_B64))
        )
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(publicKey)
        verifier.update(payloadBytes)
        if (!verifier.verify(signatureBytes)) return null
        val fields = String(payloadBytes, Charsets.UTF_8).split('|')
        if (fields.size != 6 || fields[0] != "NTTRIAL1" || fields[5] != "NetworkToolkitTrial") return null
        val issued = fields[2].toLongOrNull() ?: return null
        val expires = fields[3].toLongOrNull() ?: return null
        if (fields[1].isBlank() || issued <= 0 || expires <= issued) return null
        Claims(fields[1], issued, expires)
        } catch (_: Exception) { null }
    }

    private fun jsonString(json: String, key: String): String? {
        val match = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").find(json)
        return match?.groupValues?.getOrNull(1)
    }

    private fun persist(token: String, lastSeen: Long): Boolean {
        return try {
        val deviceHash = TrialDevice.deviceHash() ?: return false
        val plain = "NTTRIALCACHE1|$token|$lastSeen"
        val blob = dpapi("protect", plain, deviceHash) ?: return false
        stateFile.parentFile.mkdirs()
        val temp = File(stateFile.parentFile, "trial.dat.tmp")
        temp.writeText(blob, Charsets.US_ASCII)
        try {
            java.nio.file.Files.move(temp.toPath(), stateFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            java.nio.file.Files.move(temp.toPath(), stateFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        writeRegistry(blob) && stateFile.isFile &&
            constantTimeEquals(stateFile.readText(Charsets.US_ASCII).trim(), readRegistry().orEmpty())
        } catch (_: Exception) { false }
    }

    private fun dpapi(operation: String, value: String, deviceHash: String? = null): String? {
        val entropyDevice = deviceHash ?: TrialDevice.deviceHash() ?: return null
        val script = if (operation == "protect") {
            """
            ${'$'}ErrorActionPreference = 'Stop'
            Add-Type -AssemblyName System.Security
            ${'$'}data = [Text.Encoding]::UTF8.GetBytes(${'$'}env:NT_TRIAL_VALUE)
            ${'$'}entropy = [Text.Encoding]::UTF8.GetBytes(${'$'}env:NT_TRIAL_DEVICE)
            ${'$'}out = [Security.Cryptography.ProtectedData]::Protect(${'$'}data, ${'$'}entropy, [Security.Cryptography.DataProtectionScope]::LocalMachine)
            [Convert]::ToBase64String(${'$'}out)
            """.trimIndent()
        } else {
            """
            ${'$'}ErrorActionPreference = 'Stop'
            Add-Type -AssemblyName System.Security
            ${'$'}data = [Convert]::FromBase64String(${'$'}env:NT_TRIAL_VALUE)
            ${'$'}entropy = [Text.Encoding]::UTF8.GetBytes(${'$'}env:NT_TRIAL_DEVICE)
            ${'$'}out = [Security.Cryptography.ProtectedData]::Unprotect(${'$'}data, ${'$'}entropy, [Security.Cryptography.DataProtectionScope]::LocalMachine)
            [Text.Encoding]::UTF8.GetString(${'$'}out)
            """.trimIndent()
        }
        return try {
            val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
            val process = ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
                .redirectErrorStream(true).apply {
                    environment()["NT_TRIAL_VALUE"] = value
                    environment()["NT_TRIAL_DEVICE"] = entropyDevice
                }.start()
            if (!process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly(); null
            } else {
                val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
                if (process.exitValue() != 0 || output.isBlank()) null else output.lineSequence().last().trim()
            }
        } catch (_: Exception) { null }
    }

    private fun readRegistry(): String? = registryQuery(REG_VALUE)
    private fun readLegacyRegistry(): String? = registryQuery("TrialState")
    private fun registryQuery(valueName: String): String? = try {
        val process = ProcessBuilder("reg.exe", "query", REG_KEY, "/v", valueName).redirectErrorStream(true).start()
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) null
        else process.inputStream.bufferedReader().readText().lineSequence()
            .firstOrNull { it.contains(valueName, ignoreCase = true) }?.trim()?.split(Regex("\\s+"))?.lastOrNull()
    } catch (_: Exception) { null }

    private fun writeRegistry(value: String): Boolean = try {
        val process = ProcessBuilder("reg.exe", "add", REG_KEY, "/v", REG_VALUE, "/t", "REG_SZ", "/d", value, "/f")
            .redirectErrorStream(true).start()
        process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0
    } catch (_: Exception) { false }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}

/** A reinstall-stable fingerprint based on firmware UUID, not Windows MachineGuid. */
private object TrialDevice {
    @Synchronized
    fun deviceHash(): String? {
        return try {
        val process = ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command",
            "(Get-CimInstance -ClassName Win32_ComputerSystemProduct -ErrorAction Stop).UUID")
            .redirectErrorStream(true).start()
        if (!process.waitFor(12, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) return null
        val uuid = process.inputStream.bufferedReader(Charsets.UTF_8).readText().trim().uppercase()
        if (uuid.isBlank() || uuid == "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF" ||
            uuid == "00000000-0000-0000-0000-000000000000" ||
            uuid.contains("INVALID") || uuid.contains("TO BE FILLED") ||
            uuid == "UNKNOWN" || uuid == "NONE") return null
        MessageDigest.getInstance("SHA-256").digest("NetworkToolkitTrial|$uuid".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { null }
    }
}

private object LicenseManager {
    private val file = File(
        System.getProperty("user.home"),
        ".networktoolkit/license.txt"
    )

    fun deviceId(): String {
        val machineGuid = try {
            val process = ProcessBuilder(
                "reg.exe",
                "query",
                "HKLM\\SOFTWARE\\Microsoft\\Cryptography",
                "/v",
                "MachineGuid"
            ).redirectErrorStream(true).start()

            process.inputStream.bufferedReader().readText()
                .lines()
                .lastOrNull { it.contains("MachineGuid") }
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.lastOrNull().orEmpty()
        } catch (_: Exception) {
            ""
        }

        val biosUuid = try {
            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-Command",
                "(Get-CimInstance Win32_ComputerSystemProduct).UUID"
            ).redirectErrorStream(true).start()

            process.inputStream.bufferedReader().readText().trim()
        } catch (_: Exception) {
            ""
        }

        val raw = listOf(machineGuid, biosUuid)
            .filter { it.isNotBlank() }
            .joinToString("|")
            .ifBlank {
                System.getenv("COMPUTERNAME") ?: "UNKNOWN-PC"
            }

        return java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02X".format(it) }
            .chunked(4)
            .take(6)
            .joinToString("-")
    }

    fun isActivated(): Boolean =
        try {
            verify(file.readText().trim())
        } catch (_: Exception) {
            false
        }

    fun activate(value: String): String =
        try {
            if (!verify(value.trim())) {
                "授权无效：签名、设备码或许可证格式不正确。"
            } else {
                file.parentFile.mkdirs()
                file.writeText(value.trim())
                "激活成功。"
            }
        } catch (e: Exception) {
            "授权验证失败：${e.message}"
        }

    private fun verify(value: String): Boolean {
        val parts = value.split(".")
        if (parts.size != 2) return false

        val payload = String(
            Base64.getUrlDecoder().decode(parts[0]),
            StandardCharsets.UTF_8
        )

        val fields = payload.split("|")
        if (fields.size != 3 || fields[0] != deviceId()) {
            return false
        }

        val expiry = fields[1].toLongOrNull() ?: return false

        if (expiry != 0L && System.currentTimeMillis() > expiry) {
            return false
        }

        val publicKey = KeyFactory.getInstance("Ed25519")
            .generatePublic(
                X509EncodedKeySpec(
                    Base64.getDecoder().decode(PUBLIC_KEY_B64)
                )
            )

        val signature = Signature.getInstance("Ed25519")
        signature.initVerify(publicKey)
        signature.update(parts[0].toByteArray(Charsets.US_ASCII))

        return signature.verify(
            Base64.getUrlDecoder().decode(parts[1])
        )
    }
}
