package com.orchords.orchordsai.ui.components.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import me.orchid.hugeicons.HugeIcons
import me.orchid.hugeicons.stroke.Add01
import me.orchid.hugeicons.stroke.Cancel01
import com.orchords.orchordsai.R
import com.orchords.orchordsai.data.model.Tag
import kotlin.uuid.Uuid

@Composable
fun TagsInput(
    value: List<Uuid>,
    tags: List<Tag>,
    modifier: Modifier = Modifier,
    onValueChange: (value: List<Uuid>, tags: List<Tag>) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    val selectedTags = tags.filter { tag -> value.contains(tag.id) }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        selectedTags.fastForEach { tag ->
            InputChip(onClick = {}, label = {
                Text(tag.name)
            }, selected = false, trailingIcon = {
                Icon(
                    imageVector = HugeIcons.Cancel01,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable {
                            onValueChange(
                                value.filter { it != tag.id }, tags
                            )
                        },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            })
        }

        Surface(
            shape = CircleShape,
            tonalElevation = 2.dp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { showAddDialog = true }) {
            Icon(
                imageVector = HugeIcons.Add01,
                contentDescription = stringResource(R.string.add),
                modifier = Modifier
                    .padding(6.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (showAddDialog) {
        var tagName by remember { mutableStateOf("") }
        var showError by remember { mutableStateOf(false) }

        val unselectedTags = tags.filter { tag -> !value.contains(tag.id) }

        AlertDialog(onDismissRequest = {
            showAddDialog = false
            tagName = ""
            showError = false
        }, title = {
            Text(stringResource(R.string.tag_input_dialog_title))
        }, text = {
            Column {
                if (unselectedTags.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.tag_input_dialog_existing_tags),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        unselectedTags.forEach { tag ->
                            InputChip(
                                onClick = {
                                    onValueChange(value + tag.id, tags)
                                    showAddDialog = false
                                    tagName = ""
                                    showError = false
                                }, label = { Text(tag.name) }, selected = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.tag_input_dialog_create_new),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = tagName,
                    onValueChange = {
                        tagName = it
                        showError = false
                    },
                    label = { Text(stringResource(R.string.tag_input_dialog_label)) },
                    placeholder = { Text(stringResource(R.string.tag_input_dialog_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = showError
                )

                if (showError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.tag_input_dialog_tag_exists),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }, confirmButton = {
            TextButton(
                onClick = {
                    if (tagName.isNotBlank()) {
                        val trimmedName = tagName.trim()
                        val existingTag =
                            tags.find { it.name.equals(trimmedName, ignoreCase = true) }
                        if (existingTag != null) {
                            showError = true
                        } else {
                            val newTag = Tag(id = Uuid.random(), name = trimmedName)
                            onValueChange(value + newTag.id, tags + newTag)
                            showAddDialog = false
                            tagName = ""
                            showError = false
                        }
                    }
                }, enabled = tagName.isNotBlank()
            ) {
                Text(stringResource(R.string.confirm))
            }
        }, dismissButton = {
            TextButton(
                onClick = {
                    showAddDialog = false
                    tagName = ""
                    showError = false
                }) {
                Text(stringResource(R.string.cancel))
            }
        })
    }
}
