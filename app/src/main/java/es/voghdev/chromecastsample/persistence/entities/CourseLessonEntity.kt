package es.voghdev.chromecastsample.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "course_lessons",
    foreignKeys = [
        ForeignKey(
            entity = CourseUnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unit_id"],
            onDelete = CASCADE,
        ),
    ],
    indices = [
        Index("unit_id"),
        Index(value = ["unit_id", "position"], unique = true),
    ],
)
data class CourseLessonEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "unit_id") val unitId: Int,
    val position: Int,
    val title: String? = null,
    val body: String? = null,
    @ColumnInfo(name = "is_public") val isPublic: Boolean? = null,
    @ColumnInfo(name = "video_item_id") val videoItemId: Int? = null,
)
