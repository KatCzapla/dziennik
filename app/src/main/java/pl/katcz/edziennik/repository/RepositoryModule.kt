package pl.katcz.edziennik.repository

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    @Singleton
    fun bindStudentRepository(repository: UonetStudentRepository): StudentRepository

    @Binds
    @Singleton
    fun bindTimetableRepository(repository: UonetTimetableRepository): TimetableRepository
}
