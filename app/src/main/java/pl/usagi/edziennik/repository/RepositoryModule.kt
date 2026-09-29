package pl.usagi.edziennik.repository

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.usagi.edziennik.network.schooltasks.SchoolTasksRemoteDataSource
import pl.usagi.edziennik.network.schooltasks.UonetSchoolTasksDataSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    @Singleton
    fun bindDashboardRepository(repository: UonetDashboardRepository): DashboardRepository

    @Binds
    @Singleton
    fun bindStudentRepository(repository: UonetStudentRepository): StudentRepository

    @Binds
    @Singleton
    fun bindTimetableRepository(repository: UonetTimetableRepository): TimetableRepository

    @Binds
    @Singleton
    fun bindAttendanceRepository(repository: UonetAttendanceRepository): AttendanceRepository

    @Binds
    @Singleton
    fun bindGradesRepository(repository: UonetGradesRepository): GradesRepository

    @Binds
    @Singleton
    fun bindSchoolTasksRepository(repository: UonetSchoolTasksRepository): SchoolTasksRepository

    @Binds
    @Singleton
    fun bindMessagesRepository(repository: UonetMessagesRepository): MessagesRepository

    @Binds
    @Singleton
    fun bindSchoolTasksRemoteDataSource(dataSource: UonetSchoolTasksDataSource): SchoolTasksRemoteDataSource
}
