package dev.sito8943.sitonotas.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Formulario de alta/edicion en un dialogo. `id` nulo = alta. */
data class CategoryForm(
    val id: Long? = null,
    val name: String = "",
    val description: String = "",
    val color: String? = null,
    val error: CategoryError? = null,
    val saving: Boolean = false,
)

data class CategoriesUiState(
    val loading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val uses: Map<Long, Int> = emptyMap(),
    val form: CategoryForm? = null,
    val confirmDelete: Category? = null,
)

class CategoriesViewModel(private val repository: CategoryRepository) : ViewModel() {

    private val form = MutableStateFlow<CategoryForm?>(null)
    private val confirmDelete = MutableStateFlow<Category?>(null)

    val state: StateFlow<CategoriesUiState> = combine(
        repository.observeCategories(),
        repository.observeUses(),
        form,
        confirmDelete,
    ) { categories, uses, currentForm, deleting ->
        CategoriesUiState(
            loading = false,
            categories = categories,
            uses = uses,
            form = currentForm,
            confirmDelete = deleting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun startCreate() {
        viewModelScope.launch { form.value = CategoryForm(color = repository.suggestColor()) }
    }

    fun startEdit(category: Category) {
        form.value = CategoryForm(
            id = category.id,
            name = category.name,
            description = category.description.orEmpty(),
            color = category.color,
        )
    }

    fun onNameChange(value: String) = form.update { it?.copy(name = value, error = null) }

    fun onDescriptionChange(value: String) = form.update { it?.copy(description = value) }

    fun onColorChange(value: String) = form.update { it?.copy(color = value) }

    fun dismissForm() {
        form.value = null
    }

    fun save() {
        val current = form.value ?: return
        form.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                if (current.id == null) {
                    repository.create(current.name, current.description, current.color)
                } else {
                    repository.update(current.id, current.name, current.description, current.color)
                }
                form.value = null
            } catch (error: CategoryException) {
                form.value = current.copy(saving = false, error = error.error)
            }
        }
    }

    fun askDelete(category: Category) {
        confirmDelete.value = category
    }

    fun dismissDelete() {
        confirmDelete.value = null
    }

    fun confirmDelete() {
        val category = confirmDelete.value ?: return
        confirmDelete.value = null
        viewModelScope.launch { repository.delete(category.id) }
    }
}
