package com.powerampremote.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/** Mesmo padrão da Biblioteca; a altura cresce com o texto e a fonte do usuário. */
@Composable
internal fun CompactActionButton(label: String, symbol: String?, modifier: Modifier = Modifier,
                                 enabled: Boolean = true, prominent: Boolean = false,
                                 selected: Boolean? = null, subtitle: String? = null,
                                 onClick: () -> Unit) {
    val highlighted = prominent || selected == true
    val foreground = when {
        !enabled -> Color(0xFF7D8794)
        highlighted -> Color(0xFF72B7FF)
        else -> Color(0xFFD1DAE7)
    }
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
        .background(if (highlighted) Color(0xFF1D2938) else Color(0xFF111923))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { selected?.let { this.selected = it } }
        .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val marker = if (selected == true) "✓" else symbol
        if (marker != null) Text(marker,
            modifier = Modifier.width(20.dp).clearAndSetSemantics { },
            color = foreground, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = foreground, style = MaterialTheme.typography.bodySmall,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal)
            if (subtitle != null) Text(subtitle, color = foreground,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
