package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.acala

import java.math.BigDecimal
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.signWithAccount
import jp.co.soramitsu.feature_crowdloan_impl.BuildConfig
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.acala.AcalaApi
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.acala.AcalaContributeRequest
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.acala.AcalaTransferRequest
import jp.co.soramitsu.feature_crowdloan_impl.data.network.blockhain.extrinsic.addRemarkWithEvent
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala.AcalaBonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala.AcalaContributionType.DirectDOT
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala.AcalaContributionType.LcDOT
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AcalaContributeInteractor(
    private val acalaApi: AcalaApi,
    private val httpExceptionHandler: HttpExceptionHandler,
    private val accountRepository: AccountRepository,
) {

    suspend fun isReferralValid(referralCode: String, apiUrl: String) =
        httpExceptionHandler.wrap {
            BuildConfig.DEBUG || acalaApi.isReferralValid(apiUrl, referralCode).result
        }

    suspend fun submitOffChain(payload: AcalaBonusPayload, amount: BigDecimal, apiUrl: String, apiKey: String): Result<Unit> =
        when (payload.contributionType) {
            DirectDOT -> performContribute(payload, amount, apiUrl, apiKey)
            LcDOT -> performTransfer(payload, amount, apiUrl, apiKey)
            else -> Result.failure(Exception("Unsupported contribution type: ${payload.contributionType?.name}"))
        }

    private suspend fun performContribute(payload: AcalaBonusPayload, amount: BigDecimal, apiUrl: String, apiKey: String): Result<Unit> = runCatching {
        httpExceptionHandler.wrap {

            val statementResult = acalaApi.getStatement(apiUrl)
            val statement = statementResult.statement

            val selectedAccount = accountRepository.getSelectedAccount()
            val statementSignature = accountRepository.signWithAccount(selectedAccount, statement.toByteArray())

            val useEmail = when {
                payload.email.isNullOrEmpty() -> null
                else -> payload.email
            }
            acalaApi.contribute(
                apiUrl, "Bearer $apiKey",
                AcalaContributeRequest(
                    address = selectedAccount.address,
                    amount = Token.Type.DOT.planksFromAmount(amount),
                    signature = statementSignature.toHexString(true),
                    referral = payload.referralCode,
                    email = useEmail,
                    receiveEmail = payload.agreeReceiveEmail
                )
            )
        }
    }

    private suspend fun performTransfer(payload: AcalaBonusPayload, amount: BigDecimal, apiUrl: String, apiKey: String): Result<Unit> = runCatching {
        httpExceptionHandler.wrap {
            val address = accountRepository.getSelectedAccount().address
            val useEmail = when {
                payload.email.isNullOrEmpty() -> null
                else -> payload.email
            }
            acalaApi.transfer(
                apiUrl, "Bearer $apiKey",
                AcalaTransferRequest(
                    address = address,
                    amount = Token.Type.DOT.planksFromAmount(amount),
                    referral = payload.referralCode,
                    email = useEmail,
                    receiveEmail = payload.agreeReceiveEmail
                )
            )
        }
    }

    suspend fun submitRemark(payload: AcalaBonusPayload, extrinsicBuilder: ExtrinsicBuilder) = withContext(Dispatchers.Default) {
        val statement = acalaApi.getStatement(payload.baseUrl).statement
        extrinsicBuilder.addRemarkWithEvent(statement)
        if (payload.referralCode.isNotEmpty()) {
            extrinsicBuilder.addRemarkWithEvent("referrer:${payload.referralCode}")
        }
    }
}
