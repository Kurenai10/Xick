package com.xick.app.model.kick

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Observed: GET search.kick.com/api/v1/search?query=<q> -> {"data":{...}}. */
@Serializable
data class SearchResponse(
    @SerialName("data") val data: SearchData = SearchData(),
)

@Serializable
data class SearchData(
    @SerialName("channels") val channels: List<SearchChannel> = emptyList(),
    @SerialName("categories") val categories: List<SearchCategory> = emptyList(),
)

@Serializable
data class SearchChannel(
    @SerialName("slug") val slug: String,
    @SerialName("username") val username: String,
    @SerialName("profile_picture") val profilePicture: String = "",
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("is_verified") val isVerified: Boolean = false,
)

@Serializable
data class SearchCategory(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("slug") val slug: String = "",
)
