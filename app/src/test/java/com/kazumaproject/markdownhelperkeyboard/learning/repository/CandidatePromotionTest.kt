package com.kazumaproject.markdownhelperkeyboard.learning.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearningScorePolicy
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CandidatePromotionTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LearnRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = LearnRepository(database.learnDao())
    }

    @After fun tearDown() = database.close()

    @Test fun promotionChangesRealPathRankingAndInvalidatesCachedWords() = runBlocking {
        repository.insertLearnedData(LearnEntity("はし", "橋", score = 100, leftId = 1, rightId = 1))
        repository.insertLearnedData(LearnEntity("はし", "箸", score = 5000, leftId = 1, rightId = 1))
        assertEquals("橋", convert().first())
        assertTrue(repository.promoteCandidate("はし", "箸", 1, 1))
        assertEquals("箸", convert().first())
    }

    @Test fun repeatedPromotionPreservesRowMetadataAndOtherReadings() = runBlocking {
        repository.insertLearnedData(LearnEntity("はし", "箸", score = 5000, usageCount = 7, lastUsedAt = 42, leftId = 1, rightId = 2))
        repository.insertLearnedData(LearnEntity("ばし", "箸", score = 4000))
        repository.promoteCandidate("はし", "箸", 3, 4)
        val once = repository.findLearnDataByInputAndOutput("はし", "箸")!!
        repeat(3) { repository.promoteCandidate("はし", "箸", 3, 4) }
        assertEquals(once, repository.findLearnDataByInputAndOutput("はし", "箸"))
        assertEquals(7, once.usageCount)
        assertEquals(42L, once.lastUsedAt)
        assertEquals(1.toShort(), once.leftId)
        assertEquals(4000, repository.findLearnDataByInputAndOutput("ばし", "箸")!!.score)
        assertEquals(2, repository.allSuspend().size)
    }

    @Test fun newPreferenceIsExactAndSurvivesNormalLearning() = runBlocking {
        repository.findCommonPrefixes("はし") // cache an empty bucket first
        repository.promoteCandidate("はし", "箸", 1, 1)
        repository.upsertLearnedData(LearnEntity("はし", "箸", score = 2000))
        assertEquals(LearningScorePolicy.PREFERRED_CANDIDATE_SCORE,
            repository.findExactMatchesForConversion("はし").single().score)
        assertTrue(repository.findExactMatchesForConversion("は").isEmpty())
        assertFalse(repository.promoteCandidate("", "箸"))
        assertFalse(repository.promoteCandidate("はし", "(^^)"))
        assertEquals(1, repository.allSuspend().size)
    }

    @Test fun preferenceParticipatesInPredictiveRankingWithoutChangingExactMatchPriority() = runBlocking {
        repository.insertLearnedData(LearnEntity("は", "葉", score = 0))
        repository.insertLearnedData(LearnEntity("はしる", "走る", score = 0))
        repository.promoteCandidate("はし", "箸")
        assertEquals(listOf("葉", "箸", "走る"),
            repository.predictiveSearchByInput("は", 10).map { it.out })
    }

    @Test fun preferenceDoesNotWorsenAnExistingLowerCost() = runBlocking {
        val entry = LearnEntity("はし", "箸", score = -200_000)
        repository.insertLearnedData(entry)
        repository.promoteCandidate("はし", "箸")
        assertEquals(entry.score, repository.findLearnDataByInputAndOutput("はし", "箸")!!.score)
    }

    private suspend fun convert(): List<String> {
        // Same learned Node fields as GraphBuilder, through the production path algorithm.
        val words = repository.findExactMatchesForConversion("はし")
        fun boundary(text: String, position: Int) = Node(0, 0, 0, 0, 0, text, 0, "", position)
        val graph = mutableMapOf(
            0 to mutableListOf(boundary("BOS", 0)),
            2 to words.map { entry ->
                Node(entry.leftId ?: 1, entry.rightId ?: 1, entry.score, entry.score,
                    entry.score, entry.out, 2, entry.input, 0,
                    candidateSource = CandidateSource.LEARNED_DICTIONARY)
            }.toMutableList(),
            3 to mutableListOf(boundary("EOS", 3)),
        )
        return FindPath().backwardAStar(graph, 2, ShortArray(4), 2, 2).map { it.string }
    }
}
