package com.example.timemanager.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import com.example.timemanager.ui.theme.TimeManagerSpacing

@Composable
internal fun ReadOnlyTodoRow(
    title: String,
    emphasized: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
        Text(
            "•",
            modifier = Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.bodyMedium,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
