package jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.moonbeam

import android.content.Context
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.interfaces.SelectedAccountUseCase
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeFactory
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.moonbeam.MoonbeamContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamContributeSubmitter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamStep1Terms
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamStep2Registration
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamStep3Signed
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamStep4Contribute
import kotlinx.coroutines.CoroutineScope

class MoonbeamContributeFactory(
    override val submitter: MoonbeamContributeSubmitter,
    private val interactor: MoonbeamContributeInteractor,
    private val resourceManager: ResourceManager,
    private val accountUseCase: SelectedAccountUseCase,
    ) : CustomContributeFactory {

    override val flowType = "moonbeam"

    override fun createViewState(scope: CoroutineScope, payload: CustomContributePayload): MoonbeamContributeViewState {
        return MoonbeamContributeViewState(interactor, payload, resourceManager, scope, accountUseCase)
    }

    override fun createView(context: Context, step: Int): CustomContributeView {
        val customContributeView = when (step) {
            0 -> MoonbeamStep1Terms(context)
            1 -> MoonbeamStep2Registration(context)
            2 -> MoonbeamStep3Signed(context)
            3 -> MoonbeamStep4Contribute(context)
            else -> throw error("Not implemented screen for step $step")
        }
        return customContributeView
    }
}
