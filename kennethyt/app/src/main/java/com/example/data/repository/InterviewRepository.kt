package com.example.data.repository

import android.net.Uri
import com.example.data.local.dao.InterviewDao
import com.example.data.local.dao.InterviewWithDetails
import com.example.data.local.dao.SeedInterviewSpec
import com.example.data.local.entity.InterviewEntity
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.media.MediaStorageManager
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.model.Interview
import com.example.domain.model.MediaItem
import com.example.domain.model.MediaType
import com.example.domain.model.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

object DefaultInterviewSeed {
    const val CURRENT_SEED_VERSION = 2

    val v1ExampleTitles = listOf(
        "GTA 6: significado",
        "Nombre de Jason",
        "Nombre de Lucía",
        "Edad de Jason",
        "Trabajo de Lucía",
        "Relación de Jason y Lucía",
        "Ubicación del mapa",
        "Fecha de lanzamiento",
        "Precio del juego",
        "Plataformas disponibles",
        "Qué esperas de GTA 6",
        "¿Lo comprarías?"
    )

    val v1FirstInterviewQuestions = listOf(
        "¿Qué significa G-TA?",
        "¿Y de dónde sacó ese nombre?",
        "Bueno, ¿y ese 6 qué significa?",
        "¿Y qué pasó con los otros cinco? JAJAJA"
    )

    val realInterviews: List<SeedInterviewSpec> = listOf(
        SeedInterviewSpec(
            title = "GTA 6: significado",
            questions = listOf(
                "¿Qué significan las siglas GTA?",
                "¿Y de dónde sacó ese nombre?",
                "Bueno, ¿y ese 6 qué significa?",
                "¿Y qué pasó con los otros cinco? JAJAJA"
            )
        ),
        SeedInterviewSpec(
            title = "Nombre de Jason",
            questions = listOf(
                "¿Cómo cree que se llama este muchacho?",
                "¿De qué cree que trabaja?",
                "¿Y usted lo contrataría?",
                "¿Qué consejo le daría para que triunfe en la vida?",
                "¿Y usted ya siguió ese consejo o todavía está en eso?"
            )
        ),
        SeedInterviewSpec(
            title = "País del juego",
            questions = listOf(
                "¿Qué país cree que es?",
                "¿Y usted viviría ahí?",
                "¿Y se llevaría a su pareja o se iría solito?",
                "¿Y qué sería lo primero que haría al llegar?",
                "Bueno, mexicanos, ¡ahí les mandamos a su nuevo vecino! ¡Recíbanlo!"
            )
        ),
        SeedInterviewSpec(
            title = "Nombre de Lucía",
            questions = listOf(
                "¿Qué nombre le pondría a esta muchacha?",
                "¿Y por qué cree que tiene eso en la mano?",
                "¿Usted cree que lo sabe usar?",
                "¿Y si se le acerca con eso, qué haría?",
                "¿Y si le dice que no tenga miedo, usted le cree?"
            )
        ),
        SeedInterviewSpec(
            title = "El señor de las llaves",
            questions = listOf(
                "¿A quién le recuerda este señor?",
                "¿Y qué tiene de parecido con esa persona?",
                "¿Usted le confiaría las llaves de su casa?",
                "¿Y qué cree que sería lo primero que haría estando solo en su casa?",
                "¿Y si cuando usted regresa ya cambió las cerraduras y no lo deja entrar?"
            )
        ),
        SeedInterviewSpec(
            title = "Dentro de GTA 6",
            questions = listOf(
                "¿Qué cree que se hace en este videojuego?",
                "¿Y a usted le gustaría meterse en ese juego?",
                "¿Qué personaje cree que sería usted: el bueno o el malo?",
                "¿Y qué haría si se encuentra a la policía?",
                "¿Y si lo meten preso, quién de su familia cree que iría a sacarlo?"
            )
        ),
        SeedInterviewSpec(
            title = "Precio de GTA 6",
            questions = listOf(
                "¿Cuánto cree que cuesta este videojuego?",
                "¿Y por qué cree que vale tanto?",
                "¿Usted lo compraría?",
                "Aunque no lo crea, este juego cuesta 80 dólares. ¿Qué haría usted con ese dinero?",
                "¿Qué le manda a decir a esa gente que gastó esa plata en el juego?"
            )
        ),
        SeedInterviewSpec(
            title = "Las cinco estrellas",
            questions = listOf(
                "¿Qué cree que significan estas cinco estrellas?",
                "¿Y qué tendría que hacer una persona para conseguirlas?",
                "¿Y usted cuántas estrellas cree que se ganaría?",
                "¿Y su familia sabe que usted anda haciendo esas cosas?"
            )
        ),
        SeedInterviewSpec(
            title = "Mi primo Jason en Perú",
            questions = listOf(
                "Este es mi primo Jason, se fue para Perú.",
                "¿De casualidad usted no lo ha visto por allá?",
                "¿Y dónde cree que puede andar metido?",
                "¿Anda trabajando o haciendo vagancias?",
                "Bueno, si lo ve, dígale que su tío lo anda buscando.",
                "¿Y si le pregunta que para qué lo busca, qué le va a decir?"
            )
        ),
        SeedInterviewSpec(
            title = "Personaje más peligroso",
            questions = listOf(
                "¿Cuál cree que es el personaje más peligroso de todos estos?",
                "¿Y qué le vio para pensar eso?",
                "¿Usted se atrevería a subirse a un carro con él?",
                "¿Y si le dice que lo va a llevar a un lugar secreto?",
                "¿Y usted se sube o comienza a rezar?"
            )
        ),
        SeedInterviewSpec(
            title = "El tacón en el mapa",
            questions = listOf(
                "¿Qué cree que significa este tacón en el mapa?",
                "¿Y qué cree que hacen ahí adentro?",
                "¿Quiere verlo? ¿Seguro?",
                "¿Le gustó lo que vio?",
                "¿Usted ha entrado alguna vez a un lugar así?",
                "¿Y su pareja sabe? JAJAJA"
            )
        ),
        SeedInterviewSpec(
            title = "Apodo y cita",
            questions = listOf(
                "¿Qué apodo le pondría a esta persona?",
                "¿Qué le hace falta para ser perfecta?",
                "¿Y así sí se anima a invitarla a una cita?",
                "¿Dónde la llevaría?"
            )
        )
    )
}

