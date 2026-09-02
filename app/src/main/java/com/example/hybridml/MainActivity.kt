package com.example.hybridml

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hybridml.presentation.InferenceViewModel
import com.example.hybridml.presentation.UiState
import com.example.hybridml.ui.theme.HybridMLTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HybridMLTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    InferenceScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun InferenceScreen(
    modifier: Modifier = Modifier,
    viewModel: InferenceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Hybrid ML Engine",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        when (val state = uiState) {
            is UiState.Idle -> {
                Text(text = "Engine ready for input tensor.")
            }
            is UiState.Loading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Executing inference...")
            }
            is UiState.Success -> {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Source: ${state.result.source.name}")
                        Text(text = "Latency: ${state.result.executionLatencyMs} ms")
                        Text(text = "Confidence: ${(state.result.confidence * 100).toInt()}%")
                        Text(text = "Top Class Score: ${state.result.outputScores[1]}")
                    }
                }
            }
            is UiState.Error -> {
                Text(
                    text = "Failure: ${state.message}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = { viewModel.runSimulatedInference() }) {
            Text(text = "Run Inference Benchmark")
        }
    }
}