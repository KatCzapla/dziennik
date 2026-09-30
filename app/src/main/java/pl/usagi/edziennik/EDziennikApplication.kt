package pl.usagi.edziennik

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity
import javax.inject.Inject

@HiltAndroidApp
class EDziennikApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var activeStudentManager: ActiveStudentManager
    @Inject lateinit var localSnapshotIdentity: LocalSnapshotIdentity

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            activeStudentManager.activeStudent.collect { student ->
                if (student != null) runCatching {
                    if (localSnapshotIdentity.currentAccountId() != null) {
                        localSnapshotIdentity.selectStudent(student.diaryId)
                    }
                }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
