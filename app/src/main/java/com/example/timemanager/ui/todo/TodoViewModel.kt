package com.example.timemanager.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timemanager.data.repository.TodoRepository
import com.example.timemanager.domain.todo.validatedTodoTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TodoViewModel(private val repository: TodoRepository) : ViewModel() {
    val dayState = repository.dayState
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    fun add(title: String, onSaved: () -> Unit) {
        if (_saving.value) return
        val validated = try { validatedTodoTitle(title) }
        catch (error: IllegalArgumentException) { _message.value = error.message; return }
        _saving.value = true
        viewModelScope.launch {
            try { repository.create(validated); _message.value = null; onSaved() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { _message.value = "未能保存待办，请重试" }
            finally { _saving.value = false }
        }
    }
    fun setCompleted(id: Long, completed: Boolean) = mutate {
        if (completed) repository.complete(id) else repository.uncomplete(id)
    }
    fun delete(id: Long) = mutate { repository.delete(id) }
    private fun mutate(operation: suspend () -> Unit) {
        viewModelScope.launch {
            try { operation(); _message.value = null }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { _message.value = "未能更新待办，请重试" }
        }
    }
    class Factory(private val repository: TodoRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TodoViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return TodoViewModel(repository) as T
        }
    }
}
