package com.example.hybridml.di

import android.content.Context
import androidx.room.Room
import com.example.hybridml.data.local.AppDatabase
import com.example.hybridml.data.local.BenchmarkDao
import com.example.hybridml.data.repository.BenchmarkRepositoryImpl
import com.example.hybridml.domain.repository.BenchmarkRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "hybridml_benchmark.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideBenchmarkDao(database: AppDatabase): BenchmarkDao {
        return database.benchmarkDao()
    }

    @Provides
    @Singleton
    fun provideBenchmarkRepository(repository: BenchmarkRepositoryImpl): BenchmarkRepository {
        return repository
    }
}
