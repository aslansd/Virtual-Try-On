package com.example.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

data class ScrapedProduct(
    val title: String,
    val imageUrl: String?,
    val url: String,
    val description: String = ""
)

object UrlScraper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun scrapeUrl(url: String): ScrapedProduct = withContext(Dispatchers.IO) {
        val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }

        try {
            val request = Request.Builder()
                .url(cleanUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ScrapedProduct(
                        title = getDomainName(cleanUrl),
                        imageUrl = null,
                        url = cleanUrl,
                        description = "Failed to fetch page metadata (Error code: ${response.code})"
                    )
                }

                val html = response.body?.string() ?: ""

                // Meta og:image match
                val imageUrl = extractMetaTag(html, "og:image")
                
                // Meta og:title match
                var title = extractMetaTag(html, "og:title")
                if (title.isBlank()) {
                    title = extractTitleTag(html)
                }
                if (title.isBlank()) {
                    title = getDomainName(cleanUrl)
                }

                // Meta og:description match
                val description = extractMetaTag(html, "og:description")

                ScrapedProduct(
                    title = title.replace("&amp;", "&").trim(),
                    imageUrl = imageUrl.trim(),
                    url = cleanUrl,
                    description = description.replace("&amp;", "&").trim()
                )
            }
        } catch (e: Exception) {
            Log.e("UrlScraper", "Error scraping URL: $url", e)
            ScrapedProduct(
                title = getDomainName(cleanUrl),
                imageUrl = null,
                url = cleanUrl,
                description = "Couln't scrape page info: ${e.localizedMessage}"
            )
        }
    }

    private fun extractMetaTag(html: String, property: String): String {
        // Match both: property="og:image" content="..." and content="..." property="og:image"
        // Pattern 1: property="..." content="..."
        val pattern1 = Pattern.compile(
            "meta\\s+[^>]*property=[\"']$property[\"']\\s+[^>]*content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        )
        var matcher = pattern1.matcher(html)
        if (matcher.find()) {
            return matcher.group(1) ?: ""
        }

        // Pattern 2: name="..." content="..."
        val pattern2 = Pattern.compile(
            "meta\\s+[^>]*name=[\"']$property[\"']\\s+[^>]*content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        )
        matcher = pattern2.matcher(html)
        if (matcher.find()) {
            return matcher.group(1) ?: ""
        }

        // Pattern 3: content="..." property="..."
        val pattern3 = Pattern.compile(
            "meta\\s+[^>]*content=[\"']([^\"']+)[\"']\\s+[^>]*property=[\"']$property[\"']",
            Pattern.CASE_INSENSITIVE
        )
        matcher = pattern3.matcher(html)
        if (matcher.find()) {
            return matcher.group(1) ?: ""
        }

        return ""
    }

    private fun extractTitleTag(html: String): String {
        val pattern = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1) ?: ""
        }
        return ""
    }

    fun getDomainName(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val domain = uri.host ?: ""
            if (domain.startsWith("www.")) domain.substring(4) else domain
        } catch (e: Exception) {
            "Shopping Store Item"
        }
    }
}
