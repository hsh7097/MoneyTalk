package com.sanha.moneytalk.feature.categorysettings.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.CustomCategoryRepository
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryInfo
import com.sanha.moneytalk.core.model.CategoryProvider
import com.sanha.moneytalk.core.model.CategoryType
import com.sanha.moneytalk.core.model.CustomCategoryInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
data class CategorySettingsUiState(
    val selectedTab: CategoryType = CategoryType.EXPENSE,
    val defaultCategories: List<CategoryInfo> = emptyList(),
    val customCategories: List<CustomCategoryInfo> = emptyList(),
    val showAddDialog: Boolean = false,
    val addEmoji: String = "\uD83D\uDCE6",
    val addName: String = "",
    @StringRes val addErrorResId: Int? = null,
    val showDeleteConfirm: Long? = null
)

@HiltViewModel
class CategorySettingsViewModel @Inject constructor(
    private val customCategoryRepository: CustomCategoryRepository,
    private val categoryProvider: CategoryProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategorySettingsUiState())
    val uiState: StateFlow<CategorySettingsUiState> = _uiState.asStateFlow()
    private var categoryLoadJob: Job? = null

    init {
        loadCategories()
    }

    fun selectTab(type: CategoryType) {
        _uiState.update { it.copy(selectedTab = type) }
        loadCategories()
    }

    fun showAddDialog() {
        _uiState.update {
            it.copy(
                showAddDialog = true,
                addEmoji = "\uD83D\uDCE6",
                addName = "",
                addErrorResId = null
            )
        }
    }

    fun dismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun updateAddEmoji(emoji: String) {
        _uiState.update { it.copy(addEmoji = emoji) }
    }

    fun updateAddName(name: String) {
        _uiState.update { it.copy(addName = name, addErrorResId = null) }
    }

    fun addCategory() {
        val state = _uiState.value
        val name = state.addName.trim()

        if (name.isBlank()) {
            _uiState.update { it.copy(addErrorResId = R.string.category_settings_error_name_required) }
            return
        }

        viewModelScope.launch {
            val isDuplicate = customCategoryRepository.isDuplicate(name, state.selectedTab)
            if (isDuplicate) {
                _uiState.update { it.copy(addErrorResId = R.string.category_settings_error_duplicate) }
                return@launch
            }

            customCategoryRepository.add(name, state.addEmoji, state.selectedTab)
            categoryProvider.invalidateCache()
            _uiState.update { it.copy(showAddDialog = false) }
            loadCategories()
        }
    }

    fun showDeleteConfirm(id: Long) {
        _uiState.update { it.copy(showDeleteConfirm = id) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = null) }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            customCategoryRepository.delete(id)
            categoryProvider.invalidateCache()
            _uiState.update { it.copy(showDeleteConfirm = null) }
            loadCategories()
        }
    }

    private fun loadCategories() {
        val type = _uiState.value.selectedTab
        categoryLoadJob?.cancel()
        categoryLoadJob = viewModelScope.launch {
            val defaults: List<CategoryInfo> = when (type) {
                CategoryType.EXPENSE -> Category.expenseEntries
                CategoryType.INCOME -> Category.incomeEntries
                CategoryType.TRANSFER -> Category.transferEntries
            }
            val customs = customCategoryRepository.getByType(type).map { entity ->
                val entityType = try {
                    CategoryType.valueOf(entity.categoryType)
                } catch (e: IllegalArgumentException) {
                    CategoryType.EXPENSE
                }
                CustomCategoryInfo(
                    id = entity.id,
                    displayName = entity.displayName,
                    emoji = entity.emoji,
                    categoryType = entityType,
                    displayOrder = entity.displayOrder
                )
            }
            _uiState.update {
                // 이전 탭 조회가 늦게 끝나도 현재 탭의 목록을 덮지 않는다.
                if (it.selectedTab == type) {
                    it.copy(defaultCategories = defaults, customCategories = customs)
                } else {
                    it
                }
            }
        }
    }
}
