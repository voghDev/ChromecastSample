package es.voghdev.chromecastsample.persistence.relations

import androidx.room.Embedded
import androidx.room.Relation
import es.voghdev.chromecastsample.persistence.entities.CourseEntity
import es.voghdev.chromecastsample.persistence.entities.CourseLessonEntity
import es.voghdev.chromecastsample.persistence.entities.CourseUnitEntity

data class UnitWithLessons(
    @Embedded val unit: CourseUnitEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "unit_id",
    )
    val lessons: List<CourseLessonEntity>,
)

data class CourseWithContent(
    @Embedded val course: CourseEntity,
    @Relation(
        entity = CourseUnitEntity::class,
        parentColumn = "id",
        entityColumn = "course_id",
    )
    val units: List<UnitWithLessons>,
)
