package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object NetworkAndCacheManager {
    private const val DISK_CACHE_DIR = "promptlens_image_cache"
    private const val MAX_DISK_CACHE_BYTES = 250L * 1024L * 1024L // 250 MB LRU

    @Volatile
    private var configuredImageLoader: ImageLoader? = null

    fun getImageLoader(context: Context): ImageLoader {
        configuredImageLoader?.let { return it }
        synchronized(this) {
            configuredImageLoader?.let { return it }
            val appContext = context.applicationContext
            val loader = ImageLoader.Builder(appContext)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCache {
                    MemoryCache.Builder(appContext)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(appContext.cacheDir.resolve(DISK_CACHE_DIR))
                        .maxSizeBytes(MAX_DISK_CACHE_BYTES)
                        .build()
                }
                .crossfade(150)
                .build()
            Coil.setImageLoader(loader)
            configuredImageLoader = loader
            return loader
        }
    }

    fun isOnlineNow(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val active = cm.activeNetwork
        if (active != null) {
            val caps = cm.getNetworkCapabilities(active)
            if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return true
            }
        }
        return runCatching {
            @Suppress("DEPRECATION")
            cm.allNetworks.any { network ->
                cm.getNetworkCapabilities(network)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }
        }.getOrDefault(false)
    }

    fun isUnmeteredWifiNow(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun observeConnectivity(context: Context): Flow<Boolean> = callbackFlow {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }

        trySend(isOnlineNow(appContext))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isOnlineNow(appContext))
            }

            override fun onLost(network: Network) {
                trySend(isOnlineNow(appContext))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(isOnlineNow(appContext))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        runCatching { cm.registerNetworkCallback(request, callback) }
        awaitClose {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoilApi::class)
    fun evictImageUrls(context: Context, urls: Collection<String>) {
        if (urls.isEmpty()) return
        val loader = getImageLoader(context)
        val diskCache = loader.diskCache
        val memoryCache = loader.memoryCache
        urls.forEach { url ->
            if (url.isNotBlank()) {
                runCatching { diskCache?.remove(url) }
                runCatching { memoryCache?.remove(MemoryCache.Key(url)) }
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearAllImageCache(context: Context) = withContext(Dispatchers.IO) {
        val loader = getImageLoader(context)
        runCatching { loader.memoryCache?.clear() }
        runCatching { loader.diskCache?.clear() }
    }

    /**
     * Prefetches the priority batch (viewed + next ~20 images) whenever online,
     * and prefetches remaining catalog images in the background ONLY on unmetered Wi-Fi.
     */
    fun prefetchCatalogImages(
        context: Context,
        scope: CoroutineScope,
        priorityUrls: List<String>,
        remainingUrls: List<String>
    ) {
        if (!isOnlineNow(context)) return
        val appContext = context.applicationContext
        val loader = getImageLoader(appContext)

        scope.launch(Dispatchers.IO) {
            priorityUrls.asSequence()
                .filter { it.isNotBlank() }
                .distinct()
                .take(22)
                .forEach { url ->
                    val request = ImageRequest.Builder(appContext)
                        .data(url)
                        .size(540, 720)
                        .precision(Precision.INEXACT)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                    loader.enqueue(request)
                }

            if (isUnmeteredWifiNow(appContext)) {
                remainingUrls.asSequence()
                    .filter { it.isNotBlank() }
                    .distinct()
                    .forEach { url ->
                        if (!isUnmeteredWifiNow(appContext)) return@launch
                        val request = ImageRequest.Builder(appContext)
                            .data(url)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                        loader.enqueue(request)
                    }
            }
        }
    }
}
