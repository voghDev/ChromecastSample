package es.voghdev.chromecastsample.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import es.voghdev.chromecastsample.persistence.entities.CourseUnitEntity
import es.voghdev.chromecastsample.persistence.relations.UnitWithLessons

@Dao
interface CourseUnitsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(units: List<CourseUnitEntity>)

    @Transaction
    @Query("SELECT * FROM course_units WHERE course_id = :courseId ORDER BY position ASC")
    suspend fun unitsWithLessons(courseId: Int): List<UnitWithLessons>

    @Query("DELETE FROM course_units WHERE course_id = :courseId")
    suspend fun deleteByCourse(courseId: Int)
}
