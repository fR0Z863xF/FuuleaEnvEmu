package com.fr0z863xf.FuEmu

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import com.fr0z863xf.FuEmu.ui.theme.FuEmuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.io.FileWriter
import androidx.core.content.edit


data class GitHubAsset(val name: String, val browserDownloadUrl: String)
data class GitHubReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
    val body: String,
    val assets: List<GitHubAsset>
)

data class InjectRule(var enabled: Boolean, var filename: String, var code: String)
data class PatchRule(var enabled: Boolean, var filename: String, var regex: String, var replacement: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FuEmuTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("FuEmuPrefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()

    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestReleaseInfo by remember { mutableStateOf<GitHubReleaseInfo?>(null) }
    var isCheckingForUpdate by remember { mutableStateOf(false) }


    fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
        val newParts = newVersion.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentVersion.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(newParts.size, currentParts.size)) {
            val newPart = newParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }
            if (newPart > currentPart) return true
            if (newPart < currentPart) return false
        }
        return false
    }

    fun checkForUpdates() {
        if (isCheckingForUpdate) return
        isCheckingForUpdate = true
        Toast.makeText(context, "正在检查更新...", Toast.LENGTH_SHORT).show()

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/fR0Z863xF/FuuleaEnvEmu/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000 // 10 seconds
                connection.readTimeout = 10000   // 10 seconds

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val jsonResponse = JSONObject(response.toString())
                    val tagName = jsonResponse.getString("tag_name").removePrefix("v") // Remove 'v' prefix if present
                    val htmlUrl = jsonResponse.getString("html_url")
                    val body = jsonResponse.getString("body")
                    val assetsArray = jsonResponse.getJSONArray("assets")
                    val assetsList = mutableListOf<GitHubAsset>()
                    for (i in 0 until assetsArray.length()) {
                        val assetObject = assetsArray.getJSONObject(i)
                        assetsList.add(
                            GitHubAsset(
                                name = assetObject.getString("name"),
                                browserDownloadUrl = assetObject.getString("browser_download_url")
                            )
                        )
                    }

                    val currentVersion = (context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0").removePrefix("v")
                    if (tagName.isNotEmpty() && tagName != currentVersion && isNewerVersion(tagName, currentVersion)) {
                        withContext(Dispatchers.Main) {
                            latestReleaseInfo = GitHubReleaseInfo(jsonResponse.getString("tag_name"), htmlUrl, body, assetsList)
                            showUpdateDialog = true
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e("MainScreen", "GitHub API Err: ${connection.responseCode} ${connection.responseMessage}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "检查更新失败: ${connection.responseMessage}", Toast.LENGTH_LONG).show()
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("MainScreen", "Update check failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "检查更新失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isCheckingForUpdate = false
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FuEmu") },
                actions = {
                    Text(if (isCheckingForUpdate) "正在检查更新..." else "检查更新->")
                    IconButton(onClick = { checkForUpdates() }, enabled = !isCheckingForUpdate) {
                        Icon(painterResource(R.drawable.refresh_fill1_24px), contentDescription = "检查更新")

                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val launchIntent = Intent(Intent.ACTION_MAIN)
                launchIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                launchIntent.setComponent(ComponentName("com.fuulea.venus.g", "com.fuulea.venus.MainActivity"))
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(context, launchIntent,null)
            }) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.play_arrow_fill1_24px), contentDescription = "启动")
                    Spacer(Modifier.width(8.dp))
                    Text("启动")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SystemInfoCard(sharedPreferences)
            GovernanceEnvironmentCard(sharedPreferences)
            UtilHooksCard(sharedPreferences)
            RNHooksCard(sharedPreferences)
            ProjectInfoCard()
        }
    }

    if (showUpdateDialog && latestReleaseInfo != null) {
        UpdateInfoDialog(
            releaseInfo = latestReleaseInfo!!,
            onDismiss = { showUpdateDialog = false },
            context = context
        )
    }
}

fun savePreferencesToJson(context: Context, sharedPreferences: SharedPreferences) {
    val allPreferences = sharedPreferences.all
    val json = JSONObject()
    for ((key, value) in allPreferences) {
        try {
            json.put(key, value)
        } catch (e: JSONException) {
            Log.e("MainScreen", "Error putting preference into JSON", e)
        }
    }

    if (json.length() == 0) {
        Log.w("MainScreen", "No preferences to save to JSON.")
        return
    }

    try {
        val fuEmuDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "FuEmu"
        )
        if (!fuEmuDir.exists()) {
            if (!fuEmuDir.mkdirs()) {
                Log.e("MainScreen", "Failed to create directory: ${fuEmuDir.absolutePath}")
                Toast.makeText(context, "创建目录失败", Toast.LENGTH_LONG).show()
                return
            }
        }
        val file = File(fuEmuDir, "FuEmuPrefs.json")
        val fileWriter = FileWriter(file)
        fileWriter.write(json.toString(4)) // Use 4 for pretty print JSON
        fileWriter.flush()
        fileWriter.close()
        Toast.makeText(context, "数据已保存", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Log.e("MainScreen", "Error saving preferences to JSON", e)
        Toast.makeText(context, "保存数据失败: ${e.message}", Toast.LENGTH_LONG).show()
    }
}


@Composable
fun UpdateInfoDialog(releaseInfo: GitHubReleaseInfo, onDismiss: () -> Unit, context: Context) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本: ${releaseInfo.tagName}") },
        text = {
            SelectionContainer {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(releaseInfo.body.ifEmpty { "暂无更新日志" })
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, releaseInfo.htmlUrl.toUri())
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "打开失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) {
                    Text("前往项目页面")
                }
                val downloadUrl = releaseInfo.assets.firstOrNull()?.browserDownloadUrl
                if (downloadUrl != null) {
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, downloadUrl.toUri())
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "下载链接打开失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }) {
                        Text("立刻下载")
                    }
                }
            }
        }//,
        //dismissButton = {
        //    Button(onClick = onDismiss) {
        //        Text("暂不更新")
        //    }
        //}
    )
}

