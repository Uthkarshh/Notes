package com.notesapp.data.metadata

import com.notesapp.data.images.CoverImages
import com.notesapp.data.prefs.FeaturePreferences
import com.notesapp.data.security.PrivateSecrets
import com.notesapp.domain.metadata.ItemMetadata
import com.notesapp.domain.model.CategoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class MetadataResult(val providerId: String, val title: String, val metadata: ItemMetadata, val description: String? = null, val coverUrl: String? = null)

/** HTTPS is limited to the selected public metadata providers. No requests happen before opt-in. */
@Singleton
class MetadataLookup @Inject constructor(private val prefs: FeaturePreferences, private val secrets: PrivateSecrets, private val covers: CoverImages) : MetadataSource {
    private val lock = Mutex()
    private var lastRequest = 0L
    override suspend fun search(query: String, category: CategoryType): List<MetadataResult> = withContext(Dispatchers.IO) {
        require(prefs.settings.first().lookupEnabled) { "Enable online lookup in Settings first. Manual editing always works offline." }
        require(query.isNotBlank()) { "Enter a title to search." }
        lock.withLock {
            delay((1000 - (android.os.SystemClock.elapsedRealtime() - lastRequest)).coerceAtLeast(0))
            lastRequest = android.os.SystemClock.elapsedRealtime()
            val encoded = URLEncoder.encode(query.trim().take(300), "UTF-8")
            when (category) {
                CategoryType.BOOK -> parseBooks(fetch("https://openlibrary.org/search.json?title=$encoded&limit=8&fields=key,title,author_name,first_publish_year,number_of_pages_median,isbn,cover_i"))
                CategoryType.MOVIE -> parseMovies(fetch("https://api.themoviedb.org/3/search/movie?query=$encoded&include_adult=false", movieToken()))
                else -> error("Lookup is available for movie and book collections.")
            }
        }
    }
    override suspend fun details(result: MetadataResult, category: CategoryType): MetadataResult = withContext(Dispatchers.IO) {
        require(prefs.settings.first().lookupEnabled) { "Online lookup is off." }
        if (category != CategoryType.MOVIE) result else {
            require(result.providerId.matches(Regex("\\d+")))
            val root = Json.parseToJsonElement(fetch("https://api.themoviedb.org/3/movie/${result.providerId}", movieToken())).jsonObject
            result.copy(metadata = result.metadata.copy(runtimeMinutes = root["runtime"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 },
                genre = root["genres"]?.jsonArray?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }?.joinToString(", ")))
        }
    }
    override suspend fun downloadCover(url: String): String = withContext(Dispatchers.IO) {
        require(prefs.settings.first().lookupEnabled) { "Online lookup is off." }
        val bytes = request(url, null, 15 * 1024 * 1024)
        bytes.inputStream().use(covers::save)
    }
    private fun movieToken() = secrets.get("tmdb_token")?.takeIf { it.isNotBlank() } ?: error("Add your TMDB API Read Access Token in Settings to look up movies. Book lookup needs no token.")
    private fun fetch(url: String, token: String? = null) = String(request(url, token, 2 * 1024 * 1024), Charsets.UTF_8)
    private fun request(url: String, token: String?, limit: Int): ByteArray {
        var target = URL(url)
        repeat(4) {
            val host = target.host.lowercase()
            require(target.protocol == "https" && (host in setOf("openlibrary.org", "covers.openlibrary.org", "api.themoviedb.org", "image.tmdb.org") || token == null && (host == "archive.org" || host.endsWith(".archive.org")))) { "Unsupported metadata server." }
            val connection = target.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000; connection.readTimeout = 15_000; connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "NotesApp/0.4.0 (user-initiated personal metadata lookup)")
                token?.let { require(host == "api.themoviedb.org"); connection.setRequestProperty("Authorization", "Bearer $it") }
                val code = connection.responseCode
                if (code in 300..399) { val redirect = URL(target, connection.getHeaderField("Location") ?: error("Invalid provider redirect.")); require(token == null || redirect.host == target.host); target = redirect }
                else {
                    require(code in 200..299) { "Metadata provider returned HTTP $code. You can keep editing offline." }
                    return connection.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0
                        while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= limit) { "Provider response is too large." }; out.write(buffer, 0, n) }
                        out.toByteArray()
                    }
                }
            } finally { connection.disconnect() }
        }
        error("Too many provider redirects.")
    }
    companion object {
        fun parseBooks(raw: String): List<MetadataResult> = Json.parseToJsonElement(raw).jsonObject["docs"]?.jsonArray.orEmpty().take(8).mapNotNull { element ->
            val row = element.jsonObject
            val title = row["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val id = row["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val cover = row["cover_i"]?.jsonPrimitive?.longOrNull
            MetadataResult(id, title, ItemMetadata(author = row["author_name"]?.jsonArray?.map { it.jsonPrimitive.content }?.joinToString(", "),
                pageCount = row["number_of_pages_median"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }, isbn = row["isbn"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull), coverUrl = cover?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" })
        }
        fun parseMovies(raw: String): List<MetadataResult> = Json.parseToJsonElement(raw).jsonObject["results"]?.jsonArray.orEmpty().take(8).mapNotNull { element ->
            val row = element.jsonObject
            val id = row["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val title = row["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val poster = row["poster_path"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith('/') }
            MetadataResult(id.toString(), title, ItemMetadata(releaseYear = row["release_date"]?.jsonPrimitive?.contentOrNull?.take(4)?.toIntOrNull()),
                row["overview"]?.jsonPrimitive?.contentOrNull, poster?.let { "https://image.tmdb.org/t/p/w500$it" })
        }
    }
}
