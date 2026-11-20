package com.xick.app.util

import android.content.Context
import java.util.UUID

/**
 * The guest/anon id Kick's chat auth needs (`client_id` in the body of
 * realtime/auth/connection, and the `sub` of the JWT it returns). The web
 * client stores a random UUID in the `kick:anon-id:v1` cookie; this is the
 * app-side equivalent, persisted so chat identity is stable across runs.
 */
object AnonId {
    private const val PREFS = "xick_anon"
    private const val KEY = "anon_id"

    @Volatile
    private var context: Context? = null

    @Volatile
    private var cached: String? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    fun get(): String {
        cached?.let { return it }
        val prefs = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs?.getString(KEY, null)
        val id = existing ?: UUID.randomUUID().toString().also { prefs?.edit()?.putString(KEY, it)?.apply() }
        cached = id
        return id
    }
}
