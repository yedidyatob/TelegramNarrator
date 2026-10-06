package io.github.yedidyatob.telegramnarrator.ui.screens.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.auth.PhoneCountries
import io.github.yedidyatob.telegramnarrator.domain.auth.PhoneCountry
import io.github.yedidyatob.telegramnarrator.domain.auth.PhoneFieldValidation
import io.github.yedidyatob.telegramnarrator.domain.auth.PhoneNumberNormalizer

/**
 * Login phone step (#56): a country picker (flag + dial code, searchable) in front of the number field.
 * Local numbers with or without the trunk prefix and pasted `+…` numbers are normalized to E.164 before
 * [onEnter] is called.
 */
@Composable
fun PhoneNumberInput(
    isLoading: Boolean,
    error: String?,
    normalizer: PhoneNumberNormalizer,
    countries: () -> List<PhoneCountry>,
    defaultRegion: String?,
    onEnter: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    var selectedRegion by rememberSaveable { mutableStateOf(defaultRegion) }
    var submitAttempted by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    val result = remember(text, selectedRegion) { normalizer.normalize(text, selectedRegion) }
    // A typed / pasted +international number selects its own country
    val shownRegion = remember(text, selectedRegion) {
        PhoneFieldValidation.effectiveRegion(text, selectedRegion, normalizer)
    }
    val inlineReason = PhoneFieldValidation.visibleReason(result, submitAttempted)
    val submit = {
        submitAttempted = true
        if (!isLoading && result is PhoneNumberNormalizer.Result.Valid) onEnter(result.e164)
    }

    LoginStepFrame(
        title = stringResource(R.string.app_name),
        subtitle = stringResource(R.string.login_title_phone),
        buttonText = stringResource(R.string.login_btn_send_code),
        canSubmit = text.isNotBlank(),
        isLoading = isLoading,
        error = error,
        onSubmit = submit,
        // Telegram API ToS 2.2 / 2.3 notice + the in-app privacy policy link Google Play requires
        footer = { UnofficialAppNotice() }
    ) {
        // Dial code + number read as one left-to-right phone number, also in RTL locales
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                CountryButton(
                    region = shownRegion,
                    normalizer = normalizer,
                    enabled = !isLoading,
                    onClick = { pickerOpen = true },
                    modifier = Modifier.padding(top = 8.dp) // align with the outlined field (label space)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = PhoneNumberNormalizer.filterTyped(it) },
                    label = { Text(stringResource(R.string.login_label_phone)) },
                    placeholder = {
                        val example = shownRegion?.let { normalizer.exampleNationalNumber(it) }
                        if (example != null) Text(example, style = LtrTextStyle)
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.merge(LtrTextStyle),
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    isError = inlineReason != null || error != null,
                    supportingText = inlineReason?.let { reason -> { Text(stringResource(reason.messageRes())) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() })
                )
            }
        }
    }

    if (pickerOpen) {
        CountryPickerDialog(
            countries = countries(),
            selectedRegion = shownRegion,
            onSelect = { country ->
                selectedRegion = country.regionCode
                // A +number of another country would override the choice: keep only its national part
                if (normalizer.regionForInternational(text).let { it != null && it != country.regionCode }) {
                    text = normalizer.nationalPart(text)
                }
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false }
        )
    }
}

private val LtrTextStyle = TextStyle(textDirection = TextDirection.Ltr)

private fun PhoneNumberNormalizer.Reason.messageRes(): Int = when (this) {
    PhoneNumberNormalizer.Reason.TOO_SHORT -> R.string.login_phone_error_too_short
    PhoneNumberNormalizer.Reason.TOO_LONG -> R.string.login_phone_error_too_long
    PhoneNumberNormalizer.Reason.WRONG_LENGTH -> R.string.login_phone_error_wrong_length
    PhoneNumberNormalizer.Reason.NO_COUNTRY -> R.string.login_phone_error_no_country
    PhoneNumberNormalizer.Reason.INVALID,
    PhoneNumberNormalizer.Reason.EMPTY -> R.string.login_phone_error_invalid
}

/** Flag + dial code ("🇮🇱 +972 ▾"), or "Country ▾" when nothing is selected. 56dp high like the text field. */
@Composable
private fun CountryButton(
    region: String?,
    normalizer: PhoneNumberNormalizer,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dial = region?.let { normalizer.dialCodeFor(it) }
    val description = if (region != null && dial != null) {
        stringResource(R.string.login_country_button_a11y, PhoneCountries.displayName(region), "+$dial")
    } else {
        stringResource(R.string.login_country_choose_a11y)
    }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 4.dp),
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics { contentDescription = description }
    ) {
        // The button's description says it all; the visible parts are hidden from TalkBack
        val hidden = Modifier.clearAndSetSemantics {}
        if (region != null && dial != null) {
            Text(PhoneCountries.flagEmoji(region), modifier = hidden)
            Spacer(Modifier.width(6.dp))
            Text("+$dial", style = LtrTextStyle, modifier = hidden)
        } else {
            Text(stringResource(R.string.login_country_choose), modifier = hidden)
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    }
}

/** Searchable country list (name in the UI language or English, ISO code or dial code). Rows are 56dp. */
@Composable
private fun CountryPickerDialog(
    countries: List<PhoneCountry>,
    selectedRegion: String?,
    onSelect: (PhoneCountry) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(countries, query) { PhoneCountries.filter(countries, query) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    stringResource(R.string.login_country_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .semantics { heading() }
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.login_country_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
                HorizontalDivider()
                if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.login_country_none_found),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(filtered, key = { it.regionCode }) { country ->
                        CountryRow(country, selected = country.regionCode == selectedRegion, onClick = { onSelect(country) })
                    }
                }
                HorizontalDivider()
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.login_country_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryRow(country: PhoneCountry, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 24.dp)
    ) {
        // TalkBack reads "<name> +<code>" (the merged texts); the flag emoji is decorative
        Text(country.flag, modifier = Modifier.width(36.dp).clearAndSetSemantics {})
        Text(country.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            country.dialCodeText,
            style = MaterialTheme.typography.bodyMedium.merge(LtrTextStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}
