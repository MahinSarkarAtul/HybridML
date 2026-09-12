package com.example.hybridml.data

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.hybridml.data.image.ImagePreprocessor
import com.example.hybridml.domain.ExecutionEngineSource
import com.example.hybridml.domain.InferenceEngine
import com.example.hybridml.domain.ModelInput
import com.example.hybridml.domain.PredictionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.system.measureTimeMillis

@Singleton
class OnDeviceInferenceEngine @Inject constructor(
    @param:ApplicationContext private val context: Context
) : InferenceEngine {

    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var ortSession: OrtSession? = null

    val classMap: Map<Int, String> by lazy {
        loadClassMap()
    }

    private fun loadClassMap(): Map<Int, String> {
        return try {
            val jsonStr = context.assets.open("models/classes.json").bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)
            val classesArray = json.getJSONArray("classes")
            val map = mutableMapOf<Int, String>()
            for (i in 0 until classesArray.length()) {
                val item = classesArray.getJSONObject(i)
                val id = item.getInt("classId")
                val name = item.getString("className")
                map[id] = name
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun initSession() {
        if (ortSession == null) {
            val modelBytes = context.assets.open("models/mobilenetv3_small_dynamic_mixed.onnx").readBytes()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }
            ortSession = ortEnv.createSession(modelBytes, sessionOptions)
        }
    }

    override suspend fun warmUp(): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            initSession()
        }
    }

    /**
     * Executes edge inference over a real camera/gallery [Bitmap].
     *
     * Preprocesses bitmap into [1, 3, 224, 224] FloatBuffer, executes ONNX Runtime session,
     * computes stable softmax over logits, and returns [PredictionResult] with top class details.
     */
    suspend fun runInference(bitmap: Bitmap): Result<PredictionResult> = withContext(Dispatchers.Default) {
        runCatching {
            initSession()
            val floatBuffer = ImagePreprocessor.preprocess(bitmap)
            val (rawLogits, latency) = executeInference(floatBuffer)
            buildPredictionResult(rawLogits, latency)
        }
    }

    override suspend fun runInference(input: ModelInput): Result<PredictionResult> = withContext(Dispatchers.Default) {
        runCatching {
            initSession()
            val requiredSize = 1 * ImagePreprocessor.INPUT_CHANNELS * ImagePreprocessor.INPUT_WIDTH * ImagePreprocessor.INPUT_HEIGHT
            val floatBuffer = if (input.tensorData.size == requiredSize) {
                FloatBuffer.wrap(input.tensorData)
            } else {
                val padded = FloatArray(requiredSize) { i ->
                    if (i < input.tensorData.size) input.tensorData[i] else 0.0f
                }
                FloatBuffer.wrap(padded)
            }
            val (rawLogits, latency) = executeInference(floatBuffer)
            buildPredictionResult(rawLogits, latency)
        }
    }

    private fun executeInference(floatBuffer: FloatBuffer): Pair<FloatArray, Long> {
        val session = checkNotNull(ortSession) { "Session failed to initialize" }
        val shape = longArrayOf(1, 3, 224, 224)
        val inputName = session.inputNames.firstOrNull() ?: "input"
        val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, shape)

        var rawLogits = floatArrayOf()
        val latency = measureTimeMillis {
            inputTensor.use { tensor ->
                session.run(mapOf(inputName to tensor)).use { result ->
                    val outputValue = result[0]
                    val rawOutput = outputValue.value
                    rawLogits = when (rawOutput) {
                        is Array<*> -> (rawOutput[0] as FloatArray)
                        is FloatArray -> rawOutput
                        else -> error("Unexpected output format from ONNX model: ${rawOutput?.javaClass}")
                    }
                }
            }
        }
        return Pair(rawLogits, latency)
    }

    private fun buildPredictionResult(rawLogits: FloatArray, latencyMs: Long): PredictionResult {
        val probabilities = stableSoftmax(rawLogits)
        var topClassId = 0
        var maxConfidence = if (probabilities.isNotEmpty()) probabilities[0] else 0.0f
        for (i in 1 until probabilities.size) {
            if (probabilities[i] > maxConfidence) {
                maxConfidence = probabilities[i]
                topClassId = i
            }
        }
        val topClassName = classMap[topClassId] ?: "Class $topClassId"

        return PredictionResult(
            outputScores = probabilities,
            executionLatencyMs = latencyMs,
            source = ExecutionEngineSource.LOCAL_ON_DEVICE,
            confidence = maxConfidence,
            classId = topClassId,
            className = topClassName
        )
    }

    override fun release() {
        ortSession?.close()
        ortSession = null
        ortEnv.close()
    }

    companion object {
        /**
         * Numerically stable softmax:
         * P_i = exp(z_i - max(z)) / sum_j(exp(z_j - max(z)))
         */
        fun stableSoftmax(logits: FloatArray): FloatArray {
            if (logits.isEmpty()) return floatArrayOf()
            val maxLogit = logits.maxOrNull() ?: 0.0f
            val expScores = FloatArray(logits.size) { i ->
                exp((logits[i] - maxLogit).toDouble()).toFloat()
            }
            val sumExp = expScores.sum()
            return if (sumExp > 0.0f && !sumExp.isNaN()) {
                FloatArray(logits.size) { i -> expScores[i] / sumExp }
            } else {
                FloatArray(logits.size) { 1.0f / logits.size }
            }
        }
    }
}