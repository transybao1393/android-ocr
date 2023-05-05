package com.example.opencvintegration.dao

import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.opencvintegration.entities.Language

@Dao
interface LanguageDAO {

//    @WorkerThread
    @Query("Select * from language_models limit 1")
    fun getSingleLanguage(): Language

//    @WorkerThread
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOne(singleLanguage: Language)

//    @WorkerThread
    @Update
    fun updateOne(singleLanguage: Language): Int

//    @WorkerThread
    @Query("SELECT * from language_models ORDER BY lid ASC") // <- Add a query to fetch all users (in user_table) in ascending order by their IDs.
    fun readAllData(): LiveData<List<Language>> // <- This means function return type is List. Specifically, a List of Users.

    @Query("Select count(lid) from language_models")
    fun countAllData(): LiveData<Int>
}