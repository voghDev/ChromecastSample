package es.voghdev.chromecastsample.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "course_units",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = CASCADE,
        ),
    ],
    indices = [
        Index("course_id"),
        Index(value = ["course_id", "position"], unique = true),
    ],
)
data class CourseUnitEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "course_id") val courseId: Int,
    val position: Int,
    val title: String? = null,
    val body: String? = null,
    @ColumnInfo(name = "completed_by_user") val completedByUser: Boolean? = null,
)
