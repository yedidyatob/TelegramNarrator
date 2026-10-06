package io.github.yedidyatob.telegramnarrator.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.yedidyatob.telegramnarrator.BuildConfig
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeVoiceGender
import io.github.yedidyatob.telegramnarrator.domain.openai.OpenAiTts
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsVoiceLogic
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceOption
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceQuality
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceSettingsLogic
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceSettingsSection
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.EdgeVoiceUiState
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.OpenAiVoiceUiState
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.SystemVoiceUiState
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.TtsSettingsUiState
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.TtsSettingsViewModel
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.VoiceGroup
import io.github.yedidyatob.telegramnarrator.ui.components.PrivacyPolicyLink

/**
 * Voice settings bottom sheet: **Engine** (System | Edge | OpenAI), then only the selected engine's own section,
 * then **Playback** (speech rate, mark as read). See docs/tts-voice-settings.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsSheet(
    onDismiss: () -> Unit,
    viewModel: TtsSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val ttsReady by viewModel.ttsReady.collectAsState()

    // Load when opened, and again whenever the system engine finished (re)starting
    LaunchedEffect(ttsReady) { viewModel.reload() }
    DisposableEffect(Unit) { onDispose { viewModel.stopTest() } }

    val actions = remember(viewModel) {
        VoiceSettingsActions(
            onSelectProvider = viewModel::setProvider,
            onSelectSystemEngine = viewModel::selectEngine,
            onSelectSystemVoice = viewModel::selectVoice,
            onTestSystem = viewModel::testSystemVoice,
            onSelectEdgeGender = viewModel::setEdgeGender,
            onSetEdgeCustomVoice = viewModel::setEdgeCustomVoice,
            onTestEdge = viewModel::testEdgeVoice,
            onSaveOpenAiKey = viewModel::saveOpenAiApiKey,
            onRemoveOpenAiKey = viewModel::removeOpenAiApiKey,
            onSelectOpenAiModel = viewModel::setOpenAiModel,
            onSelectOpenAiVoice = viewModel::setOpenAiVoice,
            onTestOpenAi = viewModel::testOpenAiVoice,
            onSpeechRate = viewModel::setSpeechRate,
            onMarkAsRead = viewModel::setMarkAsRead
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        VoiceSettingsContent(state = state, isPlaying = isPlaying, actions = actions)
    }
}

/** Callbacks of [VoiceSettingsContent]; the defaults do nothing (previews). */
class VoiceSettingsActions(
    val onSelectProvider: (SpeechProvider) -> Unit = {},
    val onSelectSystemEngine: (String?) -> Unit = {},
    val onSelectSystemVoice: (language: String, voiceName: String?) -> Unit = { _, _ -> },
    val onTestSystem: () -> Unit = {},
    val onSelectEdgeGender: (EdgeVoiceGender) -> Unit = {},
    /** Returns false when the name is not a valid Edge voice; blank clears the custom voice. */
    val onSetEdgeCustomVoice: (String?) -> Boolean = { true },
    val onTestEdge: () -> Unit = {},
    val onSaveOpenAiKey: (String) -> Unit = {},
    val onRemoveOpenAiKey: () -> Unit = {},
    val onSelectOpenAiModel: (String) -> Unit = {},
    val onSelectOpenAiVoice: (String) -> Unit = {},
    val onTestOpenAi: () -> Unit = {},
    val onSpeechRate: (Float) -> Unit = {},
    val onMarkAsRead: (Boolean) -> Unit = {}
)

