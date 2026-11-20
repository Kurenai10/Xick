package com.xick.app.model.kick

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Observed shape of GET kick.com/api/v1/categories/top items. */
@Serializable
data class TopCategory(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("slug") val slug: String,
    @SerialName("viewers") val viewers: Int = 0,
    @SerialName("banner") val banner: CategoryBanner? = null,
)

@Serializable
data class CategoryBanner(
    // categories/top uses `src`; subcategories uses `url` + `responsive`.
    @SerialName("src") val src: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("srcset") val srcset: String? = null,
    @SerialName("responsive") val responsive: String? = null,
) {
    val bestUrl: String? get() = src ?: url
}
