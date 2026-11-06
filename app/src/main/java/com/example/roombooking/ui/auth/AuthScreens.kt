package com.example.roombooking.ui.auth

import com.example.roombooking.ui.common.appCardColors
import androidx.compose.foundation.layout.heightIn
import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.roombooking.ui.theme.Brand
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roombooking.data.repository.AuthRepository
import com.example.roombooking.ui.common.BounceButton
import com.example.roombooking.ui.common.bob
import com.example.roombooking.ui.common.floatingOrbs
import com.example.roombooking.ui.common.flowingGradient
import com.example.roombooking.ui.common.shakeOn
import com.example.roombooking.ui.common.appViewModelFactory
import com.example.roombooking.ui.common.enterAnimation
import kotlinx.coroutines.launch

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirm by mutableStateOf("")
    var fullName by mutableStateOf("")
    var faculty by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** Non-null after a failed submit; the form shakes each time this changes. */
    val failure: Any? get() = error ?: fieldErrors.takeIf { it.isNotEmpty() }

    private fun validEmail() = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    fun login() {
        val errs = buildMap {
            if (!validEmail()) put("email", "รูปแบบอีเมลไม่ถูกต้อง")
            if (password.isEmpty()) put("password", "กรุณากรอกรหัสผ่าน")
        }
        fieldErrors = errs
        if (errs.isNotEmpty()) return
        submit { repo.login(email, password) }
    }

    fun register() {
        val errs = buildMap {
            if (fullName.isBlank()) put("fullName", "กรุณากรอกชื่อ-นามสกุล")
            if (!validEmail()) put("email", "รูปแบบอีเมลไม่ถูกต้อง")
            if (password.length < 8) put("password", "รหัสผ่านอย่างน้อย 8 ตัวอักษร")
            if (confirm != password) put("confirm", "รหัสผ่านไม่ตรงกัน")
        }
        fieldErrors = errs
        if (errs.isNotEmpty()) return
        submit { repo.register(fullName, email, faculty, password) }
    }

    // On success the SessionManager switches to LoggedIn and AppRoot shows the main screens.
    private fun submit(call: suspend () -> Result<*>) {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            call().onFailure { error = it.message }
            loading = false
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> AuthViewModel(c.authRepository) }
    }
}

@Composable
private fun AuthForm(title: String, failure: Any?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // animated colour band behind the top of the screen
        Box(
            Modifier.fillMaxWidth().height(320.dp)
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .flowingGradient()
                .floatingOrbs(),
        )
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            // emblem: floating glass badge with the room icon
            Box(
                Modifier.size(84.dp).enterAnimation(0, offsetPx = -24f).bob(10f)
                    .background(Brand.Highlight.copy(alpha = 0.3f), CircleShape).padding(8.dp)
                    .background(Brand.Highlight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.MeetingRoom, null, Modifier.size(40.dp), tint = Brand.Ink)
            }
            Spacer(Modifier.height(16.dp))
            Text("ระบบจองห้อง", Modifier.enterAnimation(1), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("Smart Room Booking · มหาวิทยาลัย", Modifier.enterAnimation(1),
                style = MaterialTheme.typography.titleSmall, color = Brand.Highlight)
            Spacer(Modifier.height(32.dp))
            Card(
                Modifier.fillMaxWidth().enterAnimation(2, offsetPx = 80f).shakeOn(failure),
                shape = MaterialTheme.shapes.large,
                colors = appCardColors(),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Box(Modifier.width(48.dp).height(2.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                    content()
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("สำหรับนักศึกษาและบุคลากรเท่านั้น", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Field(
    label: String, value: String, onChange: (String) -> Unit, error: String?,
    password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SubmitButton(text: String, loading: Boolean, onClick: () -> Unit) {
    BounceButton(onClick = onClick, enabled = !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        // spinner and label cross-fade instead of popping
        Crossfade(targetState = loading, label = "submit") { busy ->
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(text)
        }
    }
}

@Composable
fun LoginScreen(onRegister: () -> Unit, vm: AuthViewModel = viewModel(factory = AuthViewModel.Factory)) {
    AuthForm("เข้าสู่ระบบ", vm.failure) {
        Field("อีเมล", vm.email, { vm.email = it }, vm.fieldErrors["email"], keyboard = KeyboardType.Email)
        Field("รหัสผ่าน", vm.password, { vm.password = it }, vm.fieldErrors["password"], password = true)
        AnimatedVisibility(vm.error != null) { Text(vm.error.orEmpty(), color = MaterialTheme.colorScheme.error) }
        SubmitButton("เข้าสู่ระบบ", vm.loading, vm::login)
        TextButton(onClick = onRegister) { Text("ยังไม่มีบัญชี? สมัครสมาชิก") }
    }
}

@Composable
fun RegisterScreen(onBack: () -> Unit, vm: AuthViewModel = viewModel(factory = AuthViewModel.Factory)) {
    AuthForm("สมัครสมาชิก", vm.failure) {
        Field("ชื่อ-นามสกุล", vm.fullName, { vm.fullName = it }, vm.fieldErrors["fullName"])
        Field("อีเมล", vm.email, { vm.email = it }, vm.fieldErrors["email"], keyboard = KeyboardType.Email)
        Field("คณะ/หน่วยงาน (ไม่บังคับ)", vm.faculty, { vm.faculty = it }, null)
        Field("รหัสผ่าน (อย่างน้อย 8 ตัว)", vm.password, { vm.password = it }, vm.fieldErrors["password"], password = true)
        Field("ยืนยันรหัสผ่าน", vm.confirm, { vm.confirm = it }, vm.fieldErrors["confirm"], password = true)
        AnimatedVisibility(vm.error != null) { Text(vm.error.orEmpty(), color = MaterialTheme.colorScheme.error) }
        SubmitButton("สมัครสมาชิก", vm.loading, vm::register)
        TextButton(onClick = onBack) { Text("มีบัญชีแล้ว? เข้าสู่ระบบ") }
    }
}
