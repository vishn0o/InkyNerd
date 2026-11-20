package com.wikiink.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/** Wikimedia API etiquette requires an identifying User-Agent on every request. */
class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Api-User-Agent", USER_AGENT)
                .build(),
        )

    companion object {
        val USER_AGENT = "InkyNerdAndroid/${com.wikiink.app.BuildConfig.VERSION_NAME} (contact@inkynerd.local)"
    }
}