@Composable
fun RNHooksCard(sharedPreferences: SharedPreferences) {
    val context = LocalContext.current
    var rnInjectEnable by remember { mutableStateOf(sharedPreferences.getBoolean("rn_inject_enable", false)) }
    var rnPatchEnable by remember { mutableStateOf(sharedPreferences.getBoolean("rn_patch_enable", false)) }

    val initialInjectRules = sharedPreferences.getString("rn_inject_rules", "[]") ?: "[]"
    val initialPatchRules = sharedPreferences.getString("rn_patch_rules", "[]") ?: "[]"

    val injectRulesList = remember { mutableStateListOf<InjectRule>().apply { addAll(parseInjectRules(initialInjectRules)) } }
    val patchRulesList = remember { mutableStateListOf<PatchRule>().apply { addAll(parsePatchRules(initialPatchRules)) } }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("RN JS层功能", style = MaterialTheme.typography.titleLarge)

            // Inject rules
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用注入")
                Spacer(Modifier.weight(1f))
                Switch(checked = rnInjectEnable, onCheckedChange = { rnInjectEnable = it })
            }
            injectRulesList.forEachIndexed { index, rule ->
                RuleEditor(rule = rule, onRuleChange = { injectRulesList[index] = it as InjectRule }, onDelete = { injectRulesList.removeAt(index) })
            }
            Button(onClick = { injectRulesList.add(InjectRule(true, "", "")) }) {
                Icon(painterResource(R.drawable.add_24px), contentDescription = "添加注入规则")
                Spacer(Modifier.width(4.dp))
                Text("添加注入规则")
            }

            // Patch rules
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用补丁")
                Spacer(Modifier.weight(1f))
                Switch(checked = rnPatchEnable, onCheckedChange = { rnPatchEnable = it })
            }
            patchRulesList.forEachIndexed { index, rule ->
                RuleEditor(rule = rule, onRuleChange = { patchRulesList[index] = it as PatchRule }, onDelete = { patchRulesList.removeAt(index) })
            }
            Button(onClick = { patchRulesList.add(PatchRule(true, "", "", "")) }) {
                Icon(painterResource(R.drawable.add_24px), contentDescription = "添加补丁规则")
                Spacer(Modifier.width(4.dp))
                Text("添加补丁规则")
            }

            // Save button
            Button(
                onClick = {
                    sharedPreferences.edit()
                        .putBoolean("rn_inject_enable", rnInjectEnable)
                        .putString("rn_inject_rules", injectRulesToJson(injectRulesList))
                        .putBoolean("rn_patch_enable", rnPatchEnable)
                        .putString("rn_patch_rules", patchRulesToJson(patchRulesList))
                        .apply()
                    savePreferencesToJson(context, sharedPreferences)
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("保存")
            }
        }
    }
}

