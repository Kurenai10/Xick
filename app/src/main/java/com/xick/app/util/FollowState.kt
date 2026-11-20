package com.xick.app.util

import android.content.Context

/**
 * Which channels the signed-in account follows, as last known. The Takip list
 * refreshes it from the followed page; the player's heart button and the follow
 * actions keep it up to date.
 */
object FollowState {

    private const val PREFS = "xick_follow_state"
    private const val KEY = "slugs"

    fun isFollowed(context: Context, slug: String): Boolean = load(context).contains(slug.lowercase())

    /** Forgets every followed channel (used on sign-out, so the next account starts clean). */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    /** Every channel the account follows, as the app knows it. */
    fun all(context: Context): Set<String> = load(context)

    fun set(context: Context, slug: String, followed: Boolean) {
        val all = load(context).toMutableSet()
        val s = slug.lowercase()
        if (followed) all.add(s) else all.remove(s)
        save(context, all)
    }

    fun replaceAll(context: Context, slugs: Collection<String>) {
        save(context, slugs.map { it.lowercase() }.toSet())
    }

    private fun load(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()) ?: emptySet()

    private fun save(context: Context, slugs: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY, HashSet(slugs)).apply()
    }
}
