package com.example.hybridml.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

data class CloudPredictRequest(
    @SerializedName("tensor_data") val tensorData: List<Float>,
    @SerializedName("shape") val shape: List<Int>
)

data class CloudPredictResponse(
    @SerializedName("probabilities") val probabilities: List<Float>,
    @SerializedName("top_class") val topClass: Int,
    @SerializedName("confidence") val confidence: Float,
    @SerializedName("server_latency_ms") val serverLatencyMs: Float,
    @SerializedName("model_version") val modelVersion: String
)

interface CloudApiService {
    @POST("predict")
    suspend fun predict(@Body request: CloudPredictRequest): CloudPredictResponse
}
