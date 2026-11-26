package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.preferences.AiModelPreference
import com.example.data.preferences.FishingMode
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private const val TAG = "FirebaseSonarAI"
private const val PER_ATTEMPT_TIMEOUT_MS = 25_000L
private const val RETRY_DELAY_MS = 1_200L

private val GEMINI_FAILOVER_POOL = listOf(
    "gemini-3.5-flash-lite",
    "gemini-3.1-flash-lite",
    "gemini-3.8-flash",
    "gemini-3-flash-preview"
)

data class MarinePrediction(
    val feedingScore: Int,
    val feedingRating: String,
    val weatherSummary: String,
    val windAndWave: String,
    val moonPhase: String,
    val tideCondition: String,
    val primeHours: List<String>,
    val baitRecommendations: List<String>,
    val tacticalAdvice: String,
    val analyzedCoordinates: String,
    val modelUsed: String = "",
    val isSampleMode: Boolean = false,
    val notice: String = ""
)

data class SonarVisionAnalysis(
    val fishDetectionSummary: String,
    val estimatedFishDensity: String,
    val seabedStructure: String,
    val waterClarity: String,
    val targetDepthRange: String,
    val recommendedLure: String,
    val confidencePercent: Int,
    val modelUsed: String = "",
    val isSampleMode: Boolean = false,
    val notice: String = ""
)

interface SonarAiRepository {
    suspend fun analyzeMarineConditions(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?,
        liveTideContext: String? = null,
        fishingMode: FishingMode = FishingMode.SHORE,
        preferredModel: AiModelPreference = AiModelPreference.AUTO
    ): Result<MarinePrediction>

    suspend fun analyzeSonarImage(
        bitmap: Bitmap,
        latitude: Double?,
        longitude: Double?,
        fishingMode: FishingMode = FishingMode.SHORE,
        preferredModel: AiModelPreference = AiModelPreference.AUTO
    ): Result<SonarVisionAnalysis>
}

/**
 * Live Gemini implementation powered by Firebase AI Logic with App Check.
 * Uses gemini-3.5-flash-lite as high-availability primary with automatic failover to gemini-3.8-flash,
 * strict structured JSON output via Schema.obj, and per-attempt coroutine timeouts.
 */
class FirebaseSonarAiRepository : SonarAiRepository {

    private val marinePredictionSchema = Schema.obj(
        properties = mapOf(
            "feedingScore" to Schema.integer(
                description = "Skor peluang ikan makan antara 0 sampai 100"
            ),
            "feedingRating" to Schema.string(
                description = "Label singkat status seperti SANGAT GACOR, POTENSIAL TINGGI, CUKUP AKTIF, atau ARUS TENANG"
            ),
            "weatherSummary" to Schema.string(
                description = "Ringkasan kondisi cuaca laut, awan, dan perkiraan suhu air dalam Bahasa Indonesia"
            ),
            "windAndWave" to Schema.string(
                description = "Ringkasan kecepatan angin dalam knot, arah angin, dan estimasi tinggi gelombang dalam meter"
            ),
            "moonPhase" to Schema.string(
                description = "Fase bulan saat ini dan pengaruhnya terhadap arus pasang surut (spring/neap tide)"
            ),
            "tideCondition" to Schema.string(
                description = "Status pasang surut laut saat ini dan saran posisi arus"
            ),
            "primeHours" to Schema.array(
                items = Schema.string(),
                description = "3 rentang jam gacor terbaik hari ini dalam WIB beserta alasannya"
            ),
            "baitRecommendations" to Schema.array(
                items = Schema.string(),
                description = "Wajib berisi tepat 3 rekomendasi konkret: (1) umpan alami/hidup & ukuran kail, (2) metal jig/lure beserta bobot gram sesuai arus, (3) teknik mancing & target ikan khas Bintan"
            ),
            "tacticalAdvice" to Schema.string(
                description = "Saran taktis singkat 2 kalimat untuk pemancing di koordinat ini dalam Bahasa Indonesia"
            )
        )
    )

