package pl.usagi.edziennik.ui.payments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import pl.usagi.edziennik.R
import pl.usagi.edziennik.domain.BankAccountPayment
import pl.usagi.edziennik.domain.MonthlyPayment
import pl.usagi.edziennik.ui.components.EDziennikTopBar
import pl.usagi.edziennik.ui.components.EmptyState
import pl.usagi.edziennik.ui.components.ErrorState
import pl.usagi.edziennik.ui.theme.EDziennikTheme
import pl.usagi.edziennik.util.uppercaseLocalized
import java.util.Locale

@Composable
fun PaymentsRoute(onBack: () -> Unit, onOpenSettings: () -> Unit, viewModel: PaymentsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    PaymentsScreen(state, onBack, onOpenSettings, viewModel::retry)
}

private val locale = Locale.forLanguageTag("pl-PL")

@Composable
private fun PaymentsScreen(
    state: PaymentsUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EDziennikTopBar(
                onOpenSettings = onOpenSettings,
                showBack = true,
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Opłaty",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            when {
                state.activeStudent == null -> item {
                    EmptyState(title = "Wybierz ucznia, aby zobaczyć opłaty.", message = "")
                }
                state.isLoading -> item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                state.errorMessage != null -> item {
                    ErrorState("Opłaty chwilowo niedostępne", state.errorMessage, "Spróbuj ponownie", onRetry)
                }
                state.payments != null -> {
                    val payments = state.payments
                    if (payments.bankAccounts.isEmpty() && payments.monthlyPayments.isEmpty()) {
                        item { EmptyState(title = "Brak opłat", message = "") }
                    } else {
                        item { SectionTitle("Rachunki") }
                        if (payments.bankAccounts.isEmpty()) item { MutedText("Brak rachunków") }
                        itemsIndexed(payments.bankAccounts) { _, account -> AccountSection(account) }
                        item { SectionTitle("Rozliczenia miesięczne") }
                        if (payments.monthlyPayments.isEmpty()) item { MutedText("Brak rozliczeń miesięcznych") }
                        itemsIndexed(
                            payments.monthlyPayments,
                            key = { index, _ -> "${state.activeStudent.key}-month-$index" },
                        ) { _, month -> MonthlySection(month) }
                    }
                }
            }
            item { Spacer(Modifier.height(spacing.md)) }
        }
    }
}

@Composable
private fun AccountSection(account: BankAccountPayment) {
    val spacing = EDziennikTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        account.operationName?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        account.amountDue?.let {
            Text(it.formatPln(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        PaymentField("Numer rachunku", account.accountNumber?.formatNrb())
        PaymentField("Tytuł płatności", account.paymentTitle)
        account.information?.let { information ->
            PaymentField("Synchronizacja", information.synchronizedAt?.format(paymentSyncFormatter))
            information.details.forEach { detail ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        detail.name?.takeIf(String::isNotBlank).orEmpty(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    detail.value?.let { Text(it.formatPln(), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun MonthlySection(payment: MonthlyPayment) {
    val spacing = EDziennikTheme.spacing
    var expanded by rememberSaveable(payment) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = payment.details.isNotEmpty()) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                payment.month?.let {
                    Text(
                        it.format(paymentMonthFormatter).replaceFirstChar(Char::titlecase),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                payment.paymentDeadline?.let { MutedText("Termin płatności: ${it.format(paymentDateFormatter)}") }
            }
            if (payment.details.isNotEmpty()) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Zwiń szczegóły" else "Rozwiń szczegóły",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val charges = payment.chargeLines()
        if (charges.isEmpty()) MutedText("Brak naliczeń")
        charges.forEach { (name, value) -> PaymentField(name, value) }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                payment.details.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        group.name?.takeIf(String::isNotBlank)?.let { SectionTitle(it) }
                        group.information.forEach { detail ->
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                                detail.date?.let { MutedText(it.format(paymentDateFormatter)) }
                                detail.value?.let { Text(it.formatPln(), style = MaterialTheme.typography.bodyMedium) }
                                detail.info?.takeIf(String::isNotBlank)?.let { MutedText(it) }
                                detail.info2?.takeIf(String::isNotBlank)?.let { MutedText(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentField(label: String?, value: String?) {
    if (value.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.xxs)) {
        label?.takeIf(String::isNotBlank)?.let { MutedText(it) }
        value?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercaseLocalized(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun MutedText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
