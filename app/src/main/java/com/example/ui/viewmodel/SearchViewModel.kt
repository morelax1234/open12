package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.models.VideoItem
import com.example.data.repository.YouTubeRepository
import com.example.ui.screens.SearchUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: YouTubeRepository = YouTubeRepository()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var lastSearchedQuery = ""

    fun updateQuery(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun search(query: String, isArabic: Boolean) {
        if (query.isBlank()) return
        lastSearchedQuery = query
        _uiState.value = SearchUiState.Loading

        viewModelScope.launch {
            val result = repository.search(query, isArabic)
            result.fold(
                onSuccess = { items ->
                    _uiState.value = SearchUiState.Success(query, items)
                },
                onFailure = { error ->
                    _uiState.value = SearchUiState.Error(
                        error.localizedMessage ?: "Network or parsing error"
                    )
                }
            )
        }
    }

    fun retry(isArabic: Boolean) {
        if (lastSearchedQuery.isNotBlank()) {
            search(lastSearchedQuery, isArabic)
        }
    }
}
