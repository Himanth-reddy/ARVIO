package com.arflix.tv.network

import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okio.ForwardingSource
import okio.buffer
import java.util.concurrent.ConcurrentHashMap

/** Track streaming bodies, which may outlive their OkHttp dispatcher entries. */
internal class IptvPlaybackConnections : Interceptor {
    private val calls = ConcurrentHashMap.newKeySet<Call>()

    fun cancelAll() {
        calls.toList().forEach { it.cancel() }
    }

    fun cancelAllAsync(client: OkHttpClient, dispatcher: CoroutineDispatcher = Dispatchers.IO): Job {
        // Snapshot before dispatch: a quick resume must not cancel the new channel's calls.
        val closing = (calls.toList() + client.dispatcher.queuedCalls() + client.dispatcher.runningCalls()).distinct()
        return CoroutineScope(dispatcher).launch {
            // TLS socket cancellation may write close_notify, so it must not run on main.
            closing.forEach { call ->
                try {
                    call.cancel()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                }
            }
            try {
                client.connectionPool.evictAll()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        calls.add(call)
        try {
            val response = chain.proceed(chain.request())
            val body = response.body ?: run {
                calls.remove(call)
                return response
            }
            val source = object : ForwardingSource(body.source()) {
                override fun close() {
                    try { super.close() } finally { calls.remove(call) }
                }
            }.buffer()
            return response.newBuilder().body(object : ResponseBody() {
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun source() = source
            }).build()
        } catch (error: Throwable) {
            calls.remove(call)
            throw error
        }
    }
}
