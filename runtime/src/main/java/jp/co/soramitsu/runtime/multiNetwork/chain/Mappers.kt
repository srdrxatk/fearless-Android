package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.core_db.model.chain.ChainAssetLocal
import jp.co.soramitsu.core_db.model.chain.ChainLocal
import jp.co.soramitsu.core_db.model.chain.ChainNodeLocal
import jp.co.soramitsu.core_db.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.AssetRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainExternalApiRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote

private const val ETHEREUM_OPTION = "ethereumBased"
private const val CROWDLOAN_OPTION = "crowdloans"
private const val TESTNET_OPTION = "testnet"

private fun mapSectionTypeRemoteToSectionType(section: String) = when (section) {
    "subquery" -> Chain.ExternalApi.Section.Type.SUBQUERY
    "github" -> Chain.ExternalApi.Section.Type.GITHUB
    else -> Chain.ExternalApi.Section.Type.UNKNOWN
}

private fun mapSectionTypeToSectionTypeLocal(sectionType: Chain.ExternalApi.Section.Type): String = sectionType.name
private fun mapSectionTypeLocalToSectionType(sectionType: String): Chain.ExternalApi.Section.Type = enumValueOf(sectionType)

private fun mapStakingStringToStakingType(stakingString: String?): Chain.Asset.StakingType {
    return when (stakingString) {
        null -> Chain.Asset.StakingType.UNSUPPORTED
        "relaychain" -> Chain.Asset.StakingType.RELAYCHAIN
        else -> Chain.Asset.StakingType.UNSUPPORTED
    }
}

private fun mapStakingTypeToLocal(stakingType: Chain.Asset.StakingType): String = stakingType.name
private fun mapStakingTypeFromLocal(stakingTypeLocal: String): Chain.Asset.StakingType = enumValueOf(stakingTypeLocal)

private fun mapSectionRemoteToSection(sectionRemote: ChainExternalApiRemote.Section?) = sectionRemote?.let {
    Chain.ExternalApi.Section(
        type = mapSectionTypeRemoteToSectionType(sectionRemote.type),
        url = sectionRemote.url
    )
}

private fun mapSectionLocalToSection(sectionLocal: ChainLocal.ExternalApi.Section?) = sectionLocal?.let {
    Chain.ExternalApi.Section(
        type = mapSectionTypeLocalToSectionType(sectionLocal.type),
        url = sectionLocal.url
    )
}

private fun mapSectionToSectionLocal(sectionLocal: Chain.ExternalApi.Section?) = sectionLocal?.let {
    ChainLocal.ExternalApi.Section(
        type = mapSectionTypeToSectionTypeLocal(sectionLocal.type),
        url = sectionLocal.url
    )
}

private const val DEFAULT_PRECISION = 10

fun mapChainRemoteToChain(
    chainsRemote: List<ChainRemote>,
    assetsRemote: List<AssetRemote>
): List<Chain> {
    val assetsById = assetsRemote.filter { it.id != null }.associateBy { it.id }
    return chainsRemote.map { chainRemote ->
        val nodes = chainRemote.nodes?.map {
            Chain.Node(
                url = it.url,
                name = it.name
            )
        }

        val assets = chainRemote.assets?.mapNotNull { chainAsset ->
            chainAsset.assetId?.let {
                val assetRemote = assetsById[chainAsset.assetId]
                Chain.Asset(
                    id = chainAsset.assetId,
                    chainId = assetRemote?.chainId.orEmpty(),
                    iconUrl = assetRemote?.icon.orEmpty(),
                    symbol = assetRemote?.symbol.orEmpty(),
                    precision = assetRemote?.precision ?: DEFAULT_PRECISION,
                    name = chainRemote.name,
                    priceId = assetRemote?.priceId,
                    staking = mapStakingStringToStakingType(chainAsset.staking),
                    priceProviders = chainAsset.purchaseProviders
                )
            }
        }

        val types = chainRemote.types?.let {
            Chain.Types(
                url = it.url,
                overridesCommon = it.overridesCommon
            )
        }

        val externalApi = chainRemote.externalApi?.let { externalApi ->
            Chain.ExternalApi(
                history = mapSectionRemoteToSection(externalApi.history),
                staking = mapSectionRemoteToSection(externalApi.staking),
                crowdloans = mapSectionRemoteToSection(externalApi.crowdloans),
            )
        }

        val optionsOrEmpty = chainRemote.options.orEmpty()

        Chain(
            id = chainRemote.chainId,
            parentId = chainRemote.parentId,
            name = chainRemote.name,
            assets = assets.orEmpty(),
            types = types,
            nodes = nodes.orEmpty(),
            icon = chainRemote.icon.orEmpty(),
            externalApi = externalApi,
            addressPrefix = chainRemote.addressPrefix,
            isEthereumBased = ETHEREUM_OPTION in optionsOrEmpty,
            isTestNet = TESTNET_OPTION in optionsOrEmpty,
            hasCrowdloans = CROWDLOAN_OPTION in optionsOrEmpty
        )
    }
}

