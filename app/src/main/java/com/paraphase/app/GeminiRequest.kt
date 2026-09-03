package com.paraphase.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the generateContent body. Kept separate from the network code so the
 * per-generation differences can be unit tested.
 *
 * Gemini 3.x changed two things that matter here:
 *  - temperature / topP / topK / candidateCount are deprecated and must not be sent
 *  - thinking is a string level ("low"/"medium"/"high"), not a token budget
 * 2.x models still take the old shape, and a user can pin one, so both are built.
 */
object GeminiRequest {

    /** True for gemini-3 and anything later. Unknown names are treated as new. */
    fun isModern(model: String): Boolean {
        val major = Regex("gemini-(\\d+)").find(model.lowercase())
            ?.groupValues?.get(1)?.toIntOrNull()
            ?: return true
        return major >= 3
    }

    fun generationConfig(model: String): JSONObject {
        val config = JSONObject().put("maxOutputTokens", 2048)
        if (isModern(model)) {
            // Rewriting is latency sensitive and needs no deliberation.
            config.put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
        } else {
            config.put("temperature", 0.7)
            if (model.contains("2.5")) {
                config.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            }
        }
        return config
    }

    fun body(model: String, systemPrompt: String, text: String): JSONObject = JSONObject()
        .put(
            "systemInstruction",
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
        )
        .put(
            "contents",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", text)))
            )
        )
        .put("generationConfig", generationConfig(model))
}
