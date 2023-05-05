package com.example.opencvintegration.entities

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "language_models")
data class Language (
    @ColumnInfo(name="language_list") val languageList: String?
)