fun mapChainLocalToChain(chainLocal: JoinedChainInfo): Chain {
    val nodes = chainLocal.nodes.map {
        Chain.Node(
            url = it.url,
            name = it.name
        )
    }

    val assets = chainLocal.assets.map {
        Chain.Asset(
            iconUrl = chainLocal.chain.icon,
            id = it.id,
            symbol = it.symbol,
            precision = it.precision,
            name = it.name,
            chainId = it.chainId,
            priceId = it.priceId,
            staking = mapStakingTypeFromLocal(it.staking),
            priceProviders = it.priceProviders?.let { Gson().fromJson(it, object : TypeToken<List<String>>() {}.type) }
        )
    }

    val types = chainLocal.chain.types?.let {
        Chain.Types(
            url = it.url,
            overridesCommon = it.overridesCommon
        )
    }

    val externalApi = chainLocal.chain.externalApi?.let { externalApi ->
        Chain.ExternalApi(
            staking = mapSectionLocalToSection(externalApi.staking),
            history = mapSectionLocalToSection(externalApi.history),
            crowdloans = mapSectionLocalToSection(externalApi.crowdloans)
        )
    }

    return with(chainLocal.chain) {
        Chain(
            id = id,
            parentId = parentId,
            name = name,
            assets = assets,
            types = types,
            nodes = nodes,
            icon = icon,
            externalApi = externalApi,
            addressPrefix = prefix,
            isEthereumBased = isEthereumBased,
            isTestNet = isTestNet,
            hasCrowdloans = hasCrowdloans
        )
    }
}

fun mapChainToChainLocal(chain: Chain): JoinedChainInfo {
    val nodes = chain.nodes.map {
        ChainNodeLocal(
            url = it.url,
            name = it.name,
            chainId = chain.id
        )
    }

    val assets = chain.assets.map {
        ChainAssetLocal(
            id = it.id,
            icon = it.iconUrl,
            precision = it.precision,
            chainId = chain.id,
            name = it.name,
            priceId = it.priceId,
            staking = mapStakingTypeToLocal(it.staking),
            priceProviders = it.priceProviders?.let { Gson().toJson(it) }
        )
    }

    val types = chain.types?.let {
        ChainLocal.TypesConfig(
            url = it.url,
            overridesCommon = it.overridesCommon
        )
    }

    val externalApi = chain.externalApi?.let { externalApi ->
        ChainLocal.ExternalApi(
            staking = mapSectionToSectionLocal(externalApi.staking),
            history = mapSectionToSectionLocal(externalApi.history),
            crowdloans = mapSectionToSectionLocal(externalApi.crowdloans)
        )
    }

    val chainLocal = with(chain) {
        ChainLocal(
            id = id,
            parentId = parentId,
            name = name,
            types = types,
            icon = icon,
            prefix = addressPrefix,
            externalApi = externalApi,
            isEthereumBased = isEthereumBased,
            isTestNet = isTestNet,
            hasCrowdloans = hasCrowdloans
        )
    }

    return JoinedChainInfo(
        chain = chainLocal,
        nodes = nodes,
        assets = assets
    )
}
