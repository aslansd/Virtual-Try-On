package com.example.network

import com.squareup.moshi.JsonClass

// --- Gemini Content Models ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String // Base64 encoded string
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: Map<String, Any>? = null, // Using dynamic map or schema
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

// --- Structured Custom Analysis Output Model ---

@JsonClass(generateAdapter = true)
data class TryOnAnalysisReport(
    val overallVerdict: String = "Stunning Match",
    val summary: String = "",
    val fitAdvice: String = "",
    val colorDraping: String = "",
    val suitabilityRating: Int = 4, // 1 to 5 stars
    val recommendedOccasion: String = "Casual Wear",
    val scores: List<FitScore> = emptyList(),
    val accessorizingItems: List<StylingSuggestion> = emptyList(),
    val keyFabricTechNotes: String = ""
)

@JsonClass(generateAdapter = true)
data class FitScore(
    val category: String, // e.g. "Silhouette Fit", "Color Harmony", "Elegance", "Versatility"
    val score: Int // 0 to 10
)

@JsonClass(generateAdapter = true)
data class StylingSuggestion(
    val item: String,
    val reason: String
)
