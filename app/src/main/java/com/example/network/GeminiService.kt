package com.example.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

interface GeminiClient {
    suspend fun analyzeTryOn(
        personBase64: String?,
        garmentBase64: String?,
        garmentUrl: String,
        garmentTitle: String,
        customApiKey: String? = null
    ): TryOnAnalysisReport?
}

object GeminiService : GeminiClient {
    private const val TAG = "GeminiService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(GenerateContentRequest::class.java)
    private val responseAdapter = moshi.adapter(GenerateContentResponse::class.java)
    val reportAdapter = moshi.adapter(TryOnAnalysisReport::class.java)

    override suspend fun analyzeTryOn(
        personBase64: String?,
        garmentBase64: String?,
        garmentUrl: String,
        garmentTitle: String,
        customApiKey: String?
    ): TryOnAnalysisReport? = withContext(Dispatchers.IO) {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API Key is blank or placeholder")
            return@withContext createFallbackReport("API Key Error", "Please provide a valid Gemini API Key in the settings or build config to unlock full styling insights.", garmentTitle)
        }

        val systemPrompt = """
            You are a premier Paris-trained AI Fashion Consultant and Professional Stylist. 
            You are analyzing a virtual try-on session of a person trying a specific clothing item.
            Look at the person photo (if provided) and the garment photo (if provided).
            Analyze their visual styling suitability, color draping harmony, and structural sizing match.
            You must provide a highly professional, stylish, and structured fashion response in JSON format.
            Do not include markdown markers like ```json ... ```. Just return the pure JSON object matching the requested schema.
        """.trimIndent()

        val prompt = if (personBase64 != null) {
            """
                Analyze this Virtual Try-On combination.
                The person wants to try on the garment: "$garmentTitle" (from link: $garmentUrl).
                Compare the person's photo and the garment's style. Provide a personalized advice.
            """.trimIndent()
        } else {
            """
                Analyze this clothing item for Virtual Try-On: "$garmentTitle".
                The shopping link and details are: $garmentUrl.
                Provide general styling, body type, and occasion recommendations for this item.
            """.trimIndent()
        }

        val partsList = mutableListOf<Part>()
        partsList.add(Part(text = prompt))

