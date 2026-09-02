package com.example.hybridml.data

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.hybridml.domain.ExecutionEngineSource
import com.example.hybridml.domain.InferenceEngine
import com.example.hybridml.domain.ModelInput
import com.example.hybridml.domain.PredictionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

@Singleton
class OnDeviceInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : InferenceEngine {

    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var ortSession: OrtSession? = null

    private fun initSession() {
        if (ortSession == null) {
            val modelBytes = context.assets.open("classifier.onnx").readBytes()
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

    override suspend fun runInference(input: ModelInput): Result<PredictionResult> =
        withContext(Dispatchers.Default) {
            runCatching {
                initSession()
                val session = checkNotNull(ortSession) { "Session failed to initialize" }

                val inputTensor = OnnxTensor.createTensor(
                    ortEnv,
                    FloatBuffer.wrap(input.tensorData),
                    input.shape
                )

                var rawScores: FloatArray = floatArrayOf()
                val latency = measureTimeMillis {
                    val inputName = session.inputNames.iterator().next()
                    session.run(mapOf(inputName to inputTensor)).use { result ->
                        val rawOutput = result[0].value
                        rawScores = when (rawOutput) {
                            is Array<*> -> (rawOutput[0] as FloatArray)
                            is FloatArray -> rawOutput
                            else -> floatArrayOf()
                        }
                    }
                }

                val maxScore = rawScores.maxOrNull() ?: 0.0f

                PredictionResult(
                    outputScores = rawScores,
                    executionLatencyMs = latency,
                    source = ExecutionEngineSource.LOCAL_ON_DEVICE,
                    confidence = maxScore
                )
            }
        }

    override fun release() {
        ortSession?.close()
        ortSession = null
        ortEnv.close()
    }
}