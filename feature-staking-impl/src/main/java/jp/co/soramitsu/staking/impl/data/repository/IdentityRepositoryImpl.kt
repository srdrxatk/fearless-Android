package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.mapValuesNotNull
import jp.co.soramitsu.common.utils.toHexAccountId
import jp.co.soramitsu.runtime.ext.accountFromMapKey
import jp.co.soramitsu.runtime.ext.hexAccountIdOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.getSocket
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.definitions.types.Type
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import jp.co.soramitsu.staking.api.domain.api.AccountAddressMap
import jp.co.soramitsu.staking.api.domain.api.AccountIdMap
import jp.co.soramitsu.staking.api.domain.api.IdentityRepository
import jp.co.soramitsu.staking.api.domain.model.ChildIdentity
import jp.co.soramitsu.staking.api.domain.model.Identity
import jp.co.soramitsu.staking.api.domain.model.SuperOf
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindIdentity
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindSuperOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IdentityRepositoryImpl(
    private val bulkRetriever: BulkRetriever,
    private val chainRegistry: ChainRegistry
) : IdentityRepository {

    override suspend fun getIdentitiesFromIds(
        chain: Chain,
        accountIdsHex: List<String>
    ): AccountIdMap<Identity?> = withContext(Dispatchers.Default) {
        val socketService = chainRegistry.getSocket(chain.id)
        val runtime = chainRegistry.getRuntime(chain.id)

        val identityModule = runtime.metadata.module("Identity")

        val identityOfStorage = identityModule.storage("IdentityOf")
        val identityOfReturnType = identityOfStorage.type.value!!

        val superOfStorage = identityModule.storage("SuperOf")
        val superOfReturnType = superOfStorage.type.value!!
        val superOfKeys = superOfStorage.accountMapStorageKeys(runtime, accountIdsHex)

        val superOfValues = bulkRetriever.queryKeys(socketService, superOfKeys)
            .mapKeys { (fullKey, _) -> chain.accountFromMapKey(fullKey) }
            .mapValuesNotNull { (_, value) ->
                value?.let { bindSuperOf(it, runtime, superOfReturnType) }
            }

        val parentIdentityIds = superOfValues.values.map(SuperOf::parentIdHex).distinct()
        val parentIdentityKeys = identityOfStorage.accountMapStorageKeys(runtime, parentIdentityIds)

        val parentIdentities = fetchIdentities(socketService, parentIdentityKeys, runtime, identityOfReturnType, chain)

        val childIdentities = superOfValues.mapValues { (_, superOf) ->
            val parentIdentity = parentIdentities[superOf.parentIdHex]

            parentIdentity?.let { ChildIdentity(superOf.childName, it) }
        }

        val leftAccountIds = accountIdsHex.toSet() - childIdentities.keys - parentIdentities.keys
        val leftIdentityKeys = identityOfStorage.accountMapStorageKeys(runtime, leftAccountIds.toList())

        val rootIdentities = fetchIdentities(socketService, leftIdentityKeys, runtime, identityOfReturnType, chain)

        rootIdentities + childIdentities + parentIdentities
    }

    override suspend fun getIdentitiesFromIdsBytes(chain: Chain, accountIdsBytes: List<ByteArray>): AccountIdMap<Identity?> {
        return getIdentitiesFromIds(chain, accountIdsBytes.map { it.toHexString() })
    }

    override suspend fun getIdentitiesFromAddresses(chain: Chain, accountAddresses: List<String>): AccountAddressMap<Identity?> {
        val accountIds = accountAddresses.map(chain::hexAccountIdOf)

        val identitiesByAccountId = getIdentitiesFromIds(chain, accountIds)

        return accountAddresses.associateWith { identitiesByAccountId[it.toHexAccountId()] }
    }

    private suspend fun fetchIdentities(
        socketService: SocketService,
        keys: List<String>,
        runtime: RuntimeSnapshot,
        returnType: Type<*>,
        chain: Chain
    ): Map<String, Identity?> {
        return bulkRetriever.queryKeys(socketService, keys)
            .mapKeys { (fullKey, _) -> chain.accountFromMapKey(fullKey) }
            .mapValues { (_, value) ->
                value?.let { bindIdentity(it, runtime, returnType) }
            }
    }
}
