package com.xick.app.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.xick.app.model.kick.TopCategory
import com.xick.app.repository.datasource.SubcategoriesDataSource
import kotlinx.coroutines.flow.Flow

/** "Oyunlar" tab: the full, paginated category list (infinite scroll). */
class GamesViewModel : ViewModel() {
    val categories: Flow<PagingData<TopCategory>> = Pager(
        config = PagingConfig(pageSize = 24, enablePlaceholders = false),
        pagingSourceFactory = { SubcategoriesDataSource() },
    ).flow.cachedIn(viewModelScope)
}
