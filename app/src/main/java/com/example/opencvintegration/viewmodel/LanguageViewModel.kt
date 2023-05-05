package com.example.opencvintegration.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.opencvintegration.database.AppDatabase
import com.example.opencvintegration.entities.Language
import com.example.opencvintegration.repository.LanguageRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LanguageViewModel(application: Application): AndroidViewModel(application) {
    val readAllData: LiveData<List<Language>>
    val countAllData: LiveData<Int>
    private val repository: LanguageRepository

    init {
        val userDao = AppDatabase.getDatabase(application).languageDao()
        repository= LanguageRepository(userDao)
        readAllData = repository.readAllData
        countAllData = repository.countAllData
    }

    fun addLanguageList(language: Language) = viewModelScope.launch {
            repository.addLanguageList(language)
    }

    fun updateLanguageList(language: Language) = viewModelScope.launch {
            repository.updateLanguageList(language)
    }

    fun getSingleLanguage(): Language {
        return repository.getSingleLanguage()
    }
}