@Composable
fun RuleEditor(rule: Any, onRuleChange: (Any) -> Unit, onDelete: () -> Unit) {
    when (rule) {
        is InjectRule -> {
            var enabled by remember { mutableStateOf(rule.enabled) }
            var filename by remember { mutableStateOf(TextFieldValue(rule.filename)) }
            var code by remember { mutableStateOf(TextFieldValue(rule.code)) }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it; onRuleChange(rule.copy(enabled = it)) })
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = filename, onValueChange = { filename = it; onRuleChange(rule.copy(filename = it.text)) }, label = { Text("文件名") }, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDelete) {
                        Icon(painterResource(R.drawable.delete_24px), contentDescription = "删除规则")
                    }
                }
                OutlinedTextField(value = code, onValueChange = { code = it; onRuleChange(rule.copy(code = it.text)) }, label = { Text("注入代码") }, modifier = Modifier.fillMaxWidth().height(100.dp))
            }
        }
        is PatchRule -> {
            var enabled by remember { mutableStateOf(rule.enabled) }
            var filename by remember { mutableStateOf(TextFieldValue(rule.filename)) }
            var regex by remember { mutableStateOf(TextFieldValue(rule.regex)) }
            var replacement by remember { mutableStateOf(TextFieldValue(rule.replacement)) }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it; onRuleChange(rule.copy(enabled = it)) })
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = filename, onValueChange = { filename = it; onRuleChange(rule.copy(filename = it.text)) }, label = { Text("文件名") }, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDelete) {
                        Icon(painterResource(R.drawable.delete_24px), contentDescription = "删除规则")
                    }
                }
                OutlinedTextField(value = regex, onValueChange = { regex = it; onRuleChange(rule.copy(regex = it.text)) }, label = { Text("正则") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = replacement, onValueChange = { replacement = it; onRuleChange(rule.copy(replacement = it.text)) }, label = { Text("替换为") }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

fun parseInjectRules(json: String): List<InjectRule> {
    val rules = mutableListOf<InjectRule>()
    try {
        val jsonArray = JSONArray(json)
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            rules.add(
                InjectRule(
                    enabled = jsonObject.optBoolean("enabled", true),
                    filename = jsonObject.optString("filename", ""),
                    code = jsonObject.optString("code", "")
                )
            )
        }
    } catch (e: JSONException) {
        // Log error or handle
    }
    return rules
}

fun parsePatchRules(json: String): List<PatchRule> {
    val rules = mutableListOf<PatchRule>()
    try {
        val jsonArray = JSONArray(json)
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            rules.add(
                PatchRule(
                    enabled = jsonObject.optBoolean("enabled", true),
                    filename = jsonObject.optString("filename", ""),
                    regex = jsonObject.optString("regex", ""),
                    replacement = jsonObject.optString("replacement", "")
                )
            )
        }
    } catch (e: JSONException) {
        // Log error or handle
    }
    return rules
}

fun injectRulesToJson(rules: List<InjectRule>): String {
    val jsonArray = JSONArray()
    for (rule in rules) {
        val jsonObject = JSONObject()
        jsonObject.put("enabled", rule.enabled)
        jsonObject.put("filename", rule.filename)
        jsonObject.put("code", rule.code)
        jsonArray.put(jsonObject)
    }
    return jsonArray.toString()
}

fun patchRulesToJson(rules: List<PatchRule>): String {
    val jsonArray = JSONArray()
    for (rule in rules) {
        val jsonObject = JSONObject()
        jsonObject.put("enabled", rule.enabled)
        jsonObject.put("filename", rule.filename)
        jsonObject.put("regex", rule.regex)
        jsonObject.put("replacement", rule.replacement)
        jsonArray.put(jsonObject)
    }
    return jsonArray.toString()
}

