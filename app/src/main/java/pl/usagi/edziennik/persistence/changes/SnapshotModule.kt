package pl.usagi.edziennik.persistence.changes

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import pl.usagi.edziennik.widget.GlanceTimetableWidgetUpdater
import pl.usagi.edziennik.widget.TimetableWidgetUpdater
import pl.usagi.edziennik.widget.GlanceTasksWidgetUpdater
import pl.usagi.edziennik.widget.TasksWidgetUpdater
import pl.usagi.edziennik.widget.LocalWidgetsUpdater
import pl.usagi.edziennik.widget.WidgetUpdater

@Module
@InstallIn(SingletonComponent::class)
object SnapshotModule {
    @Provides
    fun widgetUpdater(impl: GlanceTimetableWidgetUpdater): TimetableWidgetUpdater = impl
    @Provides
    fun tasksWidgetUpdater(impl: GlanceTasksWidgetUpdater): TasksWidgetUpdater = impl
    @Provides
    fun localWidgetsUpdater(impl: LocalWidgetsUpdater): WidgetUpdater = impl
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): SnapshotDatabase =
        Room.databaseBuilder(context, SnapshotDatabase::class.java, "student_snapshots.db")
            .addMigrations(SNAPSHOT_MIGRATION_1_2, SNAPSHOT_MIGRATION_2_3).build()

    @Provides @Singleton
    fun snapshotStore(database: SnapshotDatabase, json: Json): SnapshotStore = RoomSnapshotStore(database, json)

    @Provides @Singleton
    fun identityStore(store: SharedPreferencesLocalIdentityStore): LocalIdentityStore = store

    @Provides
    fun synchronizer(sync: OneShotSnapshotSync): SnapshotSynchronizer = sync
}
