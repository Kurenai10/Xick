package com.xick.app.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.xick.app.model.kick.TopCategory
import com.xick.app.repository.KickRepository

/** Paging3 source for the full category list (/api/v1/subcategories, 1-based pages). */
class SubcategoriesDataSource(
    private val repository: KickRepository = KickRepository(),
    private val pageSize: Int = 24,
) : PagingSource<Int, TopCategory>() {

    override fun getRefreshKey(state: PagingState<Int, TopCategory>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TopCategory> {
        val page = params.key ?: 1
        return try {
            val items = repository.getSubcategories(page, pageSize)
            LoadResult.Page(
                data = items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (items.size < pageSize) null else page + 1,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