interface InterviewRepository {
    val interviews: Flow<List<Interview>>
    fun observeInterview(interviewId: Long): Flow<Interview?>
    suspend fun ensureSeeded()
    suspend fun createInterview(title: String): Result<Long>
    suspend fun updateInterviewTitle(interviewId: Long, newTitle: String): Result<Unit>
    suspend fun deleteInterview(interviewId: Long): Result<Unit>
    suspend fun duplicateInterview(interviewId: Long): Result<Long>
    suspend fun reorderInterviews(orderedIds: List<Long>)
    suspend fun addQuestion(interviewId: Long, text: String): Result<Long>
    suspend fun updateQuestion(interviewId: Long, questionId: Long, text: String): Result<Unit>
    suspend fun deleteQuestion(interviewId: Long, questionId: Long)
    suspend fun reorderQuestions(interviewId: Long, orderedQuestionIds: List<Long>)
    suspend fun saveMediaFromUri(interviewId: Long, uri: Uri, type: MediaType): Result<MediaItem>
    suspend fun removeMedia(interviewId: Long, type: MediaType)
}

class LocalInterviewRepository(
    private val dao: InterviewDao,
    private val mediaStorage: MediaStorageManager,
    private val preferences: UserPreferencesRepository
) : InterviewRepository {

    override val interviews: Flow<List<Interview>> =
        dao.observeAllInterviewsWithDetails().map { list ->
            list.map { it.toDomain() }
        }

    override fun observeInterview(interviewId: Long): Flow<Interview?> =
        dao.observeInterviewWithDetails(interviewId).map { it?.toDomain() }

    override suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        mediaStorage.cleanupOrphanTempFiles()
        val seedVersion = preferences.getSeedContentVersion()
        val existingCount = dao.getInterviewCount()
        val now = System.currentTimeMillis()

        when {
            seedVersion == 0 && existingCount == 0 -> {
                dao.seedInitialInterviews(
                    specs = DefaultInterviewSeed.realInterviews,
                    now = now
                )
                preferences.setSeedContentVersion(DefaultInterviewSeed.CURRENT_SEED_VERSION)
            }

            seedVersion < DefaultInterviewSeed.CURRENT_SEED_VERSION && existingCount > 0 -> {
                dao.migrateV1SeedToV2(
                    v1Titles = DefaultInterviewSeed.v1ExampleTitles,
                    v1FirstQuestions = DefaultInterviewSeed.v1FirstInterviewQuestions,
                    v2Specs = DefaultInterviewSeed.realInterviews,
                    now = now
                )
                preferences.setSeedContentVersion(DefaultInterviewSeed.CURRENT_SEED_VERSION)
            }

            seedVersion < DefaultInterviewSeed.CURRENT_SEED_VERSION -> {
                preferences.setSeedContentVersion(DefaultInterviewSeed.CURRENT_SEED_VERSION)
            }
        }
    }

    override suspend fun createInterview(title: String): Result<Long> = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("El título no puede estar vacío."))
        }
        val nextOrder = (dao.getMaxInterviewSortOrder() ?: -1) + 1
        val now = System.currentTimeMillis()
        val id = dao.insertInterview(
            InterviewEntity(
                title = cleanTitle,
                sortOrder = nextOrder,
                createdAt = now,
                updatedAt = now
            )
        )
        Result.success(id)
    }

    override suspend fun updateInterviewTitle(interviewId: Long, newTitle: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val cleanTitle = newTitle.trim()
            if (cleanTitle.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("El título no puede estar vacío."))
            }
            dao.updateInterviewTitle(interviewId, cleanTitle, System.currentTimeMillis())
            Result.success(Unit)
        }

    override suspend fun deleteInterview(interviewId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val mediaPaths = dao.getMediaForInterview(interviewId).map { it.localPath }
        dao.deleteInterviewById(interviewId)
        mediaPaths.forEach { mediaStorage.deleteFileSafely(it) }
        Result.success(Unit)
    }

    override suspend fun duplicateInterview(interviewId: Long): Result<Long> = withContext(Dispatchers.IO) {
        val source = dao.getInterviewWithDetails(interviewId)
            ?: return@withContext Result.failure(IllegalStateException("Entrevista no encontrada."))

        val now = System.currentTimeMillis()
        val nextOrder = (dao.getMaxInterviewSortOrder() ?: -1) + 1
        val newInterviewId = dao.insertInterview(
            InterviewEntity(
                title = "${source.interview.title} (Copia)",
                sortOrder = nextOrder,
                createdAt = now,
                updatedAt = now
            )
        )

        val sortedQuestions = source.questions.sortedBy { it.sortOrder }
        if (sortedQuestions.isNotEmpty()) {
            dao.insertQuestions(
                sortedQuestions.mapIndexed { idx, question ->
                    QuestionEntity(
                        interviewId = newInterviewId,
                        text = question.text,
                        sortOrder = idx
                    )
                }
            )
        }

        for (media in source.mediaItems) {
            val copiedPath = mediaStorage.copyMediaFile(media.localPath, media.type)
            if (copiedPath != null) {
                dao.insertOrReplaceMedia(
                    MediaEntity(
                        interviewId = newInterviewId,
                        type = media.type,
                        localPath = copiedPath,
                        mimeType = media.mimeType,
                        originalName = media.originalName,
                        width = media.width,
                        height = media.height,
                        durationMs = media.durationMs,
                        createdAt = now
                    )
                )
            }
        }
        Result.success(newInterviewId)
    }

    override suspend fun reorderInterviews(orderedIds: List<Long>) = withContext(Dispatchers.IO) {
        dao.reorderInterviews(orderedIds)
    }

    override suspend fun addQuestion(interviewId: Long, text: String): Result<Long> =
        withContext(Dispatchers.IO) {
            val cleanText = text.trim()
            if (cleanText.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("La pregunta no puede estar vacía."))
            }
            val nextOrder = (dao.getMaxQuestionSortOrder(interviewId) ?: -1) + 1
            val id = dao.insertQuestion(
                QuestionEntity(
                    interviewId = interviewId,
                    text = cleanText,
                    sortOrder = nextOrder
                )
            )
            dao.updateInterviewTimestamp(interviewId, System.currentTimeMillis())
            Result.success(id)
        }

    override suspend fun updateQuestion(
        interviewId: Long,
        questionId: Long,
        text: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("La pregunta no puede estar vacía."))
        }
        dao.updateQuestionText(questionId, cleanText)
        dao.updateInterviewTimestamp(interviewId, System.currentTimeMillis())
        Result.success(Unit)
    }

    override suspend fun deleteQuestion(interviewId: Long, questionId: Long) =
        withContext(Dispatchers.IO) {
            dao.deleteQuestionById(questionId)
            dao.updateInterviewTimestamp(interviewId, System.currentTimeMillis())
        }

    override suspend fun reorderQuestions(interviewId: Long, orderedQuestionIds: List<Long>) =
        withContext(Dispatchers.IO) {
            dao.reorderQuestions(interviewId, orderedQuestionIds, System.currentTimeMillis())
        }

    override suspend fun saveMediaFromUri(
        interviewId: Long,
        uri: Uri,
        type: MediaType
    ): Result<MediaItem> = withContext(Dispatchers.IO) {
        val existing = dao.getMediaByType(interviewId, type)
        val imported = mediaStorage.importMedia(uri, type).getOrElse { error ->
            return@withContext Result.failure(error)
        }

        val now = System.currentTimeMillis()
        val entity = MediaEntity(
            id = existing?.id ?: 0L,
            interviewId = interviewId,
            type = type,
            localPath = imported.localPath,
            mimeType = imported.mimeType,
            originalName = imported.originalName,
            width = imported.width,
            height = imported.height,
            durationMs = imported.durationMs,
            createdAt = now
        )

        return@withContext try {
            val insertedId = dao.insertOrReplaceMedia(entity)
            dao.updateInterviewTimestamp(interviewId, now)
            if (existing != null && existing.localPath != imported.localPath) {
                mediaStorage.deleteFileSafely(existing.localPath)
            }
            Result.success(entity.copy(id = insertedId).toDomain())
        } catch (e: Exception) {
            mediaStorage.deleteFileSafely(imported.localPath)
            Result.failure(e)
        }
    }

    override suspend fun removeMedia(interviewId: Long, type: MediaType) {
        withContext(Dispatchers.IO) {
            val existing = dao.getMediaByType(interviewId, type)
            dao.deleteMediaByType(interviewId, type)
            dao.updateInterviewTimestamp(interviewId, System.currentTimeMillis())
            mediaStorage.deleteFileSafely(existing?.localPath)
        }
    }
}

private fun InterviewWithDetails.toDomain(): Interview {
    val domainQuestions = questions
        .sortedWith(compareBy<QuestionEntity> { it.sortOrder }.thenBy { it.id })
        .map { it.toDomain() }

    val image = mediaItems.firstOrNull { it.type == MediaType.IMAGE }?.toDomain()
    val video = mediaItems.firstOrNull { it.type == MediaType.VIDEO }?.toDomain()

    return Interview(
        id = interview.id,
        title = interview.title,
        sortOrder = interview.sortOrder,
        questions = domainQuestions,
        imageMedia = image,
        videoMedia = video,
        createdAt = interview.createdAt,
        updatedAt = interview.updatedAt
    )
}

private fun QuestionEntity.toDomain(): Question = Question(
    id = id,
    interviewId = interviewId,
    text = text,
    sortOrder = sortOrder
)

private fun MediaEntity.toDomain(): MediaItem = MediaItem(
    id = id,
    interviewId = interviewId,
    type = type,
    localPath = localPath,
    mimeType = mimeType,
    originalName = originalName,
    width = width,
    height = height,
    durationMs = durationMs,
    createdAt = createdAt
)
