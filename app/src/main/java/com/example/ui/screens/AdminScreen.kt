package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ListingReport
import com.example.ui.components.EmptyStateView
import com.example.ui.viewmodel.WorkspaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    workspaceViewModel: WorkspaceViewModel,
    onRoomClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val reports by remember { workspaceViewModel.observeAdminReports() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var verifyUidInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Moderation") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Verify Property Owner", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = verifyUidInput,
                                    onValueChange = { verifyUidInput = it },
                                    label = { Text("Owner User ID") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (verifyUidInput.isNotBlank()) {
                                            workspaceViewModel.verifyOwner(verifyUidInput.trim())
                                            verifyUidInput = ""
                                        }
                                    },
                                    enabled = verifyUidInput.isNotBlank()
                                ) {
                                    Icon(Icons.Filled.Verified, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Verify")
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Open Reports (${reports.size})", style = MaterialTheme.typography.titleMedium)
                }

                if (reports.isEmpty()) {
                    item {
                        EmptyStateView(
                            title = "No open reports",
                            subtitle = "Reported listings and reviews requiring moderation appear here."
                        )
                    }
                } else {
                    items(reports, key = { it.id }) { report ->
                        ReportCard(
                            report = report,
                            onInspect = { if (report.targetType == "ROOM") onRoomClick(report.targetId) },
                            onRejectListing = { workspaceViewModel.resolveReport(report, removeListing = true) },
                            onDismiss = { workspaceViewModel.resolveReport(report, removeListing = false) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(
    report: ListingReport,
    onInspect: () -> Unit,
    onRejectListing: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${report.targetType} • ${report.reason}", style = MaterialTheme.typography.titleSmall)
            if (report.details.isNotBlank()) {
                Text(report.details, style = MaterialTheme.typography.bodySmall)
            }
            Text("Target ID: ${report.targetId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onInspect, modifier = Modifier.weight(1f)) { Text("Inspect") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Dismiss") }
                Button(onClick = onRejectListing, modifier = Modifier.weight(1f)) { Text("Reject") }
            }
        }
    }
}
