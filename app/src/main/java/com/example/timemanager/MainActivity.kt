package com.example.timemanager

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import com.example.timemanager.data.repository.UsageStatsReader
import com.example.timemanager.data.repository.UsageRepository
import com.example.timemanager.ui.theme.TimeManagerTheme
import com.example.timemanager.ui.home.HomeScreen
import com.example.timemanager.ui.home.HomeViewModel
import com.example.timemanager.utils.UsagePermissionManager

class MainActivity : ComponentActivity() {
    private var hasUsageAccess by mutableStateOf(false)
    private lateinit var homeViewModel: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        logUsageAccessStatus()
        homeViewModel = ViewModelProvider(
            this,
            HomeViewModel.Factory(
                UsageRepository(UsageStatsReader(applicationContext)),
            ),
        )[HomeViewModel::class.java]
        setContent {
            TimeManagerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (hasUsageAccess) {
                        HomeScreen(
                            viewModel = homeViewModel,
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        UsageAccessScreen(
                            hasUsageAccess = false,
                            onOpenUsageAccessSettings = ::openUsageAccessSettings,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        logUsageAccessStatus()
        if (hasUsageAccess) {
            homeViewModel.loadTodayUsage()
        }
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun logUsageAccessStatus() {
        hasUsageAccess = UsagePermissionManager.hasUsageAccess(this)
        Log.d(TAG, "packageName=$packageName")
        Log.d(TAG, "hasUsageAccess=$hasUsageAccess")
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

@Composable
fun UsageAccessScreen(
    hasUsageAccess: Boolean,
    onOpenUsageAccessSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hasUsageAccess) {
        Text(
            text = "已开启使用情况访问权限",
            modifier = modifier,
        )
    } else {
        Button(
            onClick = onOpenUsageAccessSettings,
            modifier = modifier,
        ) {
            Text("开启使用情况访问权限")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UsageAccessScreenPreview() {
    TimeManagerTheme {
        UsageAccessScreen(
            hasUsageAccess = false,
            onOpenUsageAccessSettings = {},
        )
    }
}
