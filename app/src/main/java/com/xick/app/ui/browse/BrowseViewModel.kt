package com.xick.app.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.xick.app.model.kick.BrowseStream
import com.xick.app.repository.KickRepository
import com.xick.app.repository.datasource.CategoryStreamsDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Popüler/category feed. Filters (language / sort) re-create the Paging
 * source so the server does the filtering (Kick's own browse filters,
 * which the user specifically wanted to reuse).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BrowseViewModel(
    private val categoryId: Int?,
    private val repository: KickRepository = KickRepository(),
) : ViewModel() {

    data class Filter(val language: String? = null, val sort: String? = null)

    private val filter = MutableStateFlow(Filter())
    val currentFilter: Filter get() = filter.value

    val streams: Flow<PagingData<BrowseStream>> = filter.flatMapLatest { f ->
        Pager(
            config = PagingConfig(pageSize = 24, enablePlaceholders = false),
            pagingSourceFactory = {
                CategoryStreamsDataSource(categoryId, repository, language = f.language, sort = f.sort)
            },
        ).flow
    }.cachedIn(viewModelScope)

    fun setFilter(language: String?, sort: String?) {
        filter.value = Filter(language, sort)
    }
}
