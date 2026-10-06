package es.voghdev.chromecastsample.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import es.voghdev.chromecastsample.persistence.entities.CourseEntity
import es.voghdev.chromecastsample.persistence.relations.CourseWithContent
import kotlinx.coroutines.flow.Flow

@Dao
interface CoursesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(course: CourseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(courses: List<CourseEntity>)

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun findById(id: Int): CourseEntity?

    @Transaction
    @Query("SELECT * FROM courses WHERE id = :id")
    fun observeCourseWithContent(id: Int): Flow<CourseWithContent?>

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun count(): Int

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM courses")
    suspend fun clear()
}
