package com.example.opencvintegration.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "language_models")
data class Language (
    @PrimaryKey val lid: Int,
    @ColumnInfo(name="language_list") val languageList: String?
)