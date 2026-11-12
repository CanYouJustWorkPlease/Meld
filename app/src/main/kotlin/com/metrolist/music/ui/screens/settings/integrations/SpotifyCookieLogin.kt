/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings.integrations

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.metrolist.music.constants.SpotifyAccessTokenKey
import com.metrolist.music.constants.SpotifyLoginMethodKey
import com.metrolist.music.constants.SpotifySpDcKey
import com.metrolist.music.constants.SpotifySpKeyKey
import com.metrolist.music.constants.SpotifyTokenExpiryKey
import com.metrolist.music.constants.SpotifyUserIdKey
import com.metrolist.music.constants.SpotifyUsernameKey
import com.metrolist.music.utils.dataStore
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Logs in to Spotify with an sp_dc cookie pasted by the user. Accepts the bare value or the full
 * "sp_dc=VALUE" form. Returns the account's display name on success; nothing is saved on failure.
 */
suspend fun loginWithSpDcCookie(
    context: Context,
    rawCookie: String,
): Result<String> =
    withContext(Dispatchers.IO) {
        runCatching {
            val spDc =
                rawCookie
                    .trim()
                    .removePrefix("sp_dc=")
                    .trim()
                    .trimEnd(';')
                    .trim()
            require(spDc.isNotBlank()) { "The cookie is empty" }

            val token = SpotifyAuth.fetchAccessToken(spDc, "").getOrThrow()
            Spotify.accessToken = token.accessToken

            var name = ""
            Spotify.me().onSuccess { user ->
                name = user.displayName ?: user.id
                context.dataStore.edit { prefs ->
                    prefs[SpotifyUsernameKey] = name
                    prefs[SpotifyUserIdKey] = user.id
                }
            }

            context.dataStore.edit { prefs ->
                prefs[SpotifySpDcKey] = spDc
                prefs[SpotifySpKeyKey] = ""
                prefs[SpotifyAccessTokenKey] = token.accessToken
                prefs[SpotifyTokenExpiryKey] = token.accessTokenExpirationTimestampMs
                prefs[SpotifyLoginMethodKey] = "cookie"
            }
            name
        }
    }
