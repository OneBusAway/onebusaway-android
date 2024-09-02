package org.onebusaway.android.database

import androidx.room.Database
import androidx.room.RoomDatabase
import org.onebusaway.android.ui.survey.dao.StudiesDao
import org.onebusaway.android.ui.survey.dao.SurveysDao
import org.onebusaway.android.ui.survey.entity.Study
import org.onebusaway.android.ui.survey.entity.Survey

/**
 * Main database class for the app, containing `Study` and `Survey` entities.
 * Provides abstract methods for accessing `StudiesDao` and `SurveysDao`.
 * The `@Database` annotation sets up Room with version 1 of the schema.
 */
@Database(entities = [Study::class, Survey::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studiesDao(): StudiesDao
    abstract fun surveysDao(): SurveysDao
}