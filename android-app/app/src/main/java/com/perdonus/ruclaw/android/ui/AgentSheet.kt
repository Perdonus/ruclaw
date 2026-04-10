@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.perdonus.ruclaw.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.perdonus.ruclaw.android.core.model.LauncherModelItem
import com.perdonus.ruclaw.android.core.model.LauncherSkillItem
import com.perdonus.ruclaw.android.core.model.LauncherSkillSearchItem
import com.perdonus.ruclaw.android.core.model.LauncherToolItem

@Composable
fun AgentSheet(
    state: MainUiState,
    viewModel: MainViewModel,
    onNewSession: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val catalog = state.launcherCatalog

    ModalBottomSheet(
        onDismissRequest = { viewModel.toggleAgentSheet(false) },
        sheetState = sheetState,
        containerColor = Color(0xFF111823),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    viewModel.toggleAgentSheet(false)
                    onNewSession()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Новый диалог")
            }

            if (!state.hasConfiguredLauncher) {
                SectionCard {
                    Text(
                        text = "Сначала укажи launcher URL и access token в настройках.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                ModelsSection(
                    catalog = catalog,
                    onSelectModel = viewModel::setDefaultLauncherModel,
                )
                SkillsSection(
                    catalog = catalog,
                    onSearchQueryChanged = viewModel::onSkillSearchQueryChanged,
                    onSearch = viewModel::searchLauncherSkills,
                    onInstall = viewModel::installLauncherSkill,
                    onUse = viewModel::useLauncherSkill,
                )
                ToolsSection(
                    catalog = catalog,
                    onToggleTool = viewModel::toggleLauncherTool,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ModelsSection(
    catalog: LauncherCatalogState,
    onSelectModel: (String) -> Unit,
) {
    val models = catalog.models.filterNot { it.isVirtual }

    SectionHeader(
        title = "Модели",
        subtitle = "Глобальный выбор модели по умолчанию для launcher.",
    )
    SectionCard {
        when {
            catalog.isLoading && models.isEmpty() -> {
                SectionLoading("Подгружаю модели…")
            }

            models.isEmpty() -> {
                EmptySectionText("Модели пока не подтянулись.")
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    models.forEach { model ->
                        ModelRow(
                            model = model,
                            isUpdating = catalog.updatingModelName == model.modelName,
                            onClick = { onSelectModel(model.modelName) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: LauncherModelItem,
    isUpdating: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (model.isDefault) Color(0x2D72D8C4) else Color(0x80182331),
        border = BorderStroke(
            width = if (model.isDefault) 1.5.dp else 1.dp,
            color = if (model.isDefault) Color(0xFF72D8C4) else Color(0x18FFFFFF),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isUpdating && !model.isDefault, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = model.modelName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(modelStatusText(model))
                        if (model.authMethod.isNotBlank()) {
                            append(" • ")
                            append(model.authMethod)
                        }
                    },
                    color = Color(0xFFB8C4D2),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when {
                isUpdating -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF72D8C4),
                )

                model.isDefault -> Text(
                    text = "Выбрана",
                    color = Color(0xFF72D8C4),
                    style = MaterialTheme.typography.labelLarge,
                )

                else -> Text(
                    text = "Выбрать",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun SkillsSection(
    catalog: LauncherCatalogState,
    onSearchQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onInstall: (LauncherSkillSearchItem) -> Unit,
    onUse: (LauncherSkillItem) -> Unit,
) {
    val canSearchMarketplace = catalog.tools.firstOrNull { it.name == "find_skills" }?.status == "enabled"
    val canInstallFromMarketplace = catalog.tools.firstOrNull { it.name == "install_skill" }?.status == "enabled"

    SectionHeader(
        title = "Навыки",
        subtitle = "Установленные навыки можно сразу подставить в запрос через /use.",
    )
    SectionCard {
        when {
            catalog.isLoading && catalog.skills.isEmpty() -> {
                SectionLoading("Подгружаю навыки…")
            }

            catalog.skills.isEmpty() -> {
                EmptySectionText("Навыков пока нет.")
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    catalog.skills.forEach { skill ->
                        InstalledSkillRow(
                            skill = skill,
                            onUse = { onUse(skill) },
                        )
                    }
                }
            }
        }
    }

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = catalog.skillSearchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Найти навык") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = agentFieldColors(),
            )

            if (!canSearchMarketplace || !canInstallFromMarketplace) {
                Text(
                    text = "Для поиска и установки включи find_skills и install_skill в разделе тулов ниже.",
                    color = Color(0xFFB8C4D2),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            FilledTonalButton(
                onClick = onSearch,
                enabled = catalog.skillSearchQuery.isNotBlank() && !catalog.isSearchingSkills && canSearchMarketplace,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            ) {
                if (catalog.isSearchingSkills) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF72D8C4),
                    )
                } else {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Найти и добавить")
                }
            }

            when {
                catalog.isSearchingSkills -> Unit
                catalog.skillSearchQuery.isBlank() -> Unit
                catalog.skillSearchResults.isEmpty() -> {
                    EmptySectionText("По этому запросу навыки не нашлись.")
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        catalog.skillSearchResults.forEach { item ->
                            SkillSearchRow(
                                item = item,
                                isInstalling = catalog.installingSkillSlug == item.slug,
                                canInstall = canInstallFromMarketplace,
                                onInstall = { onInstall(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledSkillRow(
    skill: LauncherSkillItem,
    onUse: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0x80182331),
        border = BorderStroke(1.dp, Color(0x18FFFFFF)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = skill.name,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            if (skill.description.isNotBlank()) {
                Text(
                    text = skill.description,
                    color = Color(0xFFB8C4D2),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append(skill.source)
                        if (skill.installedVersion.isNotBlank()) {
                            append(" • ")
                            append(skill.installedVersion)
                        }
                    },
                    color = Color(0xFF8FA2B6),
                    style = MaterialTheme.typography.labelMedium,
                )
                FilledTonalButton(
                    onClick = onUse,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Использовать")
                }
            }
        }
    }
}

@Composable
private fun SkillSearchRow(
    item: LauncherSkillSearchItem,
    isInstalling: Boolean,
    canInstall: Boolean,
    onInstall: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0x80182331),
        border = BorderStroke(1.dp, Color(0x18FFFFFF)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = item.displayName.ifBlank { item.slug },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(item.registryName)
                    if (item.version.isNotBlank()) {
                        append(" • ")
                        append(item.version)
                    }
                },
                color = Color(0xFF8FA2B6),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.summary.isNotBlank()) {
                Text(
                    text = item.summary,
                    color = Color(0xFFB8C4D2),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                when {
                    isInstalling -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF72D8C4),
                    )

                    item.installed -> Text(
                        text = "Установлен",
                        color = Color(0xFF72D8C4),
                        style = MaterialTheme.typography.labelLarge,
                    )

                    else -> FilledTonalButton(
                        onClick = onInstall,
                        enabled = canInstall,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Установить")
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsSection(
    catalog: LauncherCatalogState,
    onToggleTool: (LauncherToolItem, Boolean) -> Unit,
) {
    val groupedTools = catalog.tools.groupBy { it.category }
        .toSortedMap()

    SectionHeader(
        title = "Тулы",
        subtitle = "Эти переключатели меняют глобальную конфигурацию launcher.",
    )
    SectionCard {
        when {
            catalog.isLoading && catalog.tools.isEmpty() -> {
                SectionLoading("Подгружаю тулы…")
            }

            catalog.tools.isEmpty() -> {
                EmptySectionText("Тулы пока не подтянулись.")
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    groupedTools.entries.forEachIndexed { index, entry ->
                        if (index > 0) {
                            HorizontalDivider(color = Color(0x14FFFFFF))
                        }
                        Text(
                            text = toolCategoryTitle(entry.key),
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            entry.value.forEach { tool ->
                                ToolRow(
                                    tool = tool,
                                    isUpdating = catalog.updatingToolName == tool.name,
                                    onToggleTool = onToggleTool,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolRow(
    tool: LauncherToolItem,
    isUpdating: Boolean,
    onToggleTool: (LauncherToolItem, Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0x80182331),
        border = BorderStroke(1.dp, Color(0x18FFFFFF)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = tool.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tool.description,
                    color = Color(0xFFB8C4D2),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tool.reasonCode.isNotBlank()) {
                    Text(
                        text = toolReasonText(tool.reasonCode),
                        color = Color(0xFFFFB37A),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            when {
                isUpdating -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF72D8C4),
                )

                tool.status == "blocked" -> TextButton(
                    onClick = { onToggleTool(tool, true) },
                ) {
                    Text("Исправить")
                }

                else -> Switch(
                    checked = tool.status == "enabled",
                    onCheckedChange = { enabled -> onToggleTool(tool, enabled) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = subtitle,
            color = Color(0xFFB8C4D2),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SectionCard(
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xCC182231),
        border = BorderStroke(1.dp, Color(0x18FFFFFF)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SectionLoading(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = Color(0xFF72D8C4),
        )
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EmptySectionText(text: String) {
    Text(
        text = text,
        color = Color(0xFFB8C4D2),
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun modelStatusText(model: LauncherModelItem): String {
    return when {
        model.isDefault && model.available -> "По умолчанию"
        model.isDefault -> "По умолчанию • " + rawModelStatusText(model.status)
        else -> rawModelStatusText(model.status)
    }
}

private fun rawModelStatusText(status: String): String {
    return when (status) {
        "available" -> "Готова"
        "unconfigured" -> "Не настроена"
        "unreachable" -> "Недоступна"
        else -> if (status.isBlank()) "Статус неизвестен" else status
    }
}

private fun toolCategoryTitle(category: String): String {
    return when (category) {
        "filesystem" -> "Файлы"
        "automation" -> "Автоматизация"
        "web" -> "Веб"
        "communication" -> "Связь"
        "skills" -> "Навыки"
        "agents" -> "Агенты"
        "hardware" -> "Железо"
        "discovery" -> "Поиск"
        else -> category.replaceFirstChar { it.uppercase() }
    }
}

private fun toolReasonText(reasonCode: String): String {
    return when (reasonCode) {
        "requires_skills" -> "Нужно включить базовую поддержку skills."
        "requires_subagent" -> "Нужна поддержка subagent."
        "requires_linux" -> "Работает только на Linux."
        "requires_mcp_discovery" -> "Нужно включить discovery для MCP."
        else -> reasonCode
    }
}

@Composable
private fun agentFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = Color(0xFFB8C4D2),
    cursorColor = Color(0xFF72D8C4),
    focusedBorderColor = Color(0xFF72D8C4),
    unfocusedBorderColor = Color(0x33FFFFFF),
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedPlaceholderColor = Color(0x88FFFFFF),
    unfocusedPlaceholderColor = Color(0x66FFFFFF),
)
