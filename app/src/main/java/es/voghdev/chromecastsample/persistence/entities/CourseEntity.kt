package es.voghdev.chromecastsample.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "courses",
    indices = [Index("main_category")],
)
data class CourseEntity(
    @PrimaryKey val id: Int,
    @Embedded(prefix = "title_") val title: TitleEmbedded? = null,
    @ColumnInfo(name = "aasm_state") val aasmState: String? = null,
    @ColumnInfo(name = "main_category") val mainCategory: Int = 0,
    @ColumnInfo(name = "teacher_id") val teacherId: String? = null,
    @ColumnInfo(name = "cover_id") val coverId: Int? = null,
    @ColumnInfo(name = "color_place_holder") val colorPlaceHolder: String? = null,
    @ColumnInfo(name = "directory_hidden") val directoryHidden: Boolean? = null,
    @ColumnInfo(name = "non_purchaseable") val nonPurchaseable: Boolean? = null,
    @ColumnInfo(name = "is_specialization") val isSpecialization: Boolean? = null,
    @ColumnInfo(name = "is_guided_project") val isGuidedProject: Boolean? = null,
    @ColumnInfo(name = "is_deep_dive") val isDeepDive: Boolean? = null,
)

data class TitleEmbedded(
    val es: String? = null,
    val en: String? = null,
    val pt: String? = null,
    val de: String? = null,
    val fr: String? = null,
    val it: String? = null,
    val pl: String? = null,
    val nl: String? = null,
)
