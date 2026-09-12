package com.example.hybridml.data.network

import android.os.SystemClock
import com.example.hybridml.data.remote.CloudApiService
import com.example.hybridml.domain.ExecutionEngineSource
import com.example.hybridml.domain.PredictionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudInferenceClient @Inject constructor(
    val apiService: CloudApiService
) {

    /**
     * Uploads in-memory JPEG byte array to POST /predict/image via multipart/form-data.
     */
    suspend fun predictImage(jpegBytes: ByteArray): Result<PredictionResult> = withContext(Dispatchers.IO) {
        val startTime = SystemClock.elapsedRealtime()
        try {
            val requestBody = jpegBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "image.jpg", requestBody)
            val response = apiService.predictImage(part)
            val roundTripLatency = SystemClock.elapsedRealtime() - startTime

            Result.success(
                PredictionResult(
                    outputScores = floatArrayOf(),
                    executionLatencyMs = roundTripLatency,
                    source = ExecutionEngineSource.REMOTE_GPU_CLOUD,
                    confidence = response.confidence,
                    classId = response.classId,
                    className = response.className,
                    cloudModelId = response.modelId,
                    cloudModelVersion = response.modelVersion
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        /**
         * Factory helper to create custom client instances (e.g., for test environments or unreachable ports).
         */
        fun create(
            baseUrl: String = "http://127.0.0.1:8000/",
            timeoutMs: Long = 500
        ): CloudInferenceClient {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(CloudApiService::class.java)
            return CloudInferenceClient(service)
        }
    }
}
