package com.xick.app.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.xick.app.model.kick.BrowseStream
import com.xick.app.repository.KickRepository

/** Paging3 source for a category's live-stream grid — key is the offset used by Kick's `after` cursor (see KickApi.livestreamsRequest). */
class CategoryStreamsDataSource(
    private val categoryId: Int?,
    private val repository: KickRepository = KickRepository(),
    private val pageSize: Int = 24,
    private val language: String? = null,
    private val sort: String? = null,
) : PagingSource<Int, BrowseStream>() {

    override fun getRefreshKey(state: PagingState<Int, BrowseStream>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BrowseStream> {
        val offset = params.key ?: 0
        return try {
            val streams = repository.getCategoryStreams(categoryId, offset, pageSize, language, sort)
            LoadResult.Page(
                data = streams,
                prevKey = if (offset == 0) null else (offset - pageSize).coerceAtLeast(0),
                nextKey = if (streams.size < pageSize) null else offset + pageSize,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
