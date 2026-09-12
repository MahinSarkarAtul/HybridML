package com.example.hybridml.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

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

data class CloudImagePredictionResponse(
    @SerializedName("classId") val classId: Int,
    @SerializedName("className") val className: String,
    @SerializedName("confidence") val confidence: Float,
    @SerializedName("serverLatencyMs") val serverLatencyMs: Float,
    @SerializedName("modelId") val modelId: String = "mobilenet_v3_large",
    @SerializedName("modelVersion") val modelVersion: String = "1.0.0"
)

interface CloudApiService {
    @POST("predict")
    suspend fun predict(@Body request: CloudPredictRequest): CloudPredictResponse

    @Multipart
    @POST("predict/image")
    suspend fun predictImage(
        @Part file: MultipartBody.Part
    ): CloudImagePredictionResponse
}
