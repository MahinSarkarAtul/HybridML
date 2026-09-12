package com.example.hybridml

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hybridml.data.local.AppDatabase
import com.example.hybridml.data.local.BenchmarkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDbName = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(testDbName)
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(testDbName)
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To2_preservesV1DataWithNullProvenance_andAllowsV2Insert() = runBlocking {
        // Step 1: Create v1 database and insert a historical v1 row
        val dbV1 = helper.createDatabase(testDbName, 1)
        dbV1.execSQL(
            """
            INSERT INTO benchmarks (
                id, timestamp, modelVersion, executionSource, totalLatencyMs,
                localLatencyMs, cloudLatencyMs, localConfidence, finalConfidence,
                routingThreshold, cloudAttempted, escalationReason, fallbackReason
            ) VALUES (
                1, 1000, 'v1.0-tiny', 'LOCAL_ON_DEVICE', 15,
                15, NULL, 0.85, 0.85,
                0.75, 0, NULL, NULL
            )
            """.trimIndent()
        )
        dbV1.close()

        // Step 2: Run MIGRATION_1_2 and validate schema
        val migratedDb = helper.runMigrationsAndValidate(testDbName, 2, true, AppDatabase.MIGRATION_1_2)

        // Step 3: Query migrated database with raw SQLite cursor
        val cursor = migratedDb.query("SELECT * FROM benchmarks WHERE id = 1")
        assertTrue("Historical row with id=1 must exist after migration", cursor.moveToFirst())

        // Assert existing v1 fields are intact
        assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
        assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")))
        assertEquals("v1.0-tiny", cursor.getString(cursor.getColumnIndexOrThrow("modelVersion")))
        assertEquals("LOCAL_ON_DEVICE", cursor.getString(cursor.getColumnIndexOrThrow("executionSource")))
        assertEquals(15L, cursor.getLong(cursor.getColumnIndexOrThrow("totalLatencyMs")))
        assertEquals(15L, cursor.getLong(cursor.getColumnIndexOrThrow("localLatencyMs")))
        assertTrue("cloudLatencyMs should be null", cursor.isNull(cursor.getColumnIndexOrThrow("cloudLatencyMs")))
        assertEquals(0.85f, cursor.getFloat(cursor.getColumnIndexOrThrow("localConfidence")), 0.001f)
        assertEquals(0.85f, cursor.getFloat(cursor.getColumnIndexOrThrow("finalConfidence")), 0.001f)
        assertEquals(0.75f, cursor.getFloat(cursor.getColumnIndexOrThrow("routingThreshold")), 0.001f)
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("cloudAttempted")))
        assertTrue("escalationReason should be null", cursor.isNull(cursor.getColumnIndexOrThrow("escalationReason")))
        assertTrue("fallbackReason should be null", cursor.isNull(cursor.getColumnIndexOrThrow("fallbackReason")))

        // Assert all 9 new V2 columns are present and NULL for historical row
        assertTrue("edgeModelId must be null for v1 row", cursor.isNull(cursor.getColumnIndexOrThrow("edgeModelId")))
        assertTrue("edgeModelVersion must be null for v1 row", cursor.isNull(cursor.getColumnIndexOrThrow("edgeModelVersion")))
        assertTrue("cloudModelId must be null for v1 row", cursor.isNull(cursor.getColumnIndexOrThrow("cloudModelId")))
        assertTrue("cloudModelVersion must be null for v1 row", cursor.isNull(cursor.getColumnIndexOrThrow("cloudModelVersion")))
        assertTrue("localPredictedClass must be null for v1 row", cursor.isNull(cursor.getColumnIndexOrThrow("localPredictedClass")))
        assertTrue("localPredictedClassId must be null for v1 row", cursor.isNull(cursor.getColumnIndexOrThrow("localPredictedClassId")))
        assertTrue("finalPredictedClass must be null for v1 row", cursor.isNull(cursor.getColumnIndexOrThrow("finalPredictedClass")))
        assertTrue("finalPredictedClassId must be null for v1 row", cursor.isNull(cursor.getColumnIndexOrThrow("finalPredictedClassId")))
        assertTrue("predictionChangedByCloud must be null for v1 row", cursor.isNull(cursor.getColumnIndexOrThrow("predictionChangedByCloud")))
        cursor.close()
        migratedDb.close()

        // Step 4: Open migrated database via Room
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val roomDb = Room.databaseBuilder(targetContext, AppDatabase::class.java, testDbName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

        // Step 5: Insert new v2 entity with model provenance and prediction metadata
        val v2Entity = BenchmarkEntity(
            id = 2,
            timestamp = 2000L,
            modelVersion = "v2.0-real",
            executionSource = "REMOTE_GPU_CLOUD",
            totalLatencyMs = 120L,
            localLatencyMs = 25L,
            cloudLatencyMs = 95L,
            localConfidence = 0.60f,
            finalConfidence = 0.94f,
            routingThreshold = 0.75f,
            cloudAttempted = true,
            escalationReason = "LOW_CONFIDENCE",
            fallbackReason = null,
            edgeModelId = "mobilenetv3_small_dynamic_mixed",
            edgeModelVersion = "m8_locked_v1",
            cloudModelId = "mobilenet_v3_large",
            cloudModelVersion = "1.0.0",
            localPredictedClass = "terrier",
            localPredictedClassId = 180,
            finalPredictedClass = "Old English sheepdog",
            finalPredictedClassId = 7,
            predictionChangedByCloud = true
        )
        val dao = roomDb.benchmarkDao()
        dao.insert(v2Entity)

        // Step 6: Query back through DAO and assert both rows
        val records = dao.observeRecentBenchmarks().first()
        assertEquals("Should contain 2 records total", 2, records.size)

        val record2 = records.first { it.id == 2L }
        assertEquals(2000L, record2.timestamp)
        assertEquals("v2.0-real", record2.modelVersion)
        assertEquals("REMOTE_GPU_CLOUD", record2.executionSource)
        assertEquals("mobilenetv3_small_dynamic_mixed", record2.edgeModelId)
        assertEquals("m8_locked_v1", record2.edgeModelVersion)
        assertEquals("mobilenet_v3_large", record2.cloudModelId)
        assertEquals("1.0.0", record2.cloudModelVersion)
        assertEquals("terrier", record2.localPredictedClass)
        assertEquals(180, record2.localPredictedClassId)
        assertEquals("Old English sheepdog", record2.finalPredictedClass)
        assertEquals(7, record2.finalPredictedClassId)
        assertEquals(true, record2.predictionChangedByCloud)

        val record1 = records.first { it.id == 1L }
        assertEquals(1000L, record1.timestamp)
        assertEquals("v1.0-tiny", record1.modelVersion)
        assertEquals("LOCAL_ON_DEVICE", record1.executionSource)
        assertNull(record1.edgeModelId)
        assertNull(record1.edgeModelVersion)
        assertNull(record1.cloudModelId)
        assertNull(record1.cloudModelVersion)
        assertNull(record1.localPredictedClass)
        assertNull(record1.localPredictedClassId)
        assertNull(record1.finalPredictedClass)
        assertNull(record1.finalPredictedClassId)
        assertNull(record1.predictionChangedByCloud)

        roomDb.close()
    }
}
