package jp.co.soramitsu.staking.impl.presentation.staking.bond.confirm

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.BaseConfirmViewModel

@HiltViewModel
class ConfirmPoolBondMoreViewModel @Inject constructor(
    poolSharedStateProvider: StakingPoolSharedStateProvider,
    private val stakingPoolInteractor: StakingPoolInteractor,
    resourceManager: ResourceManager,
    private val router: StakingRouter
) : BaseConfirmViewModel(
    address = requireNotNull(poolSharedStateProvider.mainState.get()?.address),
    resourceManager = resourceManager,
    asset = requireNotNull(poolSharedStateProvider.mainState.get()?.asset),
    amountInPlanks = requireNotNull(poolSharedStateProvider.manageState.get()?.amountInPlanks),
    feeEstimator = { amount -> stakingPoolInteractor.estimateBondMoreFee(amount) },
    executeOperation = { address, amount -> stakingPoolInteractor.bondMore(address, amount) },
    onOperationSuccess = { router.returnToManagePoolStake() }
) {
    fun onBackClick() {
        router.back()
    }
}
