package es.voghdev.chromecastsample.persistence

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import es.voghdev.chromecastsample.R
import es.voghdev.chromecastsample.persistence.entities.CourseEntity
import es.voghdev.chromecastsample.persistence.entities.TitleEmbedded
import java.io.InputStream
import java.io.InputStreamReader

object CourseSeeder {

    private const val TAG = "CourseSeeder"
    private const val BATCH_SIZE = 500

    data class Result(val skipped: Boolean, val inserted: Int, val durationMs: Long)

    suspend fun seedIfEmpty(context: Context, db: AppDatabase): Result {
        val start = System.currentTimeMillis()
        val dao = db.coursesDao()
        val existing = dao.count()
        if (existing > 0) {
            Log.d(TAG, "Skip seed: courses table already has $existing rows")
            return Result(skipped = true, inserted = 0, durationMs = System.currentTimeMillis() - start)
        }

        var inserted = 0
        context.resources.openRawResource(R.raw.all_courses).use { stream ->
            streamCourses(stream) { batch ->
                dao.upsertAll(batch)
                inserted += batch.size
            }
        }
        val durationMs = System.currentTimeMillis() - start
        Log.d(TAG, "Seeded $inserted courses in ${durationMs}ms")
        return Result(skipped = false, inserted = inserted, durationMs = durationMs)
    }

    private suspend fun streamCourses(
        stream: InputStream,
        onBatch: suspend (List<CourseEntity>) -> Unit,
    ) {
        JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "courses" -> readCoursesArray(reader, onBatch)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
    }

    private suspend fun readCoursesArray(
        reader: JsonReader,
        onBatch: suspend (List<CourseEntity>) -> Unit,
    ) {
        reader.beginArray()
        val buffer = ArrayList<CourseEntity>(BATCH_SIZE)
        while (reader.hasNext()) {
            readCourse(reader)?.let(buffer::add)
            if (buffer.size >= BATCH_SIZE) {
                onBatch(buffer.toList())
                buffer.clear()
            }
        }
        reader.endArray()
        if (buffer.isNotEmpty()) {
            onBatch(buffer.toList())
            buffer.clear()
        }
    }

    private fun readCourse(reader: JsonReader): CourseEntity? {
        var id: Int? = null
        var title: TitleEmbedded? = null
        var aasmState: String? = null
        var mainCategoryId = 0
        var teacherId: String? = null
        var coverColor: String? = null
        var directoryHidden: Boolean? = null
        var nonPurchaseable: Boolean? = null
        var isSpecialization: Boolean? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextIntOrNull()
                "title" -> title = readTitle(reader)
                "aasmState" -> aasmState = reader.nextStringOrNull()
                "mainCategoryId" -> mainCategoryId = reader.nextIntOrNull() ?: 0
                "teacher" -> teacherId = readTeacherId(reader)
                "coverColor" -> coverColor = reader.nextStringOrNull()
                "directoryHidden" -> directoryHidden = reader.nextBooleanOrNull()
                "nonPurchaseable" -> nonPurchaseable = reader.nextBooleanOrNull()
                "specialization" -> isSpecialization = reader.nextBooleanOrNull()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return id?.let {
            CourseEntity(
                id = it,
                title = title,
                aasmState = aasmState,
                mainCategory = mainCategoryId,
                teacherId = teacherId,
                coverId = null,
                colorPlaceHolder = coverColor,
                directoryHidden = directoryHidden,
                nonPurchaseable = nonPurchaseable,
                isSpecialization = isSpecialization,
                isGuidedProject = null,
                isDeepDive = null,
            )
        }
    }

    private fun readTitle(reader: JsonReader): TitleEmbedded? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        var es: String? = null
        var en: String? = null
        var pt: String? = null
        var de: String? = null
        var fr: String? = null
        var it: String? = null
        var pl: String? = null
        var nl: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "es" -> es = reader.nextStringOrNull()
                "en" -> en = reader.nextStringOrNull()
                "pt" -> pt = reader.nextStringOrNull()
                "de" -> de = reader.nextStringOrNull()
                "fr" -> fr = reader.nextStringOrNull()
                "it" -> it = reader.nextStringOrNull()
                "pl" -> pl = reader.nextStringOrNull()
                "nl" -> nl = reader.nextStringOrNull()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return TitleEmbedded(es = es, en = en, pt = pt, de = de, fr = fr, it = it, pl = pl, nl = nl)
    }

    private fun readTeacherId(reader: JsonReader): String? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        var teacherId: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> teacherId = reader.nextIntOrNull()?.toString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return teacherId
    }

    private fun JsonReader.nextStringOrNull(): String? =
        if (peek() == JsonToken.NULL) { nextNull(); null } else nextString()

    private fun JsonReader.nextIntOrNull(): Int? =
        if (peek() == JsonToken.NULL) { nextNull(); null } else nextInt()

    private fun JsonReader.nextBooleanOrNull(): Boolean? =
        if (peek() == JsonToken.NULL) { nextNull(); null } else nextBoolean()
}
