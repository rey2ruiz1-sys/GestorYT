package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.example.data.local.entity.InterviewEntity
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.QuestionEntity
import com.example.domain.model.MediaType
import kotlinx.coroutines.flow.Flow

data class InterviewWithDetails(
    @Embedded val interview: InterviewEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "interviewId"
    )
    val questions: List<QuestionEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "interviewId"
    )
    val mediaItems: List<MediaEntity>
)

data class SeedInterviewSpec(
    val title: String,
    val questions: List<String>
)

@Dao
interface InterviewDao {

    @Transaction
    @Query("SELECT * FROM interviews ORDER BY sortOrder ASC, id ASC")
    fun observeAllInterviewsWithDetails(): Flow<List<InterviewWithDetails>>

    @Transaction
    @Query("SELECT * FROM interviews WHERE id = :interviewId")
    fun observeInterviewWithDetails(interviewId: Long): Flow<InterviewWithDetails?>

    @Transaction
    @Query("SELECT * FROM interviews WHERE id = :interviewId")
    suspend fun getInterviewWithDetails(interviewId: Long): InterviewWithDetails?

    @Transaction
    @Query("SELECT * FROM interviews ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllInterviewsWithDetailsOnce(): List<InterviewWithDetails>

    @Query("SELECT COUNT(*) FROM interviews")
    suspend fun getInterviewCount(): Int

    @Query("SELECT MAX(sortOrder) FROM interviews")
    suspend fun getMaxInterviewSortOrder(): Int?

    @Query("SELECT MAX(sortOrder) FROM questions WHERE interviewId = :interviewId")
    suspend fun getMaxQuestionSortOrder(interviewId: Long): Int?

    @Query("SELECT * FROM media_items WHERE interviewId = :interviewId")
    suspend fun getMediaForInterview(interviewId: Long): List<MediaEntity>

    @Query("SELECT * FROM media_items WHERE interviewId = :interviewId AND type = :type LIMIT 1")
    suspend fun getMediaByType(interviewId: Long, type: MediaType): MediaEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInterview(interview: InterviewEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInterviews(interviews: List<InterviewEntity>): List<Long>

    @Query("UPDATE interviews SET title = :title, updatedAt = :updatedAt WHERE id = :interviewId")
    suspend fun updateInterviewTitle(interviewId: Long, title: String, updatedAt: Long)

    @Query("UPDATE interviews SET updatedAt = :updatedAt WHERE id = :interviewId")
    suspend fun updateInterviewTimestamp(interviewId: Long, updatedAt: Long)

    @Query("UPDATE interviews SET sortOrder = :sortOrder WHERE id = :interviewId")
    suspend fun updateInterviewSortOrder(interviewId: Long, sortOrder: Int)

    @Query("DELETE FROM interviews WHERE id = :interviewId")
    suspend fun deleteInterviewById(interviewId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQuestion(question: QuestionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Query("UPDATE questions SET text = :text WHERE id = :questionId")
    suspend fun updateQuestionText(questionId: Long, text: String)

    @Query("UPDATE questions SET sortOrder = :sortOrder WHERE id = :questionId")
    suspend fun updateQuestionSortOrder(questionId: Long, sortOrder: Int)

    @Query("DELETE FROM questions WHERE id = :questionId")
    suspend fun deleteQuestionById(questionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceMedia(media: MediaEntity): Long

    @Query("DELETE FROM media_items WHERE interviewId = :interviewId AND type = :type")
    suspend fun deleteMediaByType(interviewId: Long, type: MediaType)

    @Transaction
    suspend fun reorderInterviews(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            updateInterviewSortOrder(id, index)
        }
    }

    @Transaction
    suspend fun reorderQuestions(interviewId: Long, orderedIds: List<Long>, updatedAt: Long) {
        orderedIds.forEachIndexed { index, id ->
            updateQuestionSortOrder(id, index)
        }
        updateInterviewTimestamp(interviewId, updatedAt)
    }

    @Transaction
    suspend fun seedInitialInterviews(
        specs: List<SeedInterviewSpec>,
        now: Long
    ) {
        if (getInterviewCount() > 0) return
        val entities = specs.mapIndexed { index, spec ->
            InterviewEntity(
                title = spec.title,
                sortOrder = index,
                createdAt = now,
                updatedAt = now
            )
        }
        val insertedIds = insertInterviews(entities)
        val allQuestions = mutableListOf<QuestionEntity>()
        insertedIds.forEachIndexed { index, interviewId ->
            val questions = specs[index].questions
            questions.forEachIndexed { qIdx, qText ->
                allQuestions.add(
                    QuestionEntity(
                        interviewId = interviewId,
                        text = qText,
                        sortOrder = qIdx
                    )
                )
            }
        }
        if (allQuestions.isNotEmpty()) {
            insertQuestions(allQuestions)
        }
    }

    /**
     * Controlled one-time upgrade from v1 example interviews to v2 real interview scripts.
     * - Preserves interview IDs, sortOrder, and all imported images/videos.
     * - Updates titles only when they still match the old v1 example title.
     * - Populates real questions when an interview has no questions yet, or updates unmodified v1
     *   example questions in place without overwriting user-customized scripts.
     */
    @Transaction
    suspend fun migrateV1SeedToV2(
        v1Titles: List<String>,
        v1FirstQuestions: List<String>,
        v2Specs: List<SeedInterviewSpec>,
        now: Long
    ) {
        val existing = getAllInterviewsWithDetailsOnce()
        if (existing.isEmpty()) return

        val claimedIds = mutableSetOf<Long>()

        v2Specs.forEachIndexed { index, v2Spec ->
            val v1Title = v1Titles.getOrNull(index)
            val match = existing.firstOrNull {
                it.interview.id !in claimedIds && v1Title != null && it.interview.title == v1Title
            } ?: existing.getOrNull(index)?.takeIf {
                it.interview.id !in claimedIds &&
                    v1Title != null &&
                    it.interview.title == v1Title
            } ?: return@forEachIndexed

            claimedIds.add(match.interview.id)

            // Update title only if it still matches the unedited v1 example title
            if (match.interview.title == v1Title && match.interview.title != v2Spec.title) {
                updateInterviewTitle(match.interview.id, v2Spec.title, now)
            }

            val sortedExistingQuestions = match.questions.sortedWith(
                compareBy<QuestionEntity> { it.sortOrder }.thenBy { it.id }
            )

            if (sortedExistingQuestions.isEmpty()) {
                // v1 interviews 2..12 started empty; populate real script safely
                if (v2Spec.questions.isNotEmpty()) {
                    insertQuestions(
                        v2Spec.questions.mapIndexed { qIdx, qText ->
                            QuestionEntity(
                                interviewId = match.interview.id,
                                text = qText,
                                sortOrder = qIdx
                            )
                        }
                    )
                    updateInterviewTimestamp(match.interview.id, now)
                }
            } else if (index == 0) {
                // Interview 01 had 4 v1 sample questions; update only unedited v1 texts in place
                sortedExistingQuestions.forEachIndexed { qIdx, existingQuestion ->
                    val oldSample = v1FirstQuestions.getOrNull(qIdx)
                    val newScript = v2Spec.questions.getOrNull(qIdx)
                    if (oldSample != null && newScript != null && existingQuestion.text == oldSample && newScript != oldSample) {
                        updateQuestionText(existingQuestion.id, newScript)
                    }
                }
                updateInterviewTimestamp(match.interview.id, now)
            }
            // Any customized questions on any interview are preserved untouched
        }
    }
}
