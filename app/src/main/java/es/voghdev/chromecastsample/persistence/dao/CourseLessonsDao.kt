package es.voghdev.chromecastsample.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.voghdev.chromecastsample.persistence.entities.CourseLessonEntity

@Dao
interface CourseLessonsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(lessons: List<CourseLessonEntity>)

    @Query("SELECT * FROM course_lessons WHERE unit_id = :unitId ORDER BY position ASC")
    suspend fun forUnit(unitId: Int): List<CourseLessonEntity>

    @Query("DELETE FROM course_lessons WHERE unit_id = :unitId")
    suspend fun deleteByUnit(unitId: Int)
}
