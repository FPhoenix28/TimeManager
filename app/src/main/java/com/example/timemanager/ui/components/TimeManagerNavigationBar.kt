package com.example.timemanager.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.example.timemanager.R

enum class PrimaryDestination(
    val label: String,
    @param:DrawableRes val iconResource: Int,
) {
    HOME("首页", R.drawable.ic_home),
    STATISTICS("统计", R.drawable.ic_statistics),
    MANAGEMENT("管理", R.drawable.ic_apps),
}

@Composable
fun TimeManagerNavigationBar(
    selected: PrimaryDestination,
    onSelect: (PrimaryDestination) -> Unit,
) {
    NavigationBar {
        PrimaryDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconResource),
                        contentDescription = destination.label,
                    )
                },
                label = { Text(destination.label) },
            )
        }
    }
}