@Composable
fun SystemInfoCard(sharedPreferences: SharedPreferences) {
    var androidVersionText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("android_version", "") ?: "")) }
    var brandText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("brand", "") ?: "")) }
    var modelText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("model", "") ?: "")) }
    var productText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("product", "") ?: "")) }
    var manufacturerText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("manufacturer", "") ?: "")) }
    var hardwareText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("hardware", "") ?: "")) }
    var fingerprintText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("fingerprint", "") ?: "")) }
    var displayText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("display", "") ?: "")) }
    var boardText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("board", "") ?: "")) }
    var deviceInfoText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("device_info", "") ?: "")) }
    var deviceNameText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("device_name", "") ?: "")) }
    var serialNumberText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("serial_number", "") ?: "")) }
//    var bootloaderText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("bootloader", "") ?: "")) }
//    var hostText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("host", "") ?: "")) }
//    var idText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("id", "") ?: "")) }
//    var tagsText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("tags", "") ?: "")) }
//    var typeText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("type", "") ?: "")) }
//    var userText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("user", "") ?: "")) }
//    var timeText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("time", "") ?: "")) }
//    var codenameText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("codename", "") ?: "")) }
//    var incrementalText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("incremental", "") ?: "")) }
    //var sdkIntText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("sdk_int", "") ?: "")) }
    var basebandText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("baseband", "") ?: "")) }
    var kernelVersionText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("kernel_version", "") ?: "")) }
    var androidIdText by remember { mutableStateOf(TextFieldValue(sharedPreferences.getString("android_id", "") ?: "")) }
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("设备特征", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = androidVersionText,
                onValueChange = { androidVersionText = it },
                label = { Text("Android版本 (RELEASE)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = brandText,
                onValueChange = { brandText = it },
                label = { Text("品牌 (BRAND)") },
                modifier = Modifier.fillMaxWidth()
            )
//            OutlinedTextField(
//                value = modelText,
//                onValueChange = { modelText = it },
//                label = { Text("型号 (MODEL)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = productText,
//                onValueChange = { productText = it },
//                label = { Text("产品 (PRODUCT)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = manufacturerText,
//                onValueChange = { manufacturerText = it },
//                label = { Text("制造商 (MANUFACTURER)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = hardwareText,
//                onValueChange = { hardwareText = it },
//                label = { Text("硬件 (HARDWARE)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = fingerprintText,
//                onValueChange = { fingerprintText = it },
//                label = { Text("设备指纹 (FINGERPRINT)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = displayText,
//                onValueChange = { displayText = it },
//                label = { Text("显示 (DISPLAY)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = boardText,
//                onValueChange = { boardText = it },
//                label = { Text("主板 (BOARD)") },
//                modifier = Modifier.fillMaxWidth()
//            )
            OutlinedTextField(
                value = deviceInfoText,
                onValueChange = { deviceInfoText = it },
                label = { Text("设备 (DEVICE)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = deviceNameText,
                onValueChange = { deviceNameText = it },
                label = { Text("设备名称 (RNDeviceModule)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = serialNumberText,
                onValueChange = { serialNumberText = it },
                label = { Text("序列号 (SERIAL)") },
                modifier = Modifier.fillMaxWidth()
            )
//            OutlinedTextField(
//                value = bootloaderText,
//                onValueChange = { bootloaderText = it },
//                label = { Text("引导程序 (BOOTLOADER)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = hostText,
//                onValueChange = { hostText = it },
//                label = { Text("主机 (HOST)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = idText,
//                onValueChange = { idText = it },
//                label = { Text("ID") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = tagsText,
//                onValueChange = { tagsText = it },
//                label = { Text("标签 (TAGS)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = typeText,
//                onValueChange = { typeText = it },
//                label = { Text("类型 (TYPE)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = userText,
//                onValueChange = { userText = it },
//                label = { Text("用户 (USER)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = timeText,
//                onValueChange = { timeText = it },
//                label = { Text("时间 (TIME)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = codenameText,
//                onValueChange = { codenameText = it },
//                label = { Text("代号 (CODENAME)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = incrementalText,
//                onValueChange = { incrementalText = it },
//                label = { Text("增量 (INCREMENTAL)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = sdkIntText,
//                onValueChange = { sdkIntText = it },
//                label = { Text("SDK整数 (SDK_INT和SDK)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = basebandText,
//                onValueChange = { basebandText = it },
//                label = { Text("基带版本 (Baseband)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            OutlinedTextField(
//                value = kernelVersionText,
//                onValueChange = { kernelVersionText = it },
//                label = { Text("内核版本 (Kernel Version)") },
//                modifier = Modifier.fillMaxWidth()
//            )
            OutlinedTextField(
                value = androidIdText,
                onValueChange = { androidIdText = it },
                label = { Text("Android ID") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    sharedPreferences.edit {
                        putString("android_version", androidVersionText.text)
                        putString("brand", brandText.text)
//                    putString("model", modelText.text)
//                    putString("product", productText.text)
//                    putString("manufacturer", manufacturerText.text)
//                    putString("hardware", hardwareText.text)
//                    putString("fingerprint", fingerprintText.text)
//                    putString("display", displayText.text)
//                    putString("board", boardText.text)
                        putString("device_info", deviceInfoText.text)
                        putString("device_name", deviceNameText.text)
                        putString("serial_number", serialNumberText.text)
//                    putString("bootloader", bootloaderText.text)
//                    putString("host", hostText.text)
//                    putString("id", idText.text)
//                    putString(
//                        "tags",
//                        tagsText.text
//                    )
//                    putString("type", typeText.text)
//                    putString("user", userText.text)
//                    putString("time", timeText.text)
//                    putString("codename", codenameText.text)
//                    putString("incremental", incrementalText.text)
//                    putString("sdk_int", sdkIntText.text)
//                    putString("baseband", basebandText.text)
//                    putString("kernel_version", kernelVersionText.text)
                        putString("android_id", androidIdText.text)
                    }
                    savePreferencesToJson(context, sharedPreferences)
                    //Toast.makeText(context, "设备特征已保存", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("保存")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GovernanceEnvironmentCard(sharedPreferences: SharedPreferences) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("Linspirer", "HEM", "Hangzhi")
    var selectedOptionText by remember { mutableStateOf(sharedPreferences.getString("governance_environment", options[0]) ?: options[0]) }
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("管控环境", style = MaterialTheme.typography.titleLarge)
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedOptionText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("选择环境") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                selectedOptionText = selectionOption
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
            Button(
                onClick = {
                    sharedPreferences.edit {
                        putString("governance_environment", selectedOptionText)
                    }
                    savePreferencesToJson(context, sharedPreferences)
                    //Toast.makeText(context, "管控环境已保存", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("保存")
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtilHooksCard(sharedPreferences: SharedPreferences) {
    val context = LocalContext.current
    var useDeveloperSupport by remember { mutableStateOf(sharedPreferences.getBoolean("utils_set_UseDeveloperSupport_true", false)) }
    var enableWebviewDebugging by remember { mutableStateOf(sharedPreferences.getBoolean("utils_enableWebviewDebugging", false)) }
    var isWifiProxy by remember { mutableStateOf(sharedPreferences.getBoolean("utils_set_isWifiProxy_false", false)) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("其他功能", style = MaterialTheme.typography.titleLarge)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("getUseDeveloperSupport->true")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = useDeveloperSupport,
                    onCheckedChange = { useDeveloperSupport = it }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("enableWebviewDebugging->true")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = enableWebviewDebugging,
                    onCheckedChange = { enableWebviewDebugging = it },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("移除代理检测")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = isWifiProxy,
                    onCheckedChange = { isWifiProxy = it }
                )
            }


            Button(
                onClick = {
                    sharedPreferences.edit {
                        putBoolean("utils_set_UseDeveloperSupport_true", useDeveloperSupport)
                            .putBoolean("utils_enableWebviewDebugging", enableWebviewDebugging)
                            .putBoolean("utils_set_isWifiProxy_false", isWifiProxy)
                    }
                    savePreferencesToJson(context, sharedPreferences)
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("保存")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectInfoCard() {
    val context = LocalContext.current
    val githubUrl = "https://github.com/fR0Z863xF/FuuleaEnvEmu/tree/v2"
    val projectDescription = "FuEmu是一款用于模拟特定环境的工具，旨在帮助开发者进行相关测试和开发。\n\n此项目仅为此目的提供此工具，请在遵守相关规定的情况下使用。\n\n点击卡片前往Github项目页面。"

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, githubUrl.toUri())
            context.startActivity(intent)
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("关于项目", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(projectDescription)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    FuEmuTheme {
        MainScreen()
    }
}
