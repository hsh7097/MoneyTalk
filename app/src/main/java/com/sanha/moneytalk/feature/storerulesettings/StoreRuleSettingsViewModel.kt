package com.sanha.moneytalk.feature.storerulesettings

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import com.sanha.moneytalk.feature.home.data.StoreRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
data class StoreRuleSettingsUiState(
    val rules: List<StoreRuleEntity> = emptyList(),
    val showAddDialog: Boolean = false,
    val editingRule: StoreRuleEntity? = null,
    val addKeyword: String = "",
    val addCategory: String? = null,
    val addIsFixed: Boolean = false,
    @StringRes val addErrorResId: Int? = null,
    val showDeleteConfirm: Long? = null,
    val showCategorySelect: Boolean = false
)

@HiltViewModel
class StoreRuleSettingsViewModel @Inject constructor(
    private val storeRuleRepository: StoreRuleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoreRuleSettingsUiState())
    val uiState: StateFlow<StoreRuleSettingsUiState> = _uiState.asStateFlow()

    init {
        loadRules()
    }

    private fun loadRules() {
        viewModelScope.launch {
            storeRuleRepository.getAll().collect { rules ->
                _uiState.update { it.copy(rules = rules) }
            }
        }
    }

    fun showAddDialog() {
        _uiState.update {
            it.copy(
                showAddDialog = true,
                editingRule = null,
                addKeyword = "",
                addCategory = null,
                addIsFixed = false,
                addErrorResId = null
            )
        }
    }

    fun showEditDialog(rule: StoreRuleEntity) {
        _uiState.update {
            it.copy(
                showAddDialog = true,
                editingRule = rule,
                addKeyword = rule.keyword,
                addCategory = rule.category,
                addIsFixed = rule.isFixed ?: false,
                addErrorResId = null
            )
        }
    }

    fun dismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingRule = null) }
    }

    fun updateKeyword(keyword: String) {
        _uiState.update { it.copy(addKeyword = keyword, addErrorResId = null) }
    }

    fun updateCategory(category: String?) {
        _uiState.update { it.copy(addCategory = category, showCategorySelect = false) }
    }

    fun updateIsFixed(isFixed: Boolean) {
        _uiState.update { it.copy(addIsFixed = isFixed) }
    }

    fun showCategorySelect() {
        _uiState.update { it.copy(showCategorySelect = true) }
    }

    fun dismissCategorySelect() {
        _uiState.update { it.copy(showCategorySelect = false) }
    }

    fun saveRule() {
        val state = _uiState.value
        val keyword = state.addKeyword.trim()

        if (keyword.isBlank()) {
            _uiState.update { it.copy(addErrorResId = R.string.store_rule_error_keyword_required) }
            return
        }

        if (state.addCategory == null && !state.addIsFixed) {
            _uiState.update { it.copy(addErrorResId = R.string.store_rule_error_no_rule) }
            return
        }

        viewModelScope.launch {
            val rule = StoreRuleEntity(
                id = state.editingRule?.id ?: 0,
                keyword = keyword,
                category = state.addCategory,
                isFixed = if (state.addIsFixed) true else null
            )
            storeRuleRepository.upsert(rule)
            _uiState.update { it.copy(showAddDialog = false, editingRule = null) }
        }
    }

    fun showDeleteConfirm(id: Long) {
        _uiState.update { it.copy(showDeleteConfirm = id) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = null) }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            storeRuleRepository.deleteById(id)
            _uiState.update { it.copy(showDeleteConfirm = null) }
        }
    }
}
