package es.voghdev.chromecastsample.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import es.voghdev.chromecastsample.persistence.dao.CourseLessonsDao
import es.voghdev.chromecastsample.persistence.dao.CourseUnitsDao
import es.voghdev.chromecastsample.persistence.dao.CoursesDao
import es.voghdev.chromecastsample.persistence.entities.CourseEntity
import es.voghdev.chromecastsample.persistence.entities.CourseLessonEntity
import es.voghdev.chromecastsample.persistence.entities.CourseUnitEntity

@Database(
    entities = [
        CourseEntity::class,
        CourseUnitEntity::class,
        CourseLessonEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun coursesDao(): CoursesDao
    abstract fun courseUnitsDao(): CourseUnitsDao
    abstract fun courseLessonsDao(): CourseLessonsDao

    companion object {
        const val DB_NAME = "chromecast-sample.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                ).build().also { instance = it }
            }
    }
}
