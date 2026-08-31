package com.streamlivex.android.tv.sports

import com.streamlivex.android.tv.data.NativeLiveChannel
import java.text.Normalizer
import java.util.Locale

enum class BroadcastEvidence { EXACT, TURKEY_NETWORK_FALLBACK }

data class BroadcastOption(
    val canonicalName: String,
    val evidence: BroadcastEvidence,
    val exactBroadcast: SportsBroadcast? = null,
)

data class SportsChannelGroup(
    val canonicalName: String,
    val evidence: BroadcastEvidence,
    val sources: List<NativeLiveChannel>,
    val exactBroadcast: SportsBroadcast? = null,
)

/** Static product metadata only. It never claims that a fallback is an exact match broadcast. */
object SportsBroadcastResolver {
    fun options(event: SportsEvent): List<BroadcastOption> {
        val exact = event.broadcasts.map { broadcast ->
            BroadcastOption(
                SportsCanonicalChannels.canonicalFor(broadcast.channelName) ?: broadcast.channelName.trim(),
                BroadcastEvidence.EXACT,
                broadcast,
            )
        }.distinctBy { it.canonicalName }
        if (exact.isNotEmpty()) return exact

        return fallbackNames(event).distinct().map {
            BroadcastOption(it, BroadcastEvidence.TURKEY_NETWORK_FALLBACK)
        }
    }

    private fun fallbackNames(event: SportsEvent): List<String> {
        val league = identity(event.league)
        val sport = identity(event.sport)
        return when {
            sport.contains("formula 1") || sport.contains("motorsport") || league.contains("formula 1") || league == "f1" ->
                listOf("beIN Sports 4")
            league.contains("super lig") -> if (isBigTurkeyTeam(event.home) || isBigTurkeyTeam(event.away))
                listOf("beIN Sports 1", "beIN Sports 2") else listOf("beIN Sports 2", "beIN Sports 3", "beIN Sports 4")
            league.contains("premier league") -> listOf("beIN Sports 3", "beIN Sports 4", "beIN Sports 5")
            league.contains("ligue 1") -> listOf("beIN Sports 1", "beIN Sports 2", "beIN Sports 3", "beIN Sports 4", "beIN Sports 5")
            league.contains("la liga") || league.contains("serie a") || league.contains("bundesliga") -> listOf("S Sport", "S Sport 2")
            league.contains("primeira") || league.contains("liga portugal") || league.contains("eredivisie") ->
                listOf("Tivibu Spor 1", "Tivibu Spor 2", "Tivibu Spor 3")
            league.contains("champions league") || league.contains("europa league") || league.contains("conference league") ->
                listOf("TRT 1", "TRT Spor")
            else -> emptyList()
        }
    }

    private fun isBigTurkeyTeam(value: String): Boolean {
        val team = identity(value)
        return team in setOf("galatasaray", "gs", "fenerbahce", "fb", "besiktas", "bjk", "trabzonspor", "ts")
    }
}

/** Built once per loaded playlist and retains every real stream entry for each canonical channel. */
class SportsChannelIndex private constructor(
    private val sourcesByCanonical: Map<String, List<NativeLiveChannel>>,
) {
    fun resolve(options: List<BroadcastOption>): List<SportsChannelGroup> = options.mapNotNull { option ->
        sourcesByCanonical[option.canonicalName]?.takeIf { it.isNotEmpty() }?.let {
            SportsChannelGroup(option.canonicalName, option.evidence, it, option.exactBroadcast)
        }
    }

    fun sourceCount(canonicalName: String): Int = sourcesByCanonical[canonicalName].orEmpty().size
    val canonicalCount: Int get() = sourcesByCanonical.count { it.value.isNotEmpty() }
    val totalSourceCount: Int get() = sourcesByCanonical.values.sumOf { it.size }

    companion object {
        @Volatile private var cachedChannels: List<NativeLiveChannel>? = null
        @Volatile private var cachedIndex: SportsChannelIndex? = null

        fun forChannels(channels: List<NativeLiveChannel>): SportsChannelIndex {
            if (cachedChannels === channels) cachedIndex?.let { return it }
            return synchronized(this) {
                if (cachedChannels === channels) cachedIndex?.let { return@synchronized it }
                build(channels).also {
                    cachedChannels = channels
                    cachedIndex = it
                }
            }
        }

        private fun build(channels: List<NativeLiveChannel>): SportsChannelIndex {
            val indexed = LinkedHashMap<String, MutableList<NativeLiveChannel>>()
            channels.forEach { channel ->
                SportsCanonicalChannels.canonicalFor(channel.name)?.let { canonical ->
                    indexed.getOrPut(canonical) { mutableListOf() }.add(channel)
                }
            }
            return SportsChannelIndex(indexed.mapValues { (_, rows) -> rows.distinctBy { it.id } })
        }
    }
}

object SportsCanonicalChannels {
    val names = listOf(
        "beIN Sports 1", "beIN Sports 2", "beIN Sports 3", "beIN Sports 4", "beIN Sports 5",
        "S Sport", "S Sport 2", "Tivibu Spor 1", "Tivibu Spor 2", "Tivibu Spor 3", "TRT 1", "TRT Spor",
    )
    private val canonicalTokens by lazy(LazyThreadSafetyMode.PUBLICATION) { names.associateWith(::tokens) }

    fun canonicalFor(channelName: String): String? {
        val actual = tokens(channelName)
        if (actual.isEmpty()) return null
        return names.firstOrNull { canonical -> matches(canonicalTokens.getValue(canonical), actual) }
    }

    private fun matches(expected: List<String>, actual: List<String>): Boolean {
        val expectedNumbers = expected.filter { it.all(Char::isDigit) }
        val actualNumbers = actual.filter { it.all(Char::isDigit) }
        if (expectedNumbers != actualNumbers) return false
        val words = expected.filterNot { it.all(Char::isDigit) }
        return words.all { it in actual }
    }

    private fun tokens(value: String): List<String> = normalize(value).split(whitespacePattern)
        .filter { it.isNotBlank() && it !in noise && !qualityPattern.matches(it) }

    private fun normalize(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(markPattern, "").replace(nonAlphaNumericPattern, " ").trim()

    private val whitespacePattern = Regex("\\s+")
    private val markPattern = Regex("\\p{M}+")
    private val nonAlphaNumericPattern = Regex("[^a-z0-9]+")
    private val qualityPattern = Regex("(?:4k|uhd|fhd|hd|sd|hevc|h265|h264|1080p?|2160p?|720p?)")
    private val noise = setOf("tr", "turkiye", "turkey", "vip", "backup", "source", "server", "kanal", "channel", "tv")
}

fun sportsSourceQuality(name: String): String =
    Regex("(?i)\\b(4K|UHD|FHD|HD|SD|HEVC|H265|1080P|2160P|720P)\\b").find(name)?.value?.uppercase(Locale.ROOT)
        ?: "Standart"

private fun identity(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
