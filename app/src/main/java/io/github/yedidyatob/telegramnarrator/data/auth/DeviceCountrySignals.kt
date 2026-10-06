package io.github.yedidyatob.telegramnarrator.data.auth

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import io.github.yedidyatob.telegramnarrator.domain.auth.PhoneCountryResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the device's country hints for the login default country. None of these need a runtime permission.
 */
@Singleton
class DeviceCountrySignals @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun read(): PhoneCountryResolver.Signals {
        val hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return PhoneCountryResolver.Signals(
            simCountryIso = safely { telephony?.simCountryIso },
            networkCountryIso = if (hasTelephony) safely { telephony?.networkCountryIso } else null,
            hasTelephony = hasTelephony,
            localeCountry = Locale.getDefault().country
        )
    }

    // Some OEM builds throw from TelephonyManager on devices without a radio
    private inline fun safely(block: () -> String?): String? = try {
        block()
    } catch (e: Exception) {
        null
    }
}
