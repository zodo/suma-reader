package com.sumareader.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sumareader.MainViewModel
import com.sumareader.MainViewModel.UiState
import com.sumareader.parser.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SumaReaderApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()

    SumaReaderTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("SUMA Reader") },
                    actions = {
                        if (state is UiState.Success) {
                            IconButton(onClick = { viewModel.reset() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Scan again")
                            }
                        }
                    },
                )
            },
        ) { padding ->
            AnimatedContent(
                targetState = state,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                label = "main",
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
            ) { currentState ->
                when (currentState) {
                    is UiState.WaitingForCard -> IdleScreen()
                    is UiState.Reading -> ReadingScreen()
                    is UiState.Success -> CardResultScreen(currentState.card, onScanAgain = { viewModel.reset() })
                    is UiState.Error -> ErrorScreen(currentState.message, onRetry = { viewModel.reset() })
                }
            }
        }
    }
}

@Composable
fun IdleScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Tap your card",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Hold the card against the back of your phone\nand keep it still until the read completes",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ReadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("Reading card...", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text("Keep the card still", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onRetry) { Text("Try Again") }
        }
    }
}

@Composable
fun CardResultScreen(card: SumaCard, onScanAgain: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Hero card
        item { HeroCard(card) }

        // Cardholder
        if (card.holderName.isNotEmpty() || card.holderSurname.isNotEmpty()) {
            item {
                SectionHeader("Titular")
                Text(
                    "${card.holderName} ${card.holderSurname}".trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        // Recharges
        if (card.recharges.isNotEmpty()) {
            item { SectionHeader("Recargas") }
            items(card.recharges) { r -> RechargeRow(r) }
        }

        // Current validation
        card.currentValidation?.let { cv ->
            item { SectionHeader("Ultima validacion") }
            item { CurrentValidationCard(cv) }
        }

        // Trip history
        if (card.validations.isNotEmpty()) {
            item { SectionHeader("Historial") }
            items(card.validations) { v -> ValidationRow(v) }
        }

        // Card details
        item {
            SectionHeader("Tarjeta")
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("UID", card.uid)
                    DetailRow("Tipo", "${card.cardType}")
                    DetailRow("Subtipo", "${card.cardSubtype}")
                    DetailRow("Empresa", "${card.enterprise}")
                }
            }
        }

        // Scan again
        item {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onScanAgain,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Scan Another Card") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun HeroCard(card: SumaCard) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        card.title?.name ?: "Unknown",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (card.title != null && card.title.zone.isNotEmpty() && card.title.zone != "—") {
                        Text(
                            "Zone ${card.title.zone}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
                // Balance badge: TUIN shows euros, others show trips
                val badgeValue = when {
                    card.tuinBalanceCents != null -> "%.2f".format(card.tuinBalanceCents / 100.0)
                    card.title != null -> "${card.title.tripBalance}"
                    else -> null
                }
                val badgeLabel = if (card.tuinBalanceCents != null) "EUR" else "trips"
                if (badgeValue != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                badgeValue,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(
                                badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                card.serialNumber,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                letterSpacing = MaterialTheme.typography.titleMedium.letterSpacing * 1.5,
            )
            if (card.cardExpiry.isNotEmpty()) {
                Text(
                    "Exp. ${card.cardExpiry}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
fun RechargeRow(r: RechargeInfo) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(r.titleName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(r.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "%.2f €".format(r.amountCents / 100.0),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun CurrentValidationCard(cv: CurrentValidation) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(cv.dateTime, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(cv.operator, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
            Text(cv.typeName, style = MaterialTheme.typography.bodyMedium)
            Text("Est. ${cv.station}, vest. ${cv.vestibule}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (cv.passengers > 0) {
                Spacer(Modifier.height(4.dp))
                Text("${cv.passengers} personas", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ValidationRow(v: ValidationRecord) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(v.dateTime, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(v.operator, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(2.dp))
                Text(v.typeName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Est. ${v.station}, vest. ${v.vestibule}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (v.unitsConsumed > 0) {
                Spacer(Modifier.width(8.dp))
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "${v.unitsConsumed}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
