package com.example.hybridml.data

import com.example.hybridml.domain.InferenceEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InferenceModule {

    @Binds
    @Singleton
    abstract fun bindInferenceEngine(
        router: HybridInferenceRouter
    ): InferenceEngine
}