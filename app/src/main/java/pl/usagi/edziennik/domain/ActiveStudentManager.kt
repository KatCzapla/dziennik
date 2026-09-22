package pl.usagi.edziennik.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveStudentManager @Inject constructor() {
    private val _activeStudent = MutableStateFlow<Student?>(null)
    val activeStudent: StateFlow<Student?> = _activeStudent.asStateFlow()

    private val _students = MutableStateFlow<List<Student>>(emptyList())
    val students: StateFlow<List<Student>> = _students.asStateFlow()

    fun initialize(students: List<Student>) {
        _students.value = students
        _activeStudent.value = students.singleOrNull()
    }

    fun setActiveStudent(student: Student) {
        _activeStudent.value = student
    }

    fun clear() {
        _students.value = emptyList()
        _activeStudent.value = null
    }
}