/** Stateless sheet content (also used by the previews). */
@Composable
fun VoiceSettingsContent(
    state: TtsSettingsUiState,
    isPlaying: Boolean,
    actions: VoiceSettingsActions,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        Text(
            stringResource(R.string.settings_voice_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        if (isPlaying) {
            Text(
                stringResource(R.string.settings_voice_playing_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        state.sections.forEach { section ->
            when (section) {
                VoiceSettingsSection.ENGINE -> EngineSection(state.provider, isPlaying, actions.onSelectProvider)
                VoiceSettingsSection.SYSTEM_VOICES -> SystemSection(state.system, isPlaying, actions)
                VoiceSettingsSection.EDGE_VOICE -> EdgeSection(state.edge, isPlaying, actions)
                VoiceSettingsSection.OPENAI_VOICE -> OpenAiSection(state.openAi, isPlaying, actions)
                VoiceSettingsSection.PLAYBACK -> PlaybackSection(state.speechRate, state.markAsRead, actions)
            }
        }
        AboutSection()
    }
}

// ---- Sections -------------------------------------------------------------------------------------

@Composable
private fun EngineSection(provider: SpeechProvider, isPlaying: Boolean, onSelect: (SpeechProvider) -> Unit) {
    SettingsSection(R.string.settings_section_engine) {
        Column(Modifier.selectableGroup()) {
            ENGINE_ROWS.forEach { (engine, title, subtitle) ->
                RadioRow(
                    label = stringResource(title),
                    supporting = stringResource(subtitle),
                    selected = provider == engine,
                    enabled = !isPlaying,
                    onClick = { onSelect(engine) }
                )
            }
        }
    }
}

@Composable
private fun SystemSection(system: SystemVoiceUiState, isPlaying: Boolean, actions: VoiceSettingsActions) {
    val context = LocalContext.current
    SettingsSection(R.string.settings_section_system) {
        if (system.loading) {
            LoadingRow(stringResource(R.string.settings_system_loading))
            return@SettingsSection
        }
        val enabled = !isPlaying
        if (system.engines.isNotEmpty()) {
            val defaultLabel = stringResource(R.string.settings_system_engine_default)
            Dropdown(
                label = stringResource(R.string.settings_system_engine),
                value = system.engines.firstOrNull { it.packageName == system.selectedEngine }?.label ?: defaultLabel,
                enabled = enabled,
                options = listOf(DropdownOption<String?>(null, defaultLabel)) +
                    system.engines.map { DropdownOption(it.packageName, it.label) },
                selected = system.selectedEngine,
                onSelect = actions.onSelectSystemEngine
            )
        }
        system.groups.forEach { group -> SystemVoicePicker(group, enabled, actions.onSelectSystemVoice) }
        HelperText(stringResource(R.string.settings_system_offline_note))
        TextButton(
            onClick = {
                try {
                    context.startActivity(Intent(TTS_SETTINGS_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, context.getString(R.string.settings_system_tts_unavailable), Toast.LENGTH_SHORT).show()
                }
            }
        ) { Text(stringResource(R.string.settings_system_open_tts)) }
        TestVoiceRow(
            enabled = VoiceSettingsLogic.canTestSystem(isPlaying, systemTtsReady = !system.loading),
            testing = false,
            onTest = actions.onTestSystem
        )
    }
}

@Composable
private fun SystemVoicePicker(group: VoiceGroup, enabled: Boolean, onSelect: (String, String?) -> Unit) {
    val label = stringResource(R.string.settings_system_voice_for, group.displayName)
    if (group.voices.isEmpty()) {
        Dropdown(
            label = label,
            value = stringResource(R.string.settings_system_none_installed),
            enabled = false,
            options = emptyList<DropdownOption<String?>>(),
            selected = null,
            onSelect = {}
        )
        return
    }
    val defaultLabel = stringResource(R.string.settings_system_voice_default)
    Dropdown(
        label = label,
        value = group.selectedVoice ?: defaultLabel,
        enabled = enabled,
        options = listOf(DropdownOption<String?>(null, defaultLabel)) +
            group.voices.map { DropdownOption(it.name, it.name, qualityText(it)) },
        selected = group.selectedVoice,
        onSelect = { onSelect(group.language, it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EdgeSection(edge: EdgeVoiceUiState, isPlaying: Boolean, actions: VoiceSettingsActions) {
    SettingsSection(R.string.settings_section_edge) {
        FieldLabel(R.string.settings_edge_gender)
        val genderEnabled = VoiceSettingsLogic.edgeGenderEnabled(isPlaying, edge.customVoice)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH)) {
            EDGE_GENDERS.forEachIndexed { index, (gender, label) ->
                SegmentedButton(
                    selected = edge.gender == gender,
                    onClick = { actions.onSelectEdgeGender(gender) },
                    shape = SegmentedButtonDefaults.itemShape(index, EDGE_GENDERS.size),
                    enabled = genderEnabled
                ) { Text(stringResource(label)) }
            }
        }
        HelperText(
            if (edge.customVoice != null) stringResource(R.string.settings_edge_custom_active, edge.customVoice)
            else stringResource(R.string.settings_edge_voices_desc)
        )
        TestVoiceRow(
            enabled = VoiceSettingsLogic.canTestEdge(isPlaying, edge.testing),
            testing = edge.testing,
            onTest = actions.onTestEdge
        )
        HelperText(stringResource(R.string.settings_edge_info))
        EdgeAdvanced(edge.customVoice, enabled = !isPlaying, onSetCustomVoice = actions.onSetEdgeCustomVoice)
    }
}

/** Collapsed "Advanced" expander with the custom Edge voice name; auto-expanded while a custom voice is set. */
@Composable
private fun EdgeAdvanced(customVoice: String?, enabled: Boolean, onSetCustomVoice: (String?) -> Boolean) {
    var expanded by rememberSaveable { mutableStateOf(customVoice != null) }
    LaunchedEffect(customVoice) { if (customVoice != null) expanded = true }
    val stateText = stringResource(if (expanded) R.string.settings_state_expanded else R.string.settings_state_collapsed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH)
            .clickable(role = Role.Button) { expanded = !expanded }
            .semantics { stateDescription = stateText },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.settings_edge_advanced),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
    }
    AnimatedVisibility(visible = expanded) {
        Column {
            var draft by remember(customVoice) { mutableStateOf(customVoice.orEmpty()) }
            var invalid by remember(customVoice) { mutableStateOf(false) }
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    invalid = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_edge_custom_voice)) },
                placeholder = { Text(stringResource(R.string.settings_edge_custom_voice_hint)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (invalid) R.string.settings_edge_custom_voice_invalid
                            else R.string.settings_edge_custom_voice_desc
                        )
                    )
                },
                isError = invalid,
                singleLine = true,
                enabled = enabled
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { invalid = !onSetCustomVoice(draft.trim()) },
                    enabled = enabled && draft.isNotBlank() && draft.trim() != customVoice
                ) { Text(stringResource(R.string.settings_edge_custom_voice_use)) }
                OutlinedButton(
                    onClick = { onSetCustomVoice(null) },
                    enabled = enabled && customVoice != null
                ) { Text(stringResource(R.string.settings_edge_custom_voice_clear)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenAiSection(openAi: OpenAiVoiceUiState, isPlaying: Boolean, actions: VoiceSettingsActions) {
    SettingsSection(R.string.settings_section_openai) {
        val enabled = !isPlaying
        // The typed key lives only here (not in the ViewModel / UI state) and is not saved in instance state
        var keyDraft by remember { mutableStateOf("") }
        var showKey by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = keyDraft,
            onValueChange = { keyDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_openai_api_key)) },
            placeholder = { Text(stringResource(R.string.settings_openai_api_key_hint)) },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(
                            if (showKey) R.string.settings_openai_hide_key else R.string.settings_openai_show_key
                        )
                    )
                }
            },
            singleLine = true,
            enabled = enabled
        )
        Text(
            if (openAi.keyHint != null) stringResource(R.string.settings_openai_key_saved, openAi.keyHint)
            else stringResource(R.string.settings_openai_key_none),
            style = MaterialTheme.typography.bodyMedium,
            color = if (openAi.hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Button(
                onClick = {
                    actions.onSaveOpenAiKey(keyDraft)
                    keyDraft = ""
                    showKey = false
                },
                enabled = enabled && keyDraft.isNotBlank()
            ) { Text(stringResource(R.string.settings_openai_save_key)) }
            OutlinedButton(
                onClick = actions.onRemoveOpenAiKey,
                enabled = enabled && openAi.hasKey
            ) { Text(stringResource(R.string.settings_openai_remove_key)) }
        }
        Spacer(Modifier.height(8.dp))
        FieldLabel(R.string.settings_openai_quality)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH)) {
            OPENAI_MODELS.forEachIndexed { index, (model, label) ->
                SegmentedButton(
                    selected = openAi.model == model,
                    onClick = { actions.onSelectOpenAiModel(model) },
                    shape = SegmentedButtonDefaults.itemShape(index, OPENAI_MODELS.size),
                    enabled = enabled
                ) { Text(stringResource(label)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Dropdown(
            label = stringResource(R.string.settings_openai_voice),
            value = openAi.voice,
            enabled = enabled,
            options = OpenAiTts.VOICES.map { DropdownOption(it, it) },
            selected = openAi.voice,
            onSelect = actions.onSelectOpenAiVoice
        )
        TestVoiceRow(
            enabled = VoiceSettingsLogic.canTestOpenAi(isPlaying, openAi.testing, openAi.hasKey),
            testing = openAi.testing,
            onTest = actions.onTestOpenAi,
            hint = if (!openAi.hasKey) stringResource(R.string.settings_openai_test_needs_key) else null
        )
        HelperText(stringResource(R.string.settings_openai_cost_privacy))
    }
}

@Composable
private fun PlaybackSection(speechRate: Float, markAsRead: Boolean, actions: VoiceSettingsActions) {
    SettingsSection(R.string.settings_section_playback) {
        val rateLabel = stringResource(R.string.settings_rate)
        val rateValue = stringResource(R.string.settings_rate_value, TtsVoiceLogic.clampRate(speechRate))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(rateLabel, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(rateValue, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = speechRate,
            onValueChange = actions.onSpeechRate,
            valueRange = TtsVoiceLogic.MIN_RATE..TtsVoiceLogic.MAX_RATE,
            steps = Math.round((TtsVoiceLogic.MAX_RATE - TtsVoiceLogic.MIN_RATE) / TtsVoiceLogic.RATE_STEP) - 1,
            modifier = Modifier.semantics {
                contentDescription = rateLabel
                stateDescription = rateValue
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TOUCH)
                .toggleable(value = markAsRead, role = Role.Switch, onValueChange = actions.onMarkAsRead)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.settings_mark_read), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_mark_read_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = markAsRead, onCheckedChange = null)
        }
    }
}

/** Unofficial-app notice (Telegram API Terms of Service 2.2), app version and the privacy policy link. */
@Composable
private fun AboutSection() {
    SettingsSection(R.string.settings_section_about) {
        HelperText(stringResource(R.string.settings_about_unofficial))
        HelperText(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME))
        PrivacyPolicyLink()
    }
}

