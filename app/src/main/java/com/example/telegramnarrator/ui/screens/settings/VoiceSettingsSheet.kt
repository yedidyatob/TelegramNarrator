package com.example.telegramnarrator.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.telegramnarrator.R
import com.example.telegramnarrator.domain.tts.TtsVoiceLogic
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.telegramnarrator.domain.openai.OpenAiTts
import com.example.telegramnarrator.ui.viewmodel.TtsSettingsViewModel

/**
 * Voice settings: speech engine, speech rate, and one voice per language (offline voices only), with a
 * "Test voice" button per language and a shortcut to the system text-to-speech settings.
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
    val context = LocalContext.current

    // Load when opened, and again whenever the engine finished (re)starting
    LaunchedEffect(ttsReady) { viewModel.reload() }
    DisposableEffect(Unit) { onDispose { viewModel.stopTest() } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_voice_loading))
            }
            return@ModalBottomSheet
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                Text(stringResource(R.string.settings_voice_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_voice_offline_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isPlaying) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_voice_playing_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(16.dp))

                // Speech rate
                Text(
                    stringResource(R.string.settings_voice_rate, TtsVoiceLogic.rateLabel(state.speechRate)),
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = state.speechRate,
                    onValueChange = { viewModel.setSpeechRate(it) },
                    valueRange = TtsVoiceLogic.MIN_RATE..TtsVoiceLogic.MAX_RATE,
                    steps = Math.round((TtsVoiceLogic.MAX_RATE - TtsVoiceLogic.MIN_RATE) / TtsVoiceLogic.RATE_STEP) - 1
                )
                Spacer(Modifier.height(8.dp))

                // Mark as read in Telegram (default ON; critical for unread badge / replay-from-start)
                Text(stringResource(R.string.settings_mark_read), style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.settings_mark_read_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Switch(
                        checked = state.markAsRead,
                        onCheckedChange = { viewModel.setMarkAsRead(it) }
                    )
                }
                Spacer(Modifier.height(8.dp))

                // OpenAI TTS (optional BYOK)
                Text(stringResource(R.string.settings_openai_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_openai_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.settings_openai_enable),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Switch(
                        checked = state.openAiEnabled,
                        onCheckedChange = { viewModel.setOpenAiEnabled(it) },
                        enabled = !isPlaying
                    )
                }
                if (state.openAiEnabled) {
                    var keyDraft by remember(state.openAiHasKey, state.openAiKeyMasked) {
                        mutableStateOf("")
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { keyDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_openai_api_key)) },
                        placeholder = {
                            Text(
                                if (state.openAiHasKey) state.openAiKeyMasked
                                else stringResource(R.string.settings_openai_api_key_hint)
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !isPlaying
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.setOpenAiApiKey(keyDraft)
                                keyDraft = ""
                            },
                            enabled = !isPlaying && keyDraft.isNotBlank()
                        ) { Text(stringResource(R.string.settings_openai_save_key)) }
                        if (state.openAiHasKey) {
                            OutlinedButton(
                                onClick = { viewModel.clearOpenAiApiKey() },
                                enabled = !isPlaying
                            ) { Text(stringResource(R.string.settings_openai_clear_key)) }
                        }
                    }
                    Text(
                        stringResource(R.string.settings_openai_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_openai_model), style = MaterialTheme.typography.titleSmall)
                    OpenAiTts.MODELS.forEach { model ->
                        val label = when (model) {
                            OpenAiTts.MODEL_TTS_1 -> stringResource(R.string.settings_openai_model_tts1)
                            OpenAiTts.MODEL_TTS_1_HD -> stringResource(R.string.settings_openai_model_tts1_hd)
                            else -> model
                        }
                        ChoiceRow(
                            label = label,
                            selected = state.openAiModel == model,
                            enabled = !isPlaying,
                            onClick = { viewModel.setOpenAiModel(model) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_openai_voice), style = MaterialTheme.typography.titleSmall)
                    OpenAiTts.VOICES.forEach { voice ->
                        ChoiceRow(
                            label = voice,
                            selected = state.openAiVoice == voice,
                            enabled = !isPlaying,
                            onClick = { viewModel.setOpenAiVoice(voice) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Engines
            if (state.engines.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.settings_voice_engine), style = MaterialTheme.typography.titleMedium)
                }
                item {
                    ChoiceRow(
                        label = stringResource(R.string.settings_voice_engine_default),
                        selected = state.selectedEngine == null,
                        enabled = !isPlaying,
                        onClick = { viewModel.selectEngine(null) }
                    )
                }
                items(state.engines) { engine ->
                    ChoiceRow(
                        label = engine.label,
                        selected = engine.packageName == state.selectedEngine,
                        enabled = !isPlaying,
                        onClick = { viewModel.selectEngine(engine.packageName) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // Voices per language
            item {
                Text(stringResource(R.string.settings_voice_voices), style = MaterialTheme.typography.titleMedium)
            }
            state.groups.forEach { group ->
                item(key = "header-${group.language}") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(group.displayName, style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(
                            onClick = { viewModel.testVoice(group.language) },
                            enabled = !isPlaying
                        ) { Text(stringResource(R.string.settings_voice_test)) }
                    }
                }
                if (group.voices.isEmpty()) {
                    item(key = "empty-${group.language}") {
                        Text(
                            stringResource(R.string.settings_voice_none_installed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    item(key = "default-${group.language}") {
                        ChoiceRow(
                            label = stringResource(R.string.settings_voice_default),
                            selected = group.selectedVoice == null,
                            enabled = !isPlaying,
                            onClick = { viewModel.selectVoice(group.language, null) }
                        )
                    }
                    items(group.voices, key = { "voice-${group.language}-${it.name}" }) { voice ->
                        ChoiceRow(
                            label = voice.name,
                            supporting = TtsVoiceLogic.describe(voice),
                            selected = group.selectedVoice == voice.name,
                            enabled = !isPlaying,
                            onClick = { viewModel.selectVoice(group.language, voice.name) }
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        try {
                            context.startActivity(Intent(TTS_SETTINGS_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_voice_system_unavailable),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) { Text(stringResource(R.string.settings_voice_system)) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    supporting: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// There is no Settings.ACTION_* constant for the system text-to-speech settings; this is the action string
// the platform Settings app handles.
private const val TTS_SETTINGS_ACTION = "com.android.settings.TTS_SETTINGS"
