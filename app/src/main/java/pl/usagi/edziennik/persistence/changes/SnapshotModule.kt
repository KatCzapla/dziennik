package pl.usagi.edziennik.persistence.changes

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SnapshotModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): SnapshotDatabase =
        Room.databaseBuilder(context, SnapshotDatabase::class.java, "student_snapshots.db").build()

    @Provides @Singleton
    fun snapshotStore(database: SnapshotDatabase): SnapshotStore = RoomSnapshotStore(database)

    @Provides @Singleton
    fun identityStore(store: SharedPreferencesLocalIdentityStore): LocalIdentityStore = store

    @Provides
    fun synchronizer(sync: OneShotSnapshotSync): SnapshotSynchronizer = sync
}