    private val sonarVisionSchema = Schema.obj(
        properties = mapOf(
            "fishDetectionSummary" to Schema.string(
                description = "Analisis lengkap apakah terlihat lengkungan ikan (fish arch), gerombolan baitball, atau tanda kehidupan ikan di layar sonar / permukaan air"
            ),
            "estimatedFishDensity" to Schema.string(
                description = "Kepadatan ikan singkat: TINGGI, SEDANG, atau RINGAN beserta keterangan singkat"
            ),
            "seabedStructure" to Schema.string(
                description = "Kontur dasar laut, karang, pasir, atau struktur drop-off yang terlihat atau diindikasikan gambar"
            ),
            "waterClarity" to Schema.string(
                description = "Kejernihan dan warna air laut atau tingkat noise/clutter pada tampilan fishfinder"
            ),
            "targetDepthRange" to Schema.string(
                description = "Estimasi zona kedalaman target terbaik dalam meter untuk menurunkan umpan/jig"
            ),
            "recommendedLure" to Schema.string(
                description = "Rekomendasi umpan, berat jig/timah, dan warna umpan terbaik berdasarkan analisis gambar"
            ),
            "confidencePercent" to Schema.integer(
                description = "Tingkat keyakinan analisis gambar antara 0 sampai 100"
            )
        )
    )

    // Lazy per-model caches so Firebase.ai is only evaluated on first inference
    private val marineModelCache = ConcurrentHashMap<String, GenerativeModel>()
    private val visionModelCache = ConcurrentHashMap<String, GenerativeModel>()

    @Volatile
    private var lastHealthyModelId: String? = null

