package io.github.yedidyatob.telegramnarrator.ui.screens.onboarding

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.onboarding.OnboardingStep
import io.github.yedidyatob.telegramnarrator.domain.openai.OpenAiTts
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.ui.components.PrivacyPolicyLink
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.OnboardingViewModel

/** State of the voice step, so the page can be rendered without a ViewModel (previews / screenshots). */
data class VoiceStepState(
    val provider: SpeechProvider = SpeechProvider.DEFAULT,
    /** null while the system engine starts. */
    val hebrewVoice: Boolean? = null,
    val hasOpenAiKey: Boolean = false
)

data class VoiceStepActions(
    val onSelect: (SpeechProvider) -> Unit = {},
    val onOpenTtsSettings: () -> Unit = {},
    val onSaveOpenAiKey: (String) -> Unit = {}
)

/**
 * First-run onboarding before the login: what the app does, the unofficial-app / privacy notice, choosing a
 * voice engine, and (Android 13+) the notification permission. Skippable and shown once.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val stepIndex by viewModel.stepIndex.collectAsStateWithLifecycle()
    val provider by viewModel.provider.collectAsStateWithLifecycle()
    val hebrewVoice by viewModel.hebrewVoice.collectAsStateWithLifecycle()
    val hasKey by viewModel.hasOpenAiKey.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val finish = {
        viewModel.finish()
        onFinished()
    }
    // Whatever the answer, the onboarding is done (playback works without the permission)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    // Back from the system TTS settings: look for a Hebrew voice again
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.recheckHebrewVoice()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = stepIndex > 0) { viewModel.back() }

    OnboardingContent(
        steps = viewModel.steps,
        stepIndex = stepIndex,
        voice = VoiceStepState(provider, hebrewVoice, hasKey),
        voiceActions = VoiceStepActions(
            onSelect = viewModel::selectProvider,
            onOpenTtsSettings = {
                try {
                    context.startActivity(Intent(TTS_SETTINGS_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.settings_system_tts_unavailable, Toast.LENGTH_SHORT).show()
                }
            },
            onSaveOpenAiKey = viewModel::saveOpenAiKey
        ),
        onNext = { if (viewModel.isLastStep) finish() else viewModel.next() },
        onBack = { viewModel.back() },
        onSkip = finish,
        onAllowNotifications = {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                finish()
            }
        }
    )
}

@Composable
fun OnboardingContent(
    steps: List<OnboardingStep>,
    stepIndex: Int,
    voice: VoiceStepState,
    voiceActions: VoiceStepActions,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onAllowNotifications: () -> Unit
) {
    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]
    val isLast = stepIndex >= steps.lastIndex
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 8.dp)
                    .height(48.dp)
            ) {
                StepDots(count = steps.size, current = stepIndex, modifier = Modifier.weight(1f))
                if (!isLast) {
                    TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip)) }
                }
            }
            AnimatedContent(
                targetState = stepIndex,
                transitionSpec = {
                    // Forward slides in from the reading end (left in RTL)
                    val forward = targetState > initialState
                    val sign = (if (forward) 1 else -1) * (if (rtl) -1 else 1)
                    (slideInHorizontally { it * sign / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it * sign / 3 } + fadeOut())
                },
                label = "onboarding-step",
                modifier = Modifier.weight(1f)
            ) { index ->
                val page = steps[index.coerceIn(0, steps.lastIndex)]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    when (page) {
                        OnboardingStep.WELCOME -> WelcomePage()
                        OnboardingStep.PRIVACY -> PrivacyPage(edgeSelected = voice.provider == SpeechProvider.EDGE)
                        OnboardingStep.VOICE -> VoicePage(voice, voiceActions)
                        OnboardingStep.NOTIFICATIONS -> NotificationsPage()
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (stepIndex > 0) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.onboarding_back)) }
                }
                Spacer(Modifier.weight(1f))
                if (step == OnboardingStep.NOTIFICATIONS) {
                    TextButton(onClick = onNext) { Text(stringResource(R.string.permission_notifications_skip)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onAllowNotifications) { Text(stringResource(R.string.permission_notifications_allow)) }
                } else {
                    Button(onClick = onNext) {
                        Text(stringResource(if (isLast) R.string.onboarding_get_started else R.string.onboarding_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_step, current + 1, count)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = description }
    ) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot")
            val color by animateColorAsState(
                if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dot-color"
            )
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun PageHeader(icon: ImageVector, title: String, body: String?) {
    Spacer(Modifier.height(16.dp))
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(112.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
        }
    }
    Spacer(Modifier.height(28.dp))
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() }
    )
    if (body != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun Point(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WelcomePage() {
    PageHeader(
        Icons.Rounded.Headphones,
        stringResource(R.string.onboarding_welcome_title),
        stringResource(R.string.onboarding_welcome_body)
    )
    Point(Icons.Rounded.PlayCircle, stringResource(R.string.onboarding_welcome_point_listen))
    Point(Icons.Rounded.SkipNext, stringResource(R.string.onboarding_welcome_point_control))
    Point(Icons.Rounded.Translate, stringResource(R.string.onboarding_welcome_point_languages))
}

@Composable
private fun PrivacyPage(edgeSelected: Boolean) {
    PageHeader(Icons.Rounded.Lock, stringResource(R.string.onboarding_privacy_title), null)
    // Telegram API ToS 2.2 / 2.3: the app is unofficial and built on the Telegram API
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
    ) {
        Text(
            stringResource(R.string.login_unofficial_notice),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
    Spacer(Modifier.height(12.dp))
    Point(Icons.Rounded.PhoneAndroid, stringResource(R.string.onboarding_privacy_point_device))
    // The default engine of a new install sends text to Microsoft: say so before the voice step
    if (edgeSelected) Point(Icons.Rounded.Cloud, stringResource(R.string.onboarding_privacy_point_voice))
    Point(Icons.Rounded.DoneAll, stringResource(R.string.onboarding_privacy_point_read))
    Point(Icons.Rounded.Campaign, stringResource(R.string.onboarding_privacy_point_ads))
    Spacer(Modifier.height(8.dp))
    PrivacyPolicyLink()
}

@Composable
private fun VoicePage(state: VoiceStepState, actions: VoiceStepActions) {
    PageHeader(
        Icons.Rounded.RecordVoiceOver,
        stringResource(R.string.onboarding_voice_title),
        stringResource(R.string.onboarding_voice_body)
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().selectableGroup()
    ) {
        // Edge first: the default engine (preselected on a new install)
        VoiceOptionCard(
            selected = state.provider == SpeechProvider.EDGE,
            title = stringResource(R.string.onboarding_voice_edge_title),
            description = stringResource(R.string.onboarding_voice_edge_desc),
            onSelect = { actions.onSelect(SpeechProvider.EDGE) }
        )
        VoiceOptionCard(
            selected = state.provider == SpeechProvider.SYSTEM,
            title = stringResource(R.string.onboarding_voice_system_title),
            description = stringResource(R.string.onboarding_voice_system_desc),
            onSelect = { actions.onSelect(SpeechProvider.SYSTEM) }
        ) {
            HebrewVoiceStatus(state.hebrewVoice, actions.onOpenTtsSettings)
        }
        VoiceOptionCard(
            selected = state.provider == SpeechProvider.OPENAI,
            title = stringResource(R.string.onboarding_voice_openai_title),
            description = stringResource(R.string.onboarding_voice_openai_desc),
            onSelect = { actions.onSelect(SpeechProvider.OPENAI) }
        ) {
            OpenAiKeyEntry(state.hasOpenAiKey, actions.onSaveOpenAiKey)
        }
    }
}

@Composable
private fun VoiceOptionCard(
    selected: Boolean,
    title: String,
    description: String,
    onSelect: () -> Unit,
    extra: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                    .padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            ) {
                RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
                Column(Modifier.weight(1f).padding(top = 10.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (extra != null) {
                AnimatedVisibility(visible = selected, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.padding(start = 52.dp, end = 16.dp, bottom = 16.dp)) { extra() }
                }
            }
        }
    }
}

@Composable
private fun HebrewVoiceStatus(hebrewVoice: Boolean?, onOpenTtsSettings: () -> Unit) {
    if (hebrewVoice == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (hebrewVoice) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = if (hebrewVoice) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(if (hebrewVoice) R.string.onboarding_voice_hebrew_ok else R.string.onboarding_voice_hebrew_missing),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
    if (!hebrewVoice) {
        TextButton(onClick = onOpenTtsSettings) { Text(stringResource(R.string.settings_system_open_tts)) }
    }
}

@Composable
private fun OpenAiKeyEntry(hasKey: Boolean, onSave: (String) -> Unit) {
    if (hasKey) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.onboarding_voice_openai_saved), style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    // Not saveable on purpose: the key must not end up in the saved instance state
    var key by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text(stringResource(R.string.settings_openai_api_key)) },
        placeholder = { Text(stringResource(R.string.settings_openai_api_key_hint)) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSave(key) }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(if (visible) R.string.settings_openai_hide_key else R.string.settings_openai_show_key)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        val getKeyA11y = stringResource(R.string.settings_openai_get_key_a11y)
        TextButton(
            onClick = {
                try {
                    uriHandler.openUri(OpenAiTts.API_KEYS_URL)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.settings_openai_get_key_no_browser, Toast.LENGTH_SHORT).show()
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(context, R.string.settings_openai_get_key_no_browser, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.semantics { contentDescription = getKeyA11y }
        ) { Text(stringResource(R.string.settings_openai_get_key)) }
        Spacer(Modifier.weight(1f))
        Button(onClick = { onSave(key) }, enabled = key.isNotBlank()) { Text(stringResource(R.string.settings_openai_save_key)) }
    }
    Text(
        stringResource(R.string.onboarding_voice_openai_later),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun NotificationsPage() {
    PageHeader(
        Icons.Rounded.NotificationsActive,
        stringResource(R.string.onboarding_notifications_title),
        stringResource(R.string.onboarding_notifications_body)
    )
}

// There is no Settings.ACTION_* constant for the system text-to-speech settings (same as Voice settings)
private const val TTS_SETTINGS_ACTION = "com.android.settings.TTS_SETTINGS"
