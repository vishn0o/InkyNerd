package com.wikiink.app.testing

import java.io.IOException
import java.lang.reflect.Proxy

/** A Retrofit service stand-in where every call fails as if the phone were offline. */
inline fun <reified T : Any> offlineApi(): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        throw IOException("offline in test: ${method.name}")
    } as T
