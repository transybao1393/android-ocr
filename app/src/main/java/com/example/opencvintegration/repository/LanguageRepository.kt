package com.example.opencvintegration.repository

import androidx.lifecycle.LiveData
import com.example.opencvintegration.dao.LanguageDAO
import com.example.opencvintegration.entities.Language

class LanguageRepository (private val languageDAO: LanguageDAO) {
    val readAllData: LiveData<List<Language>> = languageDAO.readAllData()
    val countAllData: LiveData<Int> = languageDAO.countAllData()
    suspend fun addLanguageList(language: Language) {
        languageDAO.insertOne(language)
    }

    suspend fun updateLanguageList(language: Language) {
        languageDAO.updateOne(language)
    }

    fun getSingleLanguage(): Language {
        return languageDAO.getSingleLanguage()
    }
}