package com.example.opencvintegration.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.opencvintegration.entities.Language
import com.example.opencvintegration.dao.LanguageDAO

@Database(entities = [Language::class], version = 1)
abstract class AppDatabase: RoomDatabase() {
    abstract fun languageDao(): LanguageDAO
}