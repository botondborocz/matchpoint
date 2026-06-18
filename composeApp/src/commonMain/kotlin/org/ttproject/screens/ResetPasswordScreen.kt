package org.ttproject.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.ttproject.AppColors
import org.ttproject.isDark
import org.ttproject.viewmodel.LoginState
import org.ttproject.viewmodel.LoginViewModel
import ttproject.composeapp.generated.resources.Res
import ttproject.composeapp.generated.resources.matchpoint_logo_long_dark
import ttproject.composeapp.generated.resources.matchpoint_logo_long_light
import org.ttproject.shared.resources.password
import org.ttproject.shared.resources.Res as SharedRes

@Composable
fun ResetPasswordScreen(
    viewModel: LoginViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    LaunchedEffect(uiState) {
        if (uiState is LoginState.Success) {
            viewModel.resetState()
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .zIndex(100f) // Keep it completely on top of other elements
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
        contentAlignment = Alignment.Center
    ) {
        // Close Button
        IconButton(
            onClick = {
                viewModel.resetState()
                onDismiss()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .statusBarsPadding()
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = AppColors.TextPrimary)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Logo
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(500)) + slideInVertically(initialOffsetY = { 40 }, animationSpec = tween(500))
            ) {
                if (isDark) {
                    Image(
                        painter = painterResource(Res.drawable.matchpoint_logo_long_dark),
                        contentDescription = "App Logo",
                        modifier = Modifier.height(64.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(Res.drawable.matchpoint_logo_long_light),
                        contentDescription = "App Logo",
                        modifier = Modifier.height(64.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Reset Your Password",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )

            Text(
                text = "Please enter your new password below.",
                fontSize = 14.sp,
                color = AppColors.TextGray,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Password Fields
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(500, delayMillis = 100)) + slideInVertically(initialOffsetY = { 40 }, animationSpec = tween(500, delayMillis = 100))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { 
                            newPassword = it
                            localError = null
                        },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = "Toggle Visibility", tint = AppColors.TextGray)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.AccentOrange,
                            unfocusedBorderColor = AppColors.TextGray.copy(alpha = 0.5f),
                            focusedLabelColor = AppColors.AccentOrange,
                            unfocusedLabelColor = AppColors.TextGray,
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            cursorColor = AppColors.AccentOrange
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { 
                            confirmPassword = it
                            localError = null
                        },
                        label = { Text("Confirm New Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.AccentOrange,
                            unfocusedBorderColor = AppColors.TextGray.copy(alpha = 0.5f),
                            focusedLabelColor = AppColors.AccentOrange,
                            unfocusedLabelColor = AppColors.TextGray,
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            cursorColor = AppColors.AccentOrange
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action Button and Error Feedback
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(500, delayMillis = 200)) + slideInVertically(initialOffsetY = { 40 }, animationSpec = tween(500, delayMillis = 200))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val displayError = localError ?: (uiState as? LoginState.Error)?.message
                    
                    AnimatedVisibility(visible = displayError != null) {
                        if (displayError != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AppColors.ErrorText, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(displayError, color = AppColors.ErrorText, fontSize = 14.sp)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (newPassword.length < 6) {
                                localError = "Password must be at least 6 characters."
                                return@Button
                            }
                            if (newPassword != confirmPassword) {
                                localError = "Passwords do not match."
                                return@Button
                            }
                            viewModel.resetPassword(newPassword)
                        },
                        enabled = uiState !is LoginState.Loading && newPassword.isNotEmpty() && confirmPassword.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.AccentOrange,
                            disabledContainerColor = AppColors.AccentOrange.copy(alpha = 0.5f)
                        )
                    ) {
                        if (uiState is LoginState.Loading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("SAVE NEW PASSWORD", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
