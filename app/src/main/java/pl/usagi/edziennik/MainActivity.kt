package pl.usagi.edziennik

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import pl.usagi.edziennik.ui.EDziennikApp
import pl.usagi.edziennik.domain.ActiveStudentManager
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var activeStudentManager: ActiveStudentManager
    private val notificationRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_EDziennik)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationRoute.value = intent?.getStringExtra(EXTRA_NOTIFICATION_ROUTE)?.takeIf(::isAllowedRoute)
        setContent {
            val student = activeStudentManager.activeStudent.collectAsState().value
            EDziennikApp(notificationRoute.value, student != null) { notificationRoute.value = null }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationRoute.value = intent.getStringExtra(EXTRA_NOTIFICATION_ROUTE)?.takeIf(::isAllowedRoute)
    }

    companion object {
        const val EXTRA_NOTIFICATION_ROUTE = "notification_route"

        fun isAllowedRoute(route: String): Boolean = route in setOf(
            "home", "messages", "grades", "timetable", "attendance", "payments", "schoolTasks",
        ) || Regex("schoolTasks\\?taskId=\\d+&taskType=-?\\d+&taskDate=\\d{4}-\\d{2}-\\d{2}").matches(route)
    }
}
