    package org.ttproject.screens

    import androidx.compose.animation.AnimatedVisibility
    import androidx.compose.animation.core.tween
    import androidx.compose.animation.fadeIn
    import androidx.compose.animation.slideInVertically
    import androidx.compose.foundation.BorderStroke
    import androidx.compose.foundation.Image
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.gestures.detectTapGestures
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.foundation.text.KeyboardOptions
    import androidx.compose.material.icons.Icons
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
    import kotlinx.coroutines.launch
    import org.jetbrains.compose.resources.painterResource
    import org.jetbrains.compose.resources.stringResource
    import org.koin.compose.viewmodel.koinViewModel
    import org.ttproject.AppColors
    import org.ttproject.isDark
    import org.ttproject.shared.resources.already_have_an_account
    import org.ttproject.shared.resources.continue_with_google
    import org.ttproject.shared.resources.don_t_have_an_account
    import org.ttproject.shared.resources.forgot_password
    import org.ttproject.shared.resources.login
    import org.ttproject.shared.resources.or
    import org.ttproject.viewmodel.LoginState
    import org.ttproject.viewmodel.LoginViewModel
    import ttproject.composeapp.generated.resources.Res
    import org.ttproject.shared.resources.Res as SharedRes
    import ttproject.composeapp.generated.resources.match_logo_long
    import ttproject.composeapp.generated.resources.match_logo_long_dark
    import org.ttproject.shared.resources.password
    import org.ttproject.shared.resources.register
    import org.ttproject.util.rememberGoogleAuthClient
    import ttproject.composeapp.generated.resources.google_log
    import ttproject.composeapp.generated.resources.matchpoint_logo_long_dark
    import ttproject.composeapp.generated.resources.matchpoint_logo_long_light

    @Composable
    fun LoginScreen(
        viewModel: LoginViewModel = koinViewModel(),
        onLoginSuccess: () -> Unit,
        onNavigateToRegister: () -> Unit
    ) {
        val uiState by viewModel.uiState.collectAsState()

        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }

        val focusManager = LocalFocusManager.current
        val scope = rememberCoroutineScope()
        val googleAuthClient = rememberGoogleAuthClient()
        var isGoogleLoading by remember { mutableStateOf(false) }
        var googleDebugError by remember { mutableStateOf<String?>(null) }

        // 👇 1. Setup the entrance animation state
        var isVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            isVisible = true
        }

        LaunchedEffect(uiState) {
            if (uiState is LoginState.Success) {
                isGoogleLoading = false
                onLoginSuccess()
                viewModel.resetState()
            } else if (uiState is LoginState.Error) {
                isGoogleLoading = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- CHUNK 1: LOGO (No delay) ---
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

                Spacer(modifier = Modifier.height(48.dp))

                // --- CHUNK 2: INPUTS (Delay 100ms) ---
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(500, delayMillis = 100)) + slideInVertically(initialOffsetY = { 40 }, animationSpec = tween(500, delayMillis = 100))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(SharedRes.string.password)) },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                val description = if (passwordVisible) "Hide password" else "Show password"
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = description, tint = AppColors.TextGray)
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

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(SharedRes.string.forgot_password),
                            color = AppColors.AccentOrange,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .align(Alignment.End)
                                .clickable { /* TODO: Navigate to recovery */ }
                                .padding(4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // --- CHUNK 3: PRIMARY BUTTON & DIVIDER (Delay 200ms) ---
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(500, delayMillis = 200)) + slideInVertically(initialOffsetY = { 40 }, animationSpec = tween(500, delayMillis = 200))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedVisibility(visible = uiState is LoginState.Error) {
                            if (uiState is LoginState.Error) {
                                val errorMessage = (uiState as LoginState.Error).message
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = AppColors.ErrorText, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(errorMessage, color = AppColors.ErrorText, fontSize = 14.sp)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.login(email, password)
                            },
                            enabled = uiState !is LoginState.Loading && email.isNotBlank() && password.isNotBlank(),
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
                                Text(stringResource(SharedRes.string.login).uppercase(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = AppColors.TextGray.copy(alpha = 0.3f))
                            Text(" ${stringResource(SharedRes.string.or)} ", color = AppColors.TextGray, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
                            HorizontalDivider(modifier = Modifier.weight(1f), color = AppColors.TextGray.copy(alpha = 0.3f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // --- CHUNK 4: GOOGLE BUTTON & FOOTER (Delay 300ms) ---
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(500, delayMillis = 300)) + slideInVertically(initialOffsetY = { 40 }, animationSpec = tween(500, delayMillis = 300))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (googleDebugError != null) {
                            Text(
                                text = googleDebugError!!,
                                color = Color.Red,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus()
                                isGoogleLoading = true
                                googleDebugError = null
                                scope.launch {
                                    try {
                                        val idToken = googleAuthClient.signIn()
                                        if (idToken != null) viewModel.googleLogin(idToken)
                                    } catch (e: Exception) {
                                        isGoogleLoading = false
                                        googleDebugError = e.message
                                    }
                                }
                            },
                            enabled = !isGoogleLoading && uiState !is LoginState.Loading,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            border = BorderStroke(1.dp, AppColors.TextGray.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
                        ) {
                            if (isGoogleLoading) {
                                CircularProgressIndicator(color = AppColors.TextPrimary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Image(
                                    painter = painterResource(Res.drawable.google_log),
                                    contentDescription = "Google Logo",
                                    modifier = Modifier.size(24.dp).padding(end = 8.dp)
                                )
                                Text(
                                    text = stringResource(SharedRes.string.continue_with_google),
                                    color = AppColors.TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(SharedRes.string.don_t_have_an_account) + " ",
                                color = AppColors.TextGray,
                                fontSize = 14.sp
                            )
                            Text(
                                text = stringResource(SharedRes.string.register),
                                color = AppColors.AccentOrange,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onNavigateToRegister() }.padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }