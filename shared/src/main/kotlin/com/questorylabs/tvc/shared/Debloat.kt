package com.questorylabs.tvc.shared

import kotlinx.serialization.Serializable

enum class DebloatTier { SAFE, OPTIONAL, RISKY }

@Serializable
data class DebloatEntry(val pkg: String, val label: String, val tier: DebloatTier, val why: String)

@Serializable
data class DebloatList(val device: String, val packages: List<DebloatEntry>)

object DebloatCatalog {
    fun parse(jsonText: String): DebloatList = Protocol.json.decodeFromString(DebloatList.serializer(), jsonText)

    fun parseDisabledPackages(pmOutput: String): Set<String> =
        pmOutput.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .toSet()
}
