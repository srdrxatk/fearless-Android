package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.moonbeam.MoonbeamContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.ApplyActionState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger
import java.util.regex.Pattern

class MoonbeamContributeViewState(
    private val interactor: MoonbeamContributeInteractor,
    val customContributePayload: CustomContributePayload,
    resourceManager: ResourceManager,
    coroutineScope: CoroutineScope,
) : CustomContributeViewState {

    val title = customContributePayload.parachainMetadata.run {
        "$name ($token)"
    }

    init {
        interactor.nextStep(customContributePayload)
    }

    val privacyAcceptedFlow = MutableStateFlow(customContributePayload.isPrivacyAccepted ?: false)

    suspend fun getSystemRemarkFee(): BigInteger {
        return interactor.getSystemRemarkFee()
    }

    suspend fun termsText(): String =
        interactor.getTerms()

    val enteredEtheriumArrdessFlow = MutableStateFlow("")

    val isEtheriumArrdessCorrectFlow = enteredEtheriumArrdessFlow.map {
        isEtheriumCorrect(it)
    }

    private fun isEtheriumCorrect(address: String): Boolean {
        val pattern = "0x[A-Fa-f0-9]{40}"
        return Pattern.matches(pattern, address)
    }

    override val applyActionState = when (customContributePayload.step) {
        0 -> privacyAcceptedFlow.map { privacyAccepted ->
            when {
                privacyAccepted -> ApplyActionState.Available
                else -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.common_continue))
            }
        }
        3 -> enteredEtheriumArrdessFlow.map { ethAddress ->
            when {
                ethAddress.isEmpty() -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.common_continue))
                else -> ApplyActionState.Available
            }
        }
        else -> flow {
            ApplyActionState.Available
        }
    }

    override suspend fun generatePayload(): Result<BonusPayload> = runCatching {
        val payload = createBonusPayload()
        payload
    }

    private fun createBonusPayload(): ReferralCodePayload {
        return MoonbeamBonusPayload("", customContributePayload.parachainMetadata.rewardRate)
    }
}