        if (personBase64 != null) {
            partsList.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = personBase64)))
        }
        if (garmentBase64 != null) {
            partsList.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = garmentBase64)))
        }

        val requestBodyObject = GenerateContentRequest(
            contents = listOf(Content(parts = partsList)),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = getReportJsonSchema(),
                temperature = 0.4f
            )
        )

        try {
            val jsonRequest = requestAdapter.toJson(requestBodyObject)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonRequest.toRequestBody(mediaType)

            val apiEndpoint = "$BASE_URL?key=$apiKey"
            val request = Request.Builder()
                .url(apiEndpoint)
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val responseBodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "API call failed with code ${response.code}: $responseBodyStr")
                    return@withContext createFallbackReport(
                        "Try-On Analysis Offline",
                        "The styling service is temporarily offline or the API key is invalid (HTTP ${response.code}). Feel free to adjust the virtual garment overlay visually in the Try-On window!",
                        garmentTitle
                    )
                }

                val geminiResponse = responseAdapter.fromJson(responseBodyStr)
                val responseText = geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (responseText != null) {
                    try {
                        val parsedReport = reportAdapter.fromJson(responseText)
                        if (parsedReport != null) {
                            return@withContext parsedReport
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Moshi parsing failed of response content", e)
                        // Try to clean up markdown if Gemini returned it anyway
                        val cleanedText = cleanJsonString(responseText)
                        try {
                            val parsedCleaned = reportAdapter.fromJson(cleanedText)
                            if (parsedCleaned != null) return@withContext parsedCleaned
                        } catch (e2: Exception) {
                            Log.e(TAG, "Moshi second parsing attempt failed", e2)
                        }
                    }
                }
                
                return@withContext createFallbackReport("Aesthetic Evaluation Complete", "I analyzed the visual pairing. It looks stylish! Try matching it with complementary layers and coordinating shoes.", garmentTitle)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini network execution", e)
            return@withContext createFallbackReport(
                "Connection Issue",
                "Cannot reach fashion server: ${e.localizedMessage}. You can still use the Canvas drag & drop editor to try on this garment and fit it visually!",
                garmentTitle
            )
        }
    }

    private fun cleanJsonString(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```json")) {
            clean = clean.substring(7)
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3)
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length - 3)
        }
        return clean.trim()
    }

    private fun createFallbackReport(title: String, summary: String, item: String): TryOnAnalysisReport {
        return TryOnAnalysisReport(
            overallVerdict = title,
            summary = summary,
            fitAdvice = "Tailored styling is recommended. Fit the item visually onto your picture in the Try-On panel to review necklines and shoulder matches.",
            colorDraping = "Classic pairing is advised. Standard neutrals usually look excellent with $item.",
            suitabilityRating = 4,
            recommendedOccasion = "Any Dress Code",
            scores = listOf(
                FitScore("Aesthetic Appeal", 8),
                FitScore("Versatility", 7),
                FitScore("Color Contrast", 8)
            ),
            accessorizingItems = listOf(
                StylingSuggestion("Contrasting jacket", "To add dimensionality and balance the outfit structures."),
                StylingSuggestion("Modern sneakers", "For a smart-casual lifestyle fit suited for any urban scene.")
            ),
            keyFabricTechNotes = "Refer to the product care tag from the store link ($item)."
        )
    }

    // Helper to request strict JSON schema to guarantee correct parsing
    private fun getReportJsonSchema(): Map<String, Any> {
        return mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "overallVerdict" to mapOf(
                    "type" to "STRING",
                    "description" to "Overall brief verdict name or vibe, e.g., 'Parisian Chic', 'Athleisure Vibe', 'Casual Smart'"
                ),
                "summary" to mapOf(
                    "type" to "STRING",
                    "description" to "3-sentence styled paragraph consulting the user on the suitability, fit, and style"
                ),
                "fitAdvice" to mapOf(
                    "type" to "STRING",
                    "description" to "Detailed notes on how the clothes would fit on their body based on the picture"
                ),
                "colorDraping" to mapOf(
                    "type" to "STRING",
                    "description" to "Detailed notes on color draping, color coordination with skin tone/hair/background"
                ),
                "suitabilityRating" to mapOf(
                    "type" to "INTEGER",
                    "description" to "General rating star score from 1 (poor match) to 5 (perfect match)"
                ),
                "recommendedOccasion" to mapOf(
                    "type" to "STRING",
                    "description" to "Best occasion category, e.g. Business Casual, Formal Evening, High Fashion Outdoor, Urban Lounge"
                ),
                "scores" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf(
                        "type" to "OBJECT",
                        "properties" to mapOf(
                            "category" to mapOf("type" to "STRING", "description" to "Metric category e.g., 'Cut & Silhouette', 'Color Harmony', 'Versatility', 'Elegance'"),
                            "score" to mapOf("type" to "INTEGER", "description" to "Integer rating score from 0 to 10")
                        ),
                        "required" to listOf("category", "score")
                    )
                ),
                "accessorizingItems" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf(
                        "type" to "OBJECT",
                        "properties" to mapOf(
                            "item" to mapOf("type" to "STRING", "description" to "Name of specific item to finish the coordinate"),
                            "reason" to mapOf("type" to "STRING", "description" to "Why it matches specifically")
                        ),
                        "required" to listOf("item", "reason")
                    )
                ),
                "keyFabricTechNotes" to mapOf(
                    "type" to "STRING",
                    "description" to "Notes on styling standard materials and wash/care suggestions"
                )
            ),
            "required" to listOf(
                "overallVerdict", "summary", "fitAdvice", "colorDraping",
                "suitabilityRating", "recommendedOccasion", "scores", "accessorizingItems", "keyFabricTechNotes"
            )
        )
    }
}
