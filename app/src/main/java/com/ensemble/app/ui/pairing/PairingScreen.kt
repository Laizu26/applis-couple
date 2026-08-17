package com.ensemble.app.ui.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ensemble.app.R
import com.ensemble.app.ui.theme.RosePrimary

@Composable
fun PairingScreen(viewModel: PairingViewModel, onPaired: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showJoinField by remember { mutableStateOf(uiState.existingCoupleId != null) }
    var codeInput by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Favorite,
            contentDescription = null,
            tint = RosePrimary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.pairing_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))

        when {
            uiState.generatedCode != null -> {
                Text(stringResource(R.string.pairing_code_explain), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = uiState.generatedCode.orEmpty(),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(uiState.generatedCode.orEmpty())) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.pairing_code_copy)) }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onPaired, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pairing_confirm))
                }
            }

            !showJoinField -> {
                Button(onClick = viewModel::createCouple, enabled = !uiState.isLoading, modifier = Modifier.fillMaxWidth()) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.pairing_create))
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { showJoinField = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pairing_join))
                }
            }

            else -> {
                Text(stringResource(R.string.pairing_enter_code), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text(stringResource(R.string.pairing_code_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.joinWithCode(codeInput, onPaired) },
                    enabled = !uiState.isLoading && codeInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.pairing_confirm))
                    }
                }
                if (uiState.existingCoupleId == null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { showJoinField = false }) {
                        Text(stringResource(R.string.pairing_create))
                    }
                }
            }
        }

        uiState.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
