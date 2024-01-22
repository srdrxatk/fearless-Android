package jp.co.soramitsu.runtime.multiNetwork.runtime

import android.util.Log
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.core.runtime.ConstructedRuntime
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.toSyncIssue
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class RuntimeProvider(
    private val runtimeFactory: RuntimeFactory,
    private val runtimeSyncService: RuntimeSyncService,
    private val runtimeFilesCache: RuntimeFilesCache,
    private val chainDao: ChainDao,
    private val networkStateMixin: NetworkStateMixin,
    private val chain: Chain
) : CoroutineScope by CoroutineScope(Dispatchers.Default) {

    private val chainId = chain.id

    private val runtimeFlow = MutableSharedFlow<ConstructedRuntime>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var currentConstructionJob: Job? = null

    suspend fun get(): RuntimeSnapshot {
        val runtime = runtimeFlow.first()

        return runtime.runtime
    }

    suspend fun getOrNull(): RuntimeSnapshot? {
        return if (runtimeFlow.replayCache.isEmpty()) {
            null
        } else {
            runtimeFlow.first().runtime
        }
    }

    suspend fun getOrNullWithTimeout(shouldWait: Boolean = true): RuntimeSnapshot? {
        return if (runtimeFlow.replayCache.isEmpty()) {
            if (shouldWait) {
                delay(3000L)
                getOrNullWithTimeout(false)
            } else {
                null
            }
        } else {
            runtimeFlow.first().runtime
        }
    }

    fun observe(): Flow<RuntimeSnapshot> = runtimeFlow.map { it.runtime }

    suspend fun observeWithTimeout(
        timeoutMillis: Long
    ): Flow<Result<RuntimeSnapshot>> = withTimeoutOrNull(timeoutMillis) {
        runtimeFlow.map { Result.success(it.runtime) }
    } ?: flowOf(Result.failure(Throwable("Timeout")))

    init {
        runtimeSyncService.syncResultFlow(chainId)
            .onEach(::considerReconstructingRuntime)
            .launchIn(this)

        tryLoadFromCache()
    }

    fun finish() {
        invalidateRuntime()

        cancel()
    }

    private fun tryLoadFromCache() {
        constructNewRuntime()
    }

    private fun considerReconstructingRuntime(runtimeSyncResult: SyncResult) {
        launch {
            currentConstructionJob?.join()

            val currentVersion = runtimeFlow.replayCache.firstOrNull()

            if (
                currentVersion == null ||
                // metadata was synced and new hash is different from current one
                (runtimeSyncResult.metadataHash != null && currentVersion.metadataHash != runtimeSyncResult.metadataHash) ||
                // types were synced and new hash is different from current one
                (runtimeSyncResult.typesHash != null && currentVersion.ownTypesHash != runtimeSyncResult.typesHash)
            ) {
                constructNewRuntime()
            }
        }
    }

    private fun constructNewRuntime() {
        currentConstructionJob?.cancel()

        currentConstructionJob = launch {
            invalidateRuntime()

            runCatching {
                val runtimeVersion = chainDao.runtimeInfo(chainId)?.syncedVersion ?: return@launch
                val metadataRaw = runCatching { runtimeFilesCache.getChainMetadata(chainId) }
                    .getOrElse { throw ChainInfoNotInCacheException }
                val ownTypesRaw =
                    runCatching { chainDao.getTypes(chainId) ?: throw ChainInfoNotInCacheException }
                        .getOrElse { throw ChainInfoNotInCacheException }

                val runtime =
                    runtimeFactory.constructRuntime(metadataRaw, ownTypesRaw, runtimeVersion)
                if(chainId == "7834781d38e4798d548e34ec947d19deea29df148a7bf32484b7b24dacf8d4b7"){
                    Log.d("&&&", "reef runtime construction: ${runtime.runtime}")
                }
                runtimeFlow.emit(runtime)
                networkStateMixin.notifyChainSyncSuccess(chainId)
            }.onFailure {
                if(chainId == "7834781d38e4798d548e34ec947d19deea29df148a7bf32484b7b24dacf8d4b7"){
                    Log.d("&&&", "reef runtime construction failure: $it ${it.message}")
                }
                networkStateMixin.notifyChainSyncProblem(chain.toSyncIssue())
                when (it) {
                    ChainInfoNotInCacheException -> runtimeSyncService.cacheNotFound(chainId)
                    else -> it.printStackTrace()
                }
            }

            currentConstructionJob = null
        }
    }

    private fun invalidateRuntime() {
        runtimeFlow.resetReplayCache()
    }
}
