package com.example.opencvintegration.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.opencvintegration.entities.Language

@Dao
interface LanguageDAO {

    @Query("Select 1 from language_models")
    fun getOne(): Language

    @Insert
    fun insertOne(language_list: String): Boolean

    @Update
    fun updateOne(language_list: String): Language

    @Query("Select count(1) from language_models")
    fun count(): UByte
}