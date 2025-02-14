package jp.co.soramitsu.wallet.api.data.cache

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.AssetReadOnlyCache
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.dao.emptyAccountIdValue
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.shared_utils.runtime.AccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AssetCache(
    private val tokenPriceDao: TokenPriceDao,
    private val accountRepository: AccountRepository,
    private val assetDao: AssetDao,
    private val updatesMixin: UpdatesMixin,
    private val selectedFiat: SelectedFiat
) : AssetReadOnlyCache by assetDao,
    UpdatesProviderUi by updatesMixin {

    private val assetUpdateMutex = Mutex()

    suspend fun updateAsset(
        metaId: Long,
        accountId: AccountId,
        chainAsset: Asset,
        builder: (local: AssetLocal) -> AssetLocal
    ) = withContext(Dispatchers.IO) {
        val chainId = chainAsset.chainId
        val assetId = chainAsset.id
        val shouldUseChainlinkForRates = selectedFiat.isUsd() && chainAsset.priceProvider?.id != null
        val priceId = if (shouldUseChainlinkForRates) {
            chainAsset.priceProvider?.id
        } else {
            chainAsset.priceId
        }

        assetUpdateMutex.withLock {
            priceId?.let { tokenPriceDao.ensureTokenPrice(it) }

            val cachedAsset = assetDao.getAsset(metaId, accountId, chainId, assetId)?.asset
            when {
                cachedAsset == null -> assetDao.insertAsset(
                    builder.invoke(
                        AssetLocal.createEmpty(
                            accountId,
                            assetId,
                            chainId,
                            metaId,
                            priceId
                        )
                    )
                )

                cachedAsset.accountId.contentEquals(emptyAccountIdValue) -> {
                    assetDao.deleteAsset(metaId, emptyAccountIdValue, chainId, assetId)
                    assetDao.insertAsset(builder.invoke(cachedAsset.copy(accountId = accountId, tokenPriceId = priceId)))
                }

                else -> {
                    val updatedAsset = builder.invoke(cachedAsset.copy(tokenPriceId = priceId))
                    if (cachedAsset.bondedInPlanks == updatedAsset.bondedInPlanks &&
                        cachedAsset.feeFrozenInPlanks == updatedAsset.feeFrozenInPlanks &&
                        cachedAsset.miscFrozenInPlanks == updatedAsset.miscFrozenInPlanks &&
                        cachedAsset.freeInPlanks == updatedAsset.freeInPlanks &&
                        cachedAsset.redeemableInPlanks == updatedAsset.redeemableInPlanks &&
                        cachedAsset.reservedInPlanks == updatedAsset.reservedInPlanks &&
                        cachedAsset.unbondingInPlanks == updatedAsset.unbondingInPlanks &&
                        cachedAsset.tokenPriceId == updatedAsset.tokenPriceId &&
                        cachedAsset.status == updatedAsset.status
                    ) {
                        return@withLock
                    }
                    assetDao.updateAsset(updatedAsset)
                }
            }
        }
        updatesMixin.finishUpdateAsset(metaId, chainId, accountId, assetId)
    }

    suspend fun updateAsset(
        accountId: AccountId,
        chainAsset: Asset,
        builder: (local: AssetLocal) -> AssetLocal
    ) = withContext(Dispatchers.IO) {
        val applicableMetaAccount = accountRepository.findMetaAccount(accountId)

        applicableMetaAccount?.let {
            updateAsset(it.id, accountId, chainAsset, builder)
        }
    }

    suspend fun updateTokensPrice(
        update: List<TokenPriceLocal>
    ) = withContext(Dispatchers.IO) {
        tokenPriceDao.insertTokensPrice(update)
    }

    suspend fun updateTokenPrice(
        priceId: String,
        builder: (local: TokenPriceLocal) -> TokenPriceLocal
    ) = withContext(Dispatchers.IO) {
        val tokenPriceLocal = tokenPriceDao.getTokenPrice(priceId) ?: TokenPriceLocal.createEmpty(priceId)

        val newToken = builder.invoke(tokenPriceLocal)

        tokenPriceDao.insertTokenPrice(newToken)
    }

    suspend fun updateAsset(updateModel: List<AssetUpdateItem>) {
        val onlyUpdates = mutableListOf<AssetUpdateItem>()
        updateModel.listIterator().forEach {
            val cached = assetDao.getAsset(it.metaId, it.accountId, it.chainId, it.id)?.asset
            if (cached == null) {
                val initial = AssetLocal.createEmpty(
                    it.accountId,
                    it.id,
                    it.chainId,
                    it.metaId,
                    it.tokenPriceId
                )
                val newAsset = initial.copy(
                    sortIndex = it.sortIndex,
                    enabled = it.enabled
                )
                assetUpdateMutex.withLock {
                    assetDao.insertAsset(newAsset)
                }
            } else {
                onlyUpdates.add(it)
            }
        }
        assetUpdateMutex.withLock {
            assetDao.updateAssets(onlyUpdates)
        }
    }
}
