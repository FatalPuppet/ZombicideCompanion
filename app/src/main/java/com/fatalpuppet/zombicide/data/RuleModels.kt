package com.fatalpuppet.zombicide.data

data class RuleEntry(
    val title: String,
    val summary: String,
    val details: List<String>,
    val sourcePages: String
)

data class RuleSection(
    val id: String,
    val title: String,
    val entries: List<RuleEntry>
)

data class SetupStep(
    val id: String,
    val title: String,
    val note: String
)
