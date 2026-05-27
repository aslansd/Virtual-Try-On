package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TryOnDatabase
import com.example.data.TryOnRepository
import com.example.data.TryOnSession
import com.example.network.GeminiService
import com.example.network.ScrapedProduct
import com.example.network.TryOnAnalysisReport
import com.example.network.UrlScraper
import com.example.utils.ImageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Defines the prebuilt Model Presets
data class ModelPreset(
    val id: String,
    val name: String,
    val imageUrl: String,
    val description: String
)

// Defines the prebuilt Clothes Presets
data class ClothingPreset(
    val title: String,
    val imageUrl: String,
    val storeUrl: String,
    val price: String
)

class TryOnViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "TryOnViewModel"

    private val repository: TryOnRepository

    init {
        val database = TryOnDatabase.getDatabase(application)
        repository = TryOnRepository(database.tryOnDao())
    }

    // --- Static Presets ---
    val modelPresets = listOf(
        ModelPreset(
            id = "preset_amara",
            name = "Amara (Casual Trim Profile)",
            imageUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=600",
            description = "Ideal for casual shirts, summer dresses, and light denim wear."
        ),
        ModelPreset(
            id = "preset_jaden",
            name = "Jaden (Athletic Build Profile)",
            imageUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?q=80&w=600",
            description = "A versatile athletic frame for sweaters, jackets, and overcoats."
        ),
        ModelPreset(
            id = "preset_alex",
            name = "Alex (Classic Broad Profile)",
            imageUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?q=80&w=600",
            description = "Elegantly suited for blazers, tees, structural coats, and jackets."
        )
    )

    val clothingPresets = listOf(
        ClothingPreset(
            title = "Unisex Cozy Wool Knit Sweater",
            imageUrl = "https://images.unsplash.com/photo-1620799140408-edc6dcb6d633?q=80&w=400",
            storeUrl = "https://unsplash.com/photos/red-wool-knit-sweater",
            price = "$89.00"
        ),
        ClothingPreset(
            title = "Structured Double-Breasted Tailored Blazer",
            imageUrl = "https://images.unsplash.com/photo-1591047139829-d91aecb6caea?q=80&w=400",
            storeUrl = "https://unsplash.com/photos/floral-blazer",
            price = "$145.00"
        ),
        ClothingPreset(
            title = "Classic Heavyweight Levi's Denim Jacket",
            imageUrl = "https://images.unsplash.com/photo-1576995853123-5a10305d93c0?q=80&w=400",
            storeUrl = "https://unsplash.com/photos/denim-jacket",
            price = "$110.00"
        )
    )

    // --- State Management Flow ---

    // History of Saved Sessions
    val savedSessions: StateFlow<List<TryOnSession>> = repository.allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Active Try-On Creator Input State
    val personType = MutableStateFlow("PRESET") // "PRESET" or "GALLERY"
    val selectedPresetModel = MutableStateFlow(modelPresets[0])
    val galleryPersonUri = MutableStateFlow<Uri?>(null)

    val garmentUrl = MutableStateFlow("")
    val garmentTitle = MutableStateFlow("")
    val garmentImageUrl = MutableStateFlow<String?>("") // URL string or Gallery URI string
    val priceInput = MutableStateFlow("")

    // Canvas Placement States
    val canvasScale = MutableStateFlow(1f)
    val canvasOffsetX = MutableStateFlow(0f)
    val canvasOffsetY = MutableStateFlow(0f)
    val canvasRotation = MutableStateFlow(0f)
    val canvasAlpha = MutableStateFlow(0.85f)

    // AI Status state fields
    private val _isScraping = MutableStateFlow(false)
    val isScraping = _isScraping.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing = _isAnalyzing.asStateFlow()

    private val _activeAnalysisResult = MutableStateFlow<TryOnAnalysisReport?>(null)
    val activeAnalysisResult = _activeAnalysisResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    // API Key customization override
    val customApiKey = MutableStateFlow("")

    // Selected session from history (if viewing existing)
    private val _selectedHistorySession = MutableStateFlow<TryOnSession?>(null)
    val selectedHistorySession = _selectedHistorySession.asStateFlow()

    // --- Action Methods ---

    fun onUrlInputChanged(url: String) {
        garmentUrl.value = url
        if (url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://") || url.contains("."))) {
            triggerUrlScraping(url)
        }
    }

    private fun triggerUrlScraping(url: String) {
        viewModelScope.launch {
            _isScraping.value = true
            _errorMessage.value = null
            try {
                val result = UrlScraper.scrapeUrl(url)
                garmentTitle.value = result.title
                if (!result.imageUrl.isNullOrBlank()) {
                    garmentImageUrl.value = result.imageUrl
                }
                Log.d(TAG, "Scraped URL metadata successfully: $result")
            } catch (e: Exception) {
                Log.e(TAG, "Scrape failed", e)
            } finally {
                _isScraping.value = false
            }
        }
    }

    fun selectPresetClothing(preset: ClothingPreset) {
        garmentUrl.value = preset.storeUrl
        garmentTitle.value = preset.title
        garmentImageUrl.value = preset.imageUrl
        priceInput.value = preset.price
        // Reset canvas parameters to defaults for new outfit
        resetCanvas()
    }

    fun selectPresetModel(preset: ModelPreset) {
        selectedPresetModel.value = preset
        personType.value = "PRESET"
        _selectedHistorySession.value = null // exiting historic record mode if updating inputs
    }

    fun setGalleryPersonImage(uri: Uri) {
        galleryPersonUri.value = uri
        personType.value = "GALLERY"
        _selectedHistorySession.value = null // exiting historic record mode if updating inputs
    }

    fun selectGarmentFromGallery(uri: Uri) {
        garmentImageUrl.value = uri.toString()
        _selectedHistorySession.value = null
    }

    fun resetCanvas() {
        canvasScale.value = 1f
        canvasOffsetX.value = 0f
        canvasOffsetY.value = 0f
        canvasRotation.value = 0f
        canvasAlpha.value = 0.85f
    }

    fun loadHistoricalSession(session: TryOnSession) {
        _selectedHistorySession.value = session
        
        personType.value = session.personType
        if (session.personType == "PRESET") {
            val matchedModel = modelPresets.find { it.imageUrl == session.personImageUri } ?: modelPresets[0]
            selectedPresetModel.value = matchedModel
        } else {
            galleryPersonUri.value = session.personImageUri?.let { Uri.parse(it) }
        }

        garmentUrl.value = session.garmentUrl
        garmentTitle.value = session.garmentTitle
        garmentImageUrl.value = session.garmentImageUrl
        priceInput.value = session.price

        canvasScale.value = session.scale
        canvasOffsetX.value = session.offsetX
        canvasOffsetY.value = session.offsetY
        canvasRotation.value = session.rotation
        canvasAlpha.value = session.alpha

        if (!session.aiResponseJson.isNullOrBlank()) {
            try {
                val report = GeminiService.reportAdapter.fromJson(session.aiResponseJson)
                _activeAnalysisResult.value = report
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse stored JSON from db session", e)
                _activeAnalysisResult.value = null
            }
        } else {
            _activeAnalysisResult.value = null
        }
    }

    fun runVirtualTryOnAnalysis() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorMessage.value = null
            _activeAnalysisResult.value = null

            val title = garmentTitle.value.ifBlank { "Unlabelled Garment" }
            val url = garmentUrl.value.ifBlank { "https://online-dummy-shop.com" }

            // 1. Prepare Person Image Base64
            var personBase64: String? = null
            if (personType.value == "GALLERY") {
                val uri = galleryPersonUri.value
                if (uri != null) {
                    personBase64 = ImageUtils.uriToScaledBase64(app, uri)
                } else {
                    _errorMessage.value = "Please upload/select your picture first!"
                    _isAnalyzing.value = false
                    return@launch
                }
            } else {
                // Preset models -> We also want to pass their visual style.
                // Since preset image is an Unsplash URL, we could let Gemini consult about this preset.
                // We'll pass a message about the model description or try fetching it.
                // To keep it clean and robust, we can pass null for base64 or construct base64 if cached.
                // Because we pass preset descriptions/keys in text, Gemini can analyze it just using the items.
                // Let's pass the garment photo context!
            }

            // 2. Prepare Garment Image Base64
            var garmentBase64: String? = null
            val garmentImg = garmentImageUrl.value
            if (!garmentImg.isNullOrBlank()) {
                if (garmentImg.startsWith("content://") || garmentImg.startsWith("file://")) {
                    val uri = Uri.parse(garmentImg)
                    garmentBase64 = ImageUtils.uriToScaledBase64(app, uri)
                } else {
                    // It represents a scraped Unsplash or web URL item.
                    // To keep everything server-side and rapid, we let Gemini retrieve/analyze details.
                    // We can also let the user load details.
                }
            }

            Log.d(TAG, "Starting Gemini call. PersonImage: ${personBase64 != null}, GarmentImage: ${garmentBase64 != null}")

            try {
                val result = GeminiService.analyzeTryOn(
                    personBase64 = personBase64,
                    garmentBase64 = garmentBase64,
                    garmentUrl = url,
                    garmentTitle = title,
                    customApiKey = customApiKey.value.takeIf { it.isNotBlank() }
                )

                _activeAnalysisResult.value = result

                if (result != null) {
                    saveActiveSessionToHistory(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini analysis error", e)
                _errorMessage.value = "Styling Analysis failed: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private suspend fun saveActiveSessionToHistory(report: TryOnAnalysisReport) {
        try {
            val jsonReport = GeminiService.reportAdapter.toJson(report)
            val personImg = if (personType.value == "GALLERY") {
                galleryPersonUri.value?.toString()
            } else {
                selectedPresetModel.value.imageUrl
            }

            val session = TryOnSession(
                name = garmentTitle.value.ifBlank { "Virtual Try-On Outfit" },
                personType = personType.value,
                personImageUri = personImg,
                garmentUrl = garmentUrl.value,
                garmentImageUrl = garmentImageUrl.value,
                garmentTitle = garmentTitle.value,
                price = priceInput.value,
                scale = canvasScale.value,
                offsetX = canvasOffsetX.value,
                offsetY = canvasOffsetY.value,
                rotation = canvasRotation.value,
                alpha = canvasAlpha.value,
                aiResponseJson = jsonReport
            )

            val newId = repository.insertSession(session)
            Log.d(TAG, "Saved try-on session into database. ID: $newId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing history session into database", e)
        }
    }

    fun deleteSession(session: TryOnSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
            if (_selectedHistorySession.value?.id == session.id) {
                _selectedHistorySession.value = null
                _activeAnalysisResult.value = null
            }
        }
    }

    fun clearAllResults() {
        _activeAnalysisResult.value = null
        _selectedHistorySession.value = null
        resetCanvas()
    }
}
