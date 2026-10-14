package io.github.joestataka.frptunnel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.joestataka.frptunnel.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                TunnelScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("frp_tunnel", Context.MODE_PRIVATE) }

    var command by remember {
        mutableStateOf(
            prefs.getString("last_command", "") ?: ""
        )
    }
    val logs by TunnelState.logs.collectAsState()
    val running by TunnelState.running.collectAsState()
    val exitInfo by TunnelState.exitInfo.collectAsState()

    // Android 13+ 通知权限（前台服务通知需要）
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("FRP 隧道") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 状态条
            StatusBar(running = running, exitInfo = exitInfo)

            // 命令输入框
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("粘贴 frp-panel 启动指令") },
                placeholder = {
                    Text("frp-panel client -s <secret> -i <id> --api-url ... --rpc-url ...")
                },
                minLines = 3,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )

            // 按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        prefs.edit().putString("last_command", command).apply()
                        startTunnel(context, command)
                    },
                    enabled = !running,
                    modifier = Modifier.weight(1f)
                ) { Text("打通隧道") }

                OutlinedButton(
                    onClick = { stopTunnel(context) },
                    enabled = running,
                    modifier = Modifier.weight(1f)
                ) { Text("停止") }

                OutlinedButton(
                    onClick = { TunnelState.clear() },
                    modifier = Modifier.weight(1f)
                ) { Text("清空") }
            }

            // 日志
            Text(
                "运行日志",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            LogView(logs = logs, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusBar(running: Boolean, exitInfo: String?) {
    val bg = if (running) Color(0xFF1B5E20) else Color(0xFF424242)
    val text = when {
        running -> "● 隧道运行中"
        exitInfo != null -> "○ 已停止（$exitInfo）"
        else -> "○ 未启动"
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LogView(logs: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.scrollToItem(logs.size - 1)
        }
    }
    Surface(
        color = Color(0xFF101418),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        if (logs.isEmpty()) {
            Text(
                "暂无日志，粘贴指令后点击「打通隧道」",
                color = Color(0xFF9E9E9E),
                fontSize = 13.sp,
                modifier = Modifier.padding(12.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(8.dp)
            ) {
                items(logs) { line ->
                    Text(
                        text = line,
                        color = logColor(line),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

private fun logColor(line: String): Color = when {
    line.contains("[错误]") || line.contains("[异常]") -> Color(0xFFFF6E6E)
    line.contains("error", true) || line.contains("fail", true) -> Color(0xFFFFB74D)
    line.contains("success", true) -> Color(0xFF81C784)
    else -> Color(0xFFE0E0E0)
}

private fun startTunnel(context: Context, raw: String) {
    val result = CommandParser.parse(raw)
    if (result.error != null) {
        TunnelState.append("[输入错误] ${result.error}")
        TunnelState.exitInfo.value = result.error
        return
    }
    val intent = Intent(context, TunnelService::class.java).apply {
        action = TunnelService.ACTION_START
        putStringArrayListExtra(TunnelService.EXTRA_ARGS, ArrayList(result.args))
    }
    ContextCompat.startForegroundService(context, intent)
}

private fun stopTunnel(context: Context) {
    val intent = Intent(context, TunnelService::class.java).apply {
        action = TunnelService.ACTION_STOP
    }
    context.startService(intent)
}