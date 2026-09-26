package com.aectann.battlecity.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aectann.battlecity.TanksStrings
import org.jetbrains.compose.resources.stringResource

@Composable
fun AdsAgeScreen(onAgeSelected: (Int?) -> Unit) {
    var enteredAge by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val selectAge: (Int?) -> Unit = {
        focusManager.clearFocus()
        onAgeSelected(it)
    }
    Box(
        Modifier.fillMaxSize().background(MenuBackground).safeContentPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(TanksStrings.adsAgeTitle), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(TanksStrings.adsAgePrompt))
            OutlinedTextField(
                value = enteredAge,
                onValueChange = { value ->
                    if (value.length <= 3 && value.all { it in '0'..'9' }) {
                        enteredAge = value
                        invalid = false
                    }
                },
                label = { Text(stringResource(TanksStrings.adsAgeLabel)) },
                singleLine = true,
                isError = invalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            if (invalid) Text(stringResource(TanksStrings.adsAgeInvalid), color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    val age = enteredAge.toIntOrNull()
                    if (age == null || age !in 0..130) invalid = true else selectAge(age)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(TanksStrings.adsAgeContinue)) }
            OutlinedButton(onClick = { selectAge(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(TanksStrings.adsAgeSkip))
            }
        }
    }
}
