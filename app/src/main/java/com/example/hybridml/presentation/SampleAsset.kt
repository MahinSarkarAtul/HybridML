package com.example.hybridml.presentation

enum class SampleAsset(
    val displayName: String,
    val assetPath: String,
    val description: String
) {
    CHALLENGING(
        displayName = "Challenging",
        assetPath = "samples/edge_wrong_cloud_correct.jpeg",
        description = "Edge low confidence / Cloud correct"
    ),
    HIGH_CONFIDENCE(
        displayName = "High Confidence",
        assetPath = "samples/edge_high_conf_correct.jpeg",
        description = "Edge high confidence (Samoyed)"
    ),
    LOW_CONFIDENCE(
        displayName = "Low Confidence",
        assetPath = "samples/edge_low_conf_correct.jpeg",
        description = "Edge low confidence"
    )
}
