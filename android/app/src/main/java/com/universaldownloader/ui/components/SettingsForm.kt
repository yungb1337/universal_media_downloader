package com.universaldownloader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.universaldownloader.ui.theme.*

/**
 * Settings toggle button — port of ToggleButton from desktop gui/widgets.py.
 * Same visual behavior: background changes to accent color when active.
 */
@Composable
fun SettingsToggle(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (checked) Accent else Panel,
        label = "toggleBg"
    )

    Button(
        onClick = { onToggle(!checked) },
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bgColor),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = "${if (checked) "✓" else "✗"}  $label",
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary
        )
    }
}

/**
 * Numeric input field for settings like max concurrent, max retries.
 */
@Composable
fun NumericField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Local state to allow smooth editing (e.g. deleting text)
    var localValue by remember(value) { mutableStateOf(value) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.width(130.dp)
        )

        OutlinedTextField(
            value = localValue,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() } && newValue.length <= 2) {
                    localValue = newValue
                    if (newValue.isNotEmpty()) {
                        onValueChange(newValue)
                    }
                }
            },
            modifier = Modifier.width(64.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Border,
                cursorColor = Accent,
                focusedContainerColor = Background,
                unfocusedContainerColor = Background,
            ),
            shape = RoundedCornerShape(8.dp),
        )
    }
}

/**
 * Dropdown selector for settings.
 */
@Composable
fun SettingsDropdown(
    label: String,
    value: String,
    options: List<String>,
    displayOptions: List<String> = options,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentIndex = options.indexOf(value).coerceAtLeast(0)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.width(130.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = BorderStroke(1.dp, Border)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayOptions.getOrElse(currentIndex) { value },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Accent
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Surface)
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(displayOptions[index], color = TextPrimary) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Path display field with a browse action button.
 */
@Composable
fun PathField(
    label: String,
    value: String,
    onClick: () -> Unit,
    buttonText: String = "Browse",
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.width(100.dp)
        )

        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier.weight(1f),
            readOnly = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.5f)
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Border,
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
            ),
            shape = RoundedCornerShape(8.dp),
        )

        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Panel),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary
            )
        }
    }
}
