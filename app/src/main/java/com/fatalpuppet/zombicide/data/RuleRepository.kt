package com.fatalpuppet.zombicide.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class RuleRepository(private val context: Context) {
    fun loadSections(): List<RuleSection> {
        val text = context.assets.open("rules/core_rules.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val array = root.getJSONArray("sections")
        return (0 until array.length()).map { i ->
            val section = array.getJSONObject(i)
            val entries = section.getJSONArray("entries")
            RuleSection(
                id = section.getString("id"),
                title = section.getString("title"),
                entries = (0 until entries.length()).map { j ->
                    val entry = entries.getJSONObject(j)
                    RuleEntry(
                        title = entry.getString("title"),
                        summary = entry.getString("summary"),
                        details = entry.getJSONArray("details").toStringList(),
                        sourcePages = entry.getString("sourcePages")
                    )
                }
            )
        }
    }

    fun loadSetupSteps(): List<SetupStep> {
        val text = context.assets.open("rules/core_rules.json").bufferedReader().use { it.readText() }
        val array = JSONObject(text).getJSONArray("setupChecklist")
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            SetupStep(item.getString("id"), item.getString("title"), item.getString("note"))
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }
}
