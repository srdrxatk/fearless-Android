package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark

data class ConfirmScreenViewState(
    val toolbarViewState: ToolbarViewState,
    val address: TitleValueViewState,
    val amount: TitleValueViewState,
    val networkFee: TitleValueViewState,
    val assetIcon: String
)

@Composable
fun ConfirmScreen(state: ConfirmScreenViewState, onNavigationClick: () -> Unit, onConfirm: () -> Unit) {
    BottomSheetScreen(Modifier.verticalScroll(rememberScrollState())) {
        Toolbar(state = state.toolbarViewState, onNavigationClick = onNavigationClick)
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
        ) {
            MarginVertical(margin = 24.dp)
            GradientIcon(icon = state.assetIcon, color = colorAccentDark, modifier = Modifier.align(Alignment.CenterHorizontally))
            H2(
                text = stringResource(id = R.string.common_confirm),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = black2
            )
            MarginVertical(margin = 8.dp)
            H1(text = requireNotNull(state.amount.value), modifier = Modifier.align(Alignment.CenterHorizontally))
            MarginVertical(margin = 24.dp)
            InfoTable(listOf(state.address, state.amount, state.networkFee))
            MarginVertical(margin = 24.dp)
            AccentButton(
                text = stringResource(id = R.string.common_confirm),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = onConfirm
            )
            MarginVertical(margin = 16.dp)
        }
    }
}

@Preview
@Composable
private fun ConfirmJoinPoolScreenPreview() {
    val state = ConfirmScreenViewState(
        toolbarViewState = ToolbarViewState("Confirm", R.drawable.ic_arrow_back_24dp),
        address = TitleValueViewState("From", "Account for join", "0x3784348729384923849223423"),
        amount = TitleValueViewState("Amount", "10KSM", "$30"),
        networkFee = TitleValueViewState("Network Fee", "0.0051 KSM", "$0.32"),
        assetIcon = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg"
    )
    FearlessTheme {
        ConfirmScreen(state, {}, {})
    }
}
