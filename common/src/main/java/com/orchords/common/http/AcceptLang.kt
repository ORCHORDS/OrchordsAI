package com.orchords.common.http

import java.util.Locale

/**
 *
 */
class AcceptLanguageBuilder private constructor(
    private val localesInPreference: List<Locale>,
    private val options: Options
) {

    data class Options(
        val maxLanguages: Int = 6,
        val qStep: Double = 0.1,
        val minQ: Double = 0.1,
        val includeGenericLanguage: Boolean = true,
        val deduplicate: Boolean = true
    )

    companion object {
        fun fromJvmSystem(options: Options = Options()): AcceptLanguageBuilder {
            val primary = Locale.getDefault()
            return AcceptLanguageBuilder(listOf(primary), options)
        }

        /**
         */
        fun fromAndroid(context: android.content.Context, options: Options = Options()): AcceptLanguageBuilder {
            val locales = systemLocalesAndroid(context)
            return AcceptLanguageBuilder(locales, options)
        }

        fun withLocales(locales: List<Locale>, options: Options = Options()): AcceptLanguageBuilder {
            return AcceptLanguageBuilder(locales, options)
        }

        private fun systemLocalesAndroid(context: android.content.Context): List<Locale> {
            val cfg = context.resources.configuration
            return if (android.os.Build.VERSION.SDK_INT >= 24) {
                val list = cfg.locales
                (0 until list.size()).map { idx -> list[idx] }
            } else {
                listOf(cfg.locale)
            }
        }
    }

    fun build(): String {
        val tags = mutableListOf<String>()
        for (loc in localesInPreference) {
            val full = toLanguageTagCompat(loc)
            if (full.isNotBlank()) tags += full

            if (options.includeGenericLanguage) {
                val generic = genericLanguageOf(full)
                if (generic != null) tags += generic
            }
        }

        val distinct = if (options.deduplicate) tags.distinct() else tags

        val limited = distinct.take(options.maxLanguages.coerceAtLeast(1))

        val parts = mutableListOf<String>()
        var q = 1.0
        for ((i, tag) in limited.withIndex()) {
            if (i == 0) {
                parts += tag
            } else {
                q = (1.0 - i * options.qStep).coerceAtLeast(options.minQ)
                parts += "$tag;q=${formatQ(q)}"
            }
        }

        return parts.joinToString(separator = ", ")
    }


    private fun toLanguageTagCompat(locale: Locale): String {
        val tag = locale.toLanguageTag()
        if (tag.isNotBlank()) return tag

        val language = locale.language ?: return ""
        val country = locale.country
        val variant = locale.variant

        return buildString {
            append(language)
            if (!country.isNullOrBlank()) append("-").append(country)
            if (!variant.isNullOrBlank()) append("-").append(variant)
        }
    }

    private fun genericLanguageOf(tag: String): String? {
        val idx = tag.indexOf('-')
        if (idx <= 0) return null
        val head = tag.substring(0, idx)
        return if (head.isNotBlank()) head else null
    }

    private fun formatQ(value: Double): String {
        val s = String.format(java.util.Locale.ROOT, "%.3f", value)
        return s.trimEnd('0').trimEnd('.')
    }
}

fun main() {
    val builder = AcceptLanguageBuilder.fromJvmSystem()
    println(builder.build())
}