// ---- Building blocks ------------------------------------------------------------------------------

/** Divider + title (a heading for accessibility) + content. */
@Composable
private fun SettingsSection(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    HorizontalDivider(Modifier.padding(top = 16.dp))
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 12.dp, bottom = 4.dp)
            .semantics { heading() }
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

@Composable
private fun RadioRow(label: String, supporting: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class DropdownOption<T>(val value: T, val label: String, val supporting: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Dropdown(
    label: String,
    value: String,
    enabled: Boolean,
    options: List<DropdownOption<T>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val canOpen = enabled && options.isNotEmpty()
    ExposedDropdownMenuBox(expanded = expanded && canOpen, onExpandedChange = { if (canOpen) expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = canOpen,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && canOpen) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded && canOpen, onDismissRequest = { expanded = false }) {
            val selectedText = stringResource(R.string.settings_voice_selected)
            options.forEach { option ->
                val isSelected = option.value == selected
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.label)
                            option.supporting?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = selectedText) }
                    } else null,
                    onClick = {
                        expanded = false
                        if (!isSelected) onSelect(option.value)
                    },
                    modifier = Modifier.heightIn(min = MIN_TOUCH),
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
private fun TestVoiceRow(enabled: Boolean, testing: Boolean, onTest: () -> Unit, hint: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onTest, enabled = enabled) { Text(stringResource(R.string.settings_voice_test)) }
        if (testing) {
            val loading = stringResource(R.string.settings_voice_testing)
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp).semantics { contentDescription = loading },
                strokeWidth = 2.dp
            )
        } else if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FieldLabel(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HelperText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun qualityText(voice: VoiceOption): String = when (TtsVoiceLogic.qualityOf(voice)) {
    VoiceQuality.HIGH -> stringResource(R.string.settings_system_quality_high, voice.localeTag)
    VoiceQuality.NORMAL -> stringResource(R.string.settings_system_quality_normal, voice.localeTag)
    VoiceQuality.BASIC -> stringResource(R.string.settings_system_quality_basic, voice.localeTag)
}

private val MIN_TOUCH = 48.dp

private val ENGINE_ROWS = listOf(
    Triple(SpeechProvider.SYSTEM, R.string.settings_engine_system, R.string.settings_engine_system_desc),
    Triple(SpeechProvider.EDGE, R.string.settings_engine_edge, R.string.settings_engine_edge_desc),
    Triple(SpeechProvider.OPENAI, R.string.settings_engine_openai, R.string.settings_engine_openai_desc)
)

private val EDGE_GENDERS = listOf(
    EdgeVoiceGender.MALE to R.string.settings_edge_gender_male,
    EdgeVoiceGender.FEMALE to R.string.settings_edge_gender_female
)

private val OPENAI_MODELS = listOf(
    OpenAiTts.MODEL_TTS_1 to R.string.settings_openai_model_standard,
    OpenAiTts.MODEL_TTS_1_HD to R.string.settings_openai_model_hd
)

// There is no Settings.ACTION_* constant for the system text-to-speech settings; this is the action string
// the platform Settings app handles.
private const val TTS_SETTINGS_ACTION = "com.android.settings.TTS_SETTINGS"