    private fun getMarineModel(modelId: String): GenerativeModel =
        marineModelCache.getOrPut(modelId) {
            Firebase.ai.generativeModel(
                modelName = modelId,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = marinePredictionSchema
                }
            )
        }

    private fun getVisionModel(modelId: String): GenerativeModel =
        visionModelCache.getOrPut(modelId) {
            Firebase.ai.generativeModel(
                modelName = modelId,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = sonarVisionSchema
                }
            )
        }

    private fun buildCandidateOrder(preferredModel: AiModelPreference): List<String> {
        val ordered = LinkedHashSet<String>()
        preferredModel.modelId?.let { ordered.add(it) }
        if (preferredModel == AiModelPreference.AUTO) {
            lastHealthyModelId?.let { ordered.add(it) }
        }
        ordered.addAll(GEMINI_FAILOVER_POOL)
        return ordered.toList()
    }

    private fun formatModelDisplayName(modelId: String): String = when (modelId) {
        "gemini-3.5-flash-lite" -> "Gemini 3.5 Flash-Lite"
        "gemini-3.1-flash-lite" -> "Gemini 3.1 Flash-Lite"
        "gemini-3.8-flash" -> "Gemini 3.8 Flash"
        "gemini-3-flash-preview" -> "Gemini 3 Flash Preview"
        else -> modelId
    }

    private fun collectErrorMessage(error: Throwable): String {
        val parts = mutableListOf<String>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 4) {
            current.localizedMessage?.takeIf(String::isNotBlank)?.let(parts::add)
            current.message?.takeIf(String::isNotBlank)?.let(parts::add)
            current = current.cause
            depth++
        }
        return parts.distinct().joinToString(" | ")
    }

    private fun isNonRetryableLocalOrAuthError(error: Throwable): Boolean {
        if (error is UnknownHostException) return true
        val fullMsg = collectErrorMessage(error)
        return fullMsg.contains("Default FirebaseApp is not initialized", ignoreCase = true) ||
            fullMsg.contains("App Check", ignoreCase = true) ||
            fullMsg.contains("PERMISSION_DENIED", ignoreCase = true) ||
            fullMsg.contains("403", ignoreCase = true) ||
            fullMsg.contains("Unable to resolve host", ignoreCase = true) ||
            fullMsg.contains("Network is unreachable", ignoreCase = true)
    }

    private fun isTransientServerOrBusyError(error: Throwable): Boolean {
        if (error is TimeoutCancellationException) return true
        val fullMsg = collectErrorMessage(error)
        return fullMsg.contains("high demand", ignoreCase = true) ||
            fullMsg.contains("overloaded", ignoreCase = true) ||
            fullMsg.contains("503", ignoreCase = true) ||
            fullMsg.contains("429", ignoreCase = true) ||
            fullMsg.contains("UNAVAILABLE", ignoreCase = true) ||
            fullMsg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
            fullMsg.contains("try again", ignoreCase = true) ||
            fullMsg.contains("DEADLINE_EXCEEDED", ignoreCase = true) ||
            fullMsg.contains("temporarily", ignoreCase = true)
    }

    private suspend fun executeWithResilience(
        preferredModel: AiModelPreference,
        modelProvider: (String) -> GenerativeModel,
        call: suspend (GenerativeModel) -> GenerateContentResponse
    ): Pair<String, GenerateContentResponse> {
        val candidates = buildCandidateOrder(preferredModel)
        var lastError: Throwable? = null
        var sawTransientError = false

        for (modelId in candidates) {
            try {
                val response = withTimeout(PER_ATTEMPT_TIMEOUT_MS) {
                    call(modelProvider(modelId))
                }
                lastHealthyModelId = modelId
                return modelId to response
            } catch (e: TimeoutCancellationException) {
                sawTransientError = true
                lastError = e
                Log.w(TAG, "Model $modelId timed out; trying next Gemini fallback...", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isNonRetryableLocalOrAuthError(e)) throw e
                if (isTransientServerOrBusyError(e)) sawTransientError = true
                lastError = e
                Log.w(TAG, "Model $modelId failed (${e.localizedMessage}); trying next Gemini fallback...", e)
            }
        }

        // Final short backoff retry on the top candidate if transient congestion hit all models
        if (sawTransientError) {
            delay(RETRY_DELAY_MS)
            val retryModelId = candidates.first()
            val response = withTimeout(PER_ATTEMPT_TIMEOUT_MS) {
                call(modelProvider(retryModelId))
            }
            lastHealthyModelId = retryModelId
            return retryModelId to response
        }

        throw lastError ?: IllegalStateException("Semua model Gemini gagal merespons.")
    }

    private fun toFriendlyFailure(error: Throwable): IllegalStateException {
        val rawDetail = error.localizedMessage?.takeIf(String::isNotBlank)
            ?: error.message?.takeIf(String::isNotBlank)
            ?: error.javaClass.simpleName
        val fullMsg = collectErrorMessage(error)

        val friendlyText = when {
            fullMsg.contains("Default FirebaseApp is not initialized", ignoreCase = true) ->
                "Konfigurasi Firebase (app/google-services.json) belum ikut terbawa saat build APK. " +
                    "Pastikan Secret GOOGLE_SERVICES_JSON aktif di GitHub lalu build ulang APK."
            fullMsg.contains("App Check", ignoreCase = true) ||
                fullMsg.contains("403", ignoreCase = true) ||
                fullMsg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                "Verifikasi keamanan Firebase App Check gagal. Pastikan internet stabil & Google Play Services aktif, lalu tutup-buka aplikasi dan tekan Coba Pindai Lagi. ($rawDetail)"
            error is UnknownHostException ||
                fullMsg.contains("Unable to resolve host", ignoreCase = true) ||
                fullMsg.contains("Network is unreachable", ignoreCase = true) ->
                "Tidak ada koneksi internet. Periksa sinyal seluler atau WiFi Anda lalu tekan Coba Pindai Lagi."
            error is TimeoutCancellationException ->
                "Koneksi ke server Gemini AI melewati batas waktu. Periksa sinyal internet lalu tekan Coba Pindai Lagi."
            isTransientServerOrBusyError(error) ->
                "Server Gemini AI sedang padat sementara. Tunggu beberapa detik lalu tekan Coba Pindai Lagi. ($rawDetail)"
            else -> rawDetail
        }
        return IllegalStateException(friendlyText, error)
    }

    override suspend fun analyzeMarineConditions(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?,
        liveTideContext: String?,
        fishingMode: FishingMode,
        preferredModel: AiModelPreference
    ): Result<MarinePrediction> {
        return try {
            val timestamp = SimpleDateFormat("EEEE, dd MMM yyyy HH:mm z", Locale("id", "ID")).format(Date())
            val coordLabel = String.format(Locale.US, "%.5f°, %.5f°", latitude, longitude)
            val accuracyInfo = accuracyMeters?.let { "±${it.roundToInt()} meter" } ?: "tidak tersedia"
            val tideExtra = liveTideContext?.let { "\nData Pasang Surut & Gelombang Live Open-Meteo: $it" } ?: ""

            val prompt = if (fishingMode == FishingMode.SHORE) {
                """
                    Anda adalah pakar memancing pinggiran (shore fishing / pasiran / dermaga / batu karang tepi / muara bakau) spesialis Pulau Bintan dan Kepulauan Riau.
                    Pengguna memancing DARI PINGGIR (tanpa perahu), jadi JANGAN menyarankan trolling laut dalam atau jig berat 80-120 gr.
                    Analisis kondisi mancing pinggiran untuk koordinat GPS berikut dalam Bahasa Indonesia yang praktis:
                    - Mode Mancing: MANCING PINGGIR (Pantai / Dermaga / Karang Tepi)
                    - Koordinat GPS: $coordLabel (akurasi GPS: $accuracyInfo)
                    - Waktu Lokal: $timestamp$tideExtra

                    Berikan analisis lengkap meliputi:
                    1. Cuaca pesisir, angin & ombak pinggir, fase bulan, status pasang surut (kapan ikan masuk ke pinggir/karang dangkal), dan skor peluang ikan makan (0-100).
                    2. Wajib isi primeHours dengan tepat 3 rentang jam gacor WIB hari ini khusus mancing pinggiran (utamakan fase pasang naik / peralihan air) beserta alasan singkatnya.
                    3. Wajib isi baitRecommendations dengan tepat 3 referensi umpan & rangkaian khusus mancing pinggir: (a) umpan alami/hidup mudah didapat (udang hidup/kupas, cacing laut/pumpun, potongan cumi/sotong, atau lumut/roti untuk baronang) + ukuran kail & timah ringan (J1-J4 / pelampung), (b) teknik ultralight/light casting (micro jig 5-15 gr / minnow 5-9 cm / soft lure) untuk pinggir karang/dermaga, (c) teknik membaca palung, batas karang tepi, atau arus balik untuk target ikan pinggir Bintan (baronang, kakap putih, kerapu karang pinggir, sembilang, gerot-gerot, kitang, kerong-kerong, senangin).
                    4. Saran taktis singkat 2 kalimat khusus pemancing pinggiran (jarak lempar & posisi berdiri aman saat pasang).
                """.trimIndent()
            } else {
                """
                    Anda adalah kapten kapal & pakar memancing laut profesional (spesialis perairan Pulau Bintan, Selat Singapura, dan Kepulauan Riau).
                    Analisis kondisi memancing dari PERAHU / LAUT DALAM untuk koordinat GPS berikut secara akurat dan praktis dalam Bahasa Indonesia:
                    - Mode Mancing: PERAHU / LAUT DALAM
                    - Koordinat GPS: $coordLabel (akurasi GPS: $accuracyInfo)
                    - Waktu Lokal: $timestamp$tideExtra

                    Berikan analisis lengkap meliputi:
                    1. Cuaca laut, angin & gelombang, fase bulan (solunar), pasang surut, dan skor peluang ikan makan (0-100).
                    2. Wajib isi primeHours dengan tepat 3 rentang jam gacor WIB hari ini beserta alasan singkatnya.
                    3. Wajib isi baitRecommendations dengan tepat 3 referensi umpan & teknik konkret (umpan alami/hidup seperti sotong/udang/selar + ukuran kail, metal jig/lure + bobot gram sesuai arus, serta teknik dasaran/slow jigging/casting karang) untuk target ikan khas Bintan (kerapu, kakap merah, tenggiri, GT, jenaha, kurisi).
                    4. Saran taktis singkat 2 kalimat.
                """.trimIndent()
            }

            val (usedModelId, response) = executeWithResilience(
                preferredModel = preferredModel,
                modelProvider = ::getMarineModel
            ) { model ->
                model.generateContent(prompt)
            }

            val rawJson = response.text?.trim()
                ?: return Result.failure(IllegalStateException("Gemini mengembalikan respons kosong."))

            val json = JSONObject(rawJson)
            val score = json.optInt("feedingScore", 70).coerceIn(0, 100)
            val primeHoursJson = json.optJSONArray("primeHours")
            val primeHours = if (primeHoursJson != null) {
                (0 until primeHoursJson.length()).mapNotNull { primeHoursJson.optString(it).takeIf(String::isNotBlank) }
            } else {
                emptyList()
            }

            val baitsJson = json.optJSONArray("baitRecommendations")
            val baits = if (baitsJson != null) {
                (0 until baitsJson.length()).mapNotNull { baitsJson.optString(it).takeIf(String::isNotBlank) }
            } else {
                emptyList()
            }

            Result.success(
                MarinePrediction(
                    feedingScore = score,
                    feedingRating = json.optString("feedingRating", "POTENSIAL").uppercase(Locale.US),
                    weatherSummary = json.optString("weatherSummary", "Data cuaca dianalisis"),
                    windAndWave = json.optString("windAndWave", "Angin & gelombang dianalisis"),
                    moonPhase = json.optString("moonPhase", "Fase bulan dianalisis"),
                    tideCondition = json.optString("tideCondition", "Pasang surut dianalisis"),
                    primeHours = primeHours,
                    baitRecommendations = baits,
                    tacticalAdvice = json.optString("tacticalAdvice", ""),
                    analyzedCoordinates = coordLabel,
                    modelUsed = formatModelDisplayName(usedModelId),
                    isSampleMode = false,
                    notice = ""
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Marine condition analysis timed out", e)
            Result.failure(toFriendlyFailure(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI marine analysis failed", e)
            Result.failure(toFriendlyFailure(e))
        }
    }

    override suspend fun analyzeSonarImage(
        bitmap: Bitmap,
        latitude: Double?,
        longitude: Double?,
        fishingMode: FishingMode,
        preferredModel: AiModelPreference
    ): Result<SonarVisionAnalysis> {
        return try {
            val safeBitmap = downscaleForVision(bitmap, maxSide = 1024)
            val coordLine = if (latitude != null && longitude != null) {
                String.format(Locale.US, "Koordinat GPS saat foto diambil: %.5f°, %.5f°.", latitude, longitude)
            } else {
                "Koordinat GPS tidak tersedia."
            }

            val modeInstruction = if (fishingMode == FishingMode.SHORE) {
                "Pengguna sedang MANCING PINGGIR (dari pantai/dermaga/karang tepi). Jika foto berupa permukaan air/pantai/dermaga, fokuskan analisis pada zona palung (warna air lebih gelap), batas karang dangkal, garis buih/arus balik (rip current), jarak lempar ideal dari pinggir, dan rekomendasi umpan/rangkaian pinggiran (timah ringan, pelampung, udang/cumi/pumpun, atau UL lure 5-15 gr)."
            } else {
                "Pengguna memancing dari PERAHU / LAUT DALAM. Fokuskan pada pembacaan layar fishfinder/sonar atau kondisi perairan lepas pantai serta rekomendasi jig/dasaran dalam."
            }

            val prompt = """
                Anda adalah ahli pembacaan layar fishfinder/sonar dan pakar membaca spot perairan tropis Pulau Bintan/Kepulauan Riau.
                Analisis foto yang diberikan oleh pemancing ini (bisa berupa foto langsung permukaan air pantai/dermaga/karang, atau foto layar fishfinder portabel).
                $coordLine
                $modeInstruction

                Jelaskan secara jujur dan akurat dalam Bahasa Indonesia berdasarkan apa yang benar-benar terlihat di foto:
                1. Deteksi gerombolan ikan / fish arch / baitball atau tanda aktivitas ikan & zona makan potensial.
                2. Kepadatan / potensi ikan (TINGGI / SEDANG / RINGAN).
                3. Kontur dasar laut, batas karang/pasir, palung, atau kondisi arus permukaan yang terlihat di gambar.
                4. Kejernihan & warna air laut (atau kualitas pembacaan layar sonar).
                5. Estimasi zona kedalaman / jarak lempar target terbaik untuk menurunkan umpan.
                6. Rekomendasi umpan, bobot timah/jig ringan, dan teknik mancing paling cocok.
            """.trimIndent()

            val imageRequest = content {
                image(safeBitmap)
                text(prompt)
            }
            val (usedModelId, response) = executeWithResilience(
                preferredModel = preferredModel,
                modelProvider = ::getVisionModel
            ) { model ->
                model.generateContent(imageRequest)
            }

            val rawJson = response.text?.trim()
                ?: return Result.failure(IllegalStateException("Gemini Vision mengembalikan respons kosong."))

            val json = JSONObject(rawJson)
            Result.success(
                SonarVisionAnalysis(
                    fishDetectionSummary = json.optString("fishDetectionSummary", "Analisis citra selesai."),
                    estimatedFishDensity = json.optString("estimatedFishDensity", "SEDANG"),
                    seabedStructure = json.optString("seabedStructure", "Struktur dasar dianalisis."),
                    waterClarity = json.optString("waterClarity", "Kejernihan air dianalisis."),
                    targetDepthRange = json.optString("targetDepthRange", "10 – 20 meter"),
                    recommendedLure = json.optString("recommendedLure", "Sesuaikan jig/umpan dengan arus."),
                    confidencePercent = json.optInt("confidencePercent", 80).coerceIn(0, 100),
                    modelUsed = formatModelDisplayName(usedModelId),
                    isSampleMode = false,
                    notice = ""
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Sonar vision analysis timed out", e)
            Result.failure(toFriendlyFailure(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI sonar vision analysis failed", e)
            Result.failure(toFriendlyFailure(e))
        }
    }

    private fun downscaleForVision(source: Bitmap, maxSide: Int): Bitmap {
        val maxDim = maxOf(source.width, source.height)
        if (maxDim <= maxSide) return source
        val scale = maxSide.toFloat() / maxDim.toFloat()
        val targetW = (source.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }
}
