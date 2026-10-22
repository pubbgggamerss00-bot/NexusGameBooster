package com.nexus.gamebooster.core

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.Resources.Theme
import com.nexus.gamebooster.data.Language
import java.util.Locale

/**
 * Phase 7 locale engine — makes the in-app language preference ACTUALLY change the
 * strings the app shows (the previous behavior only flipped the layout direction).
 *
 * How it works, without AppCompat and without any `android:localeConfig`/permission:
 * [withAppLocale] builds a configuration overlay on top of the current configuration
 * (never mutating the base one) and returns a Context whose resource lookups resolve
 * against that configuration. [com.nexus.gamebooster.navigation.NgbApp] provides that
 * context as `LocalContext`, so every `stringResource(...)` call in the tree resolves
 * with the chosen language. [Language.SYSTEM] keeps the receiver untouched, which means
 * the app then follows the device locale exactly as before.
 */

/**
 * Returns a [Context] whose string/layout resources resolve for [language].
 *
 * - [Language.SYSTEM]: the receiver itself, unchanged (device locale rules apply).
 * - [Language.ENGLISH] / [Language.PERSIAN]: a localized context built via
 *   [Context.createConfigurationContext] over a COPY of the current configuration
 *   (with the target locale and its layout direction applied — the base
 *   configuration is never mutated in place).
 *
 * The localized context is wrapped in a thin [ContextWrapper] whose base remains the
 * ORIGINAL receiver. This matters for code that walks the context chain to find the
 * hosting Activity (e.g. MonitorScreen's keep-screen-on window flag): a raw
 * `createConfigurationContext` result is not a `ContextWrapper` and would make that
 * walk fail silently, quietly breaking an existing feature. Delegating
 * resources/assets/theme to the localized context keeps string resolution switched
 * while the Activity stays reachable.
 */
fun Context.withAppLocale(language: Language): Context {
    if (language == Language.SYSTEM) return this
    val locale = Locale.forLanguageTag(language.code)
    // Copy first, then modify the copy — the base configuration must never change.
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    val localized = createConfigurationContext(config)
    return LocalizedContextWrapper(base = this, localized = localized)
}

/**
 * Pure, JVM-testable RTL decision for the app shell (no Android types).
 * [systemLocaleIsRtl] is the device's own direction, read by the caller.
 */
object AppLocale {
    fun isRtl(language: Language, systemLocaleIsRtl: Boolean): Boolean = when (language) {
        Language.SYSTEM -> systemLocaleIsRtl
        Language.ENGLISH -> false
        Language.PERSIAN -> true
    }
}

/**
 * Context that keeps the original [base] as its chain (so the Activity stays findable)
 * but resolves resources, assets and theme through the localized context.
 * `Context.getString/getText` route through [getResources], which is what Compose's
 * `stringResource` reads via `LocalContext.current.resources`.
 */
private class LocalizedContextWrapper(
    base: Context,
    private val localized: Context,
) : ContextWrapper(base) {
    override fun getResources(): Resources = localized.resources
    override fun getAssets(): AssetManager = localized.assets
    override fun getTheme(): Theme = localized.theme
    override fun setTheme(resId: Int) {
        localized.setTheme(resId)
    }
}
