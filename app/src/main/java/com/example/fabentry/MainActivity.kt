package com.example.fabentry

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.* // Importa todos los iconos básicos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fabentry.ui.theme.FabEntryTheme
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FabEntryTheme {
                MainNavigation()
            }
        }
    }
}

// --- MODELOS DE DATOS ---
data class IngresoLog(
    val nombre: String = "",
    val rut: String = "",
    val fecha_hora: String = ""
)

// --- NAVEGACIÓN ---
@Composable
fun MainNavigation() {
    var currentScreen by remember { mutableStateOf("TERMINAL") }
    var adminName by remember { mutableStateOf("") }

    if (currentScreen == "TERMINAL") {
        SecurityAccessScreen(
            onAdminLoginSuccess = { nombreDocente ->
                adminName = nombreDocente
                currentScreen = "DASHBOARD"
            }
        )
    } else {
        TeacherDashboard(
            docenteName = adminName,
            onLogout = { currentScreen = "TERMINAL" }
        )
    }
}

// --- PANTALLA 1: TERMINAL DE ACCESO (PÚBLICA) ---
@Composable
fun SecurityAccessScreen(onAdminLoginSuccess: (String) -> Unit) {
    var inputNombre by remember { mutableStateOf("") }
    var inputRut by remember { mutableStateOf("") }
    var inputPin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var accessGranted by remember { mutableStateOf(false) }
    var showAdminDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val database = Firebase.database

    // Animación de color Rojo -> Verde
    val mainColor by animateColorAsState(
        targetValue = if (accessGranted) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
        animationSpec = tween(500), label = "color"
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // Botón Oculto Admin (Escudo)
        IconButton(
            onClick = { showAdminDialog = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Security, contentDescription = "Admin", tint = Color.DarkGray)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (accessGranted) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = null, tint = mainColor, modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(if (accessGranted) "BIENVENIDO" else "FABENTRY", color = mainColor, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("CONTROL DE ACCESO", color = Color.Gray, fontSize = 10.sp, letterSpacing = 4.sp)

            Spacer(modifier = Modifier.height(40.dp))

            // Formulario
            AccessTextField(inputNombre, { inputNombre = it }, "Nombre Completo", Icons.Default.Person, mainColor)
            Spacer(modifier = Modifier.height(16.dp))
            AccessTextField(inputRut, { inputRut = it }, "RUT (Sin puntos)", Icons.Default.Badge, mainColor) // Requiere Material Extended o usar AccountBox
            Spacer(modifier = Modifier.height(16.dp))
            AccessTextField(inputPin, { inputPin = it }, "PIN", Icons.Default.Pin, mainColor, isPassword = true, keyboardType = KeyboardType.NumberPassword)

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    // LIMPIEZA DE DATOS
                    val rutClean = inputRut.replace(".", "").trim()
                    val pinClean = inputPin.trim()
                    val nombreClean = inputNombre.trim()

                    if (rutClean.isEmpty() || pinClean.isEmpty()) return@Button

                    isLoading = true
                    val usersRef = database.getReference("usuarios_autorizados").child(rutClean)

                    usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            isLoading = false
                            if (snapshot.exists()) {
                                val dbPin = snapshot.child("pin").value.toString()
                                val dbNombre = snapshot.child("nombre").value.toString()

                                if (dbPin == pinClean && dbNombre.equals(nombreClean, ignoreCase = true)) {
                                    // ÉXITO
                                    accessGranted = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress) // Vibración
                                    database.getReference("estado_puerta").setValue("ABIERTO")

                                    val log = IngresoLog(dbNombre, rutClean, SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
                                    database.getReference("registro_ingresos").push().setValue(log)

                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        database.getReference("estado_puerta").setValue("CERRADO")
                                        accessGranted = false
                                        inputPin = ""; inputRut = ""; inputNombre = ""
                                    }, 3000)
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // Vibración Error
                                    Toast.makeText(context, "Credenciales Incorrectas", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                Toast.makeText(context, "Usuario no encontrado", Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onCancelled(error: DatabaseError) { isLoading = false }
                    })
                },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mainColor),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("INGRESAR", fontWeight = FontWeight.Bold)
            }
        }
    }

    // Login Admin Popup
    if (showAdminDialog) {
        AdminLoginDialog(
            onDismiss = { showAdminDialog = false },
            onLogin = { rut, pin ->
                val rutClean = rut.replace(".", "").trim()
                val pinClean = pin.trim()

                database.getReference("usuarios_autorizados").child(rutClean)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists() &&
                                snapshot.child("pin").value.toString() == pinClean &&
                                snapshot.child("rol").value.toString() == "Docente") {
                                showAdminDialog = false
                                onAdminLoginSuccess(snapshot.child("nombre").value.toString())
                            } else {
                                Toast.makeText(context, "Acceso Denegado", Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            }
        )
    }
}

// --- PANTALLA 2: DASHBOARD DOCENTE ---
@Composable
fun TeacherDashboard(docenteName: String, onLogout: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Historial", "Usuarios", "Crear")

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PANEL ADMIN", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                Text(docenteName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.Default.ExitToApp, contentDescription = "Salir", tint = Color.Gray)
            }
        }

        // Tabs
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Column(modifier = Modifier.weight(1f).clickable { selectedTab = index }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = title.uppercase(), color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, thickness = 2.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            when (selectedTab) {
                0 -> HistoryView()
                1 -> UsersListView()
                2 -> AddUserView()
            }
        }
    }
}

// --- VISTAS DEL DASHBOARD ---

@Composable
fun HistoryView() {
    val fullLogs = remember { mutableStateListOf<IngresoLog>() }
    var searchQuery by remember { mutableStateOf("") }
    val database = Firebase.database

    DisposableEffect(Unit) {
        val ref = database.getReference("registro_ingresos").limitToLast(50)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                fullLogs.clear()
                val temp = mutableListOf<IngresoLog>()
                snapshot.children.forEach { it.getValue(IngresoLog::class.java)?.let { log -> temp.add(log) } }
                fullLogs.addAll(temp.reversed())
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    val filteredLogs = fullLogs.filter { it.nombre.contains(searchQuery, true) || it.rut.contains(searchQuery) }

    Column {
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White),
            singleLine = true
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(filteredLogs) { log ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(log.nombre, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(log.fecha_hora, color = Color.Gray, fontSize = 10.sp)
                    }
                }
                Divider(color = Color(0xFF333333))
            }
        }
    }
}

@Composable
fun UsersListView() {
    data class UsuarioItem(val rut: String, val nombre: String, val rol: String)
    val users = remember { mutableStateListOf<UsuarioItem>() }
    val database = Firebase.database
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val ref = database.getReference("usuarios_autorizados")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                users.clear()
                snapshot.children.forEach { child ->
                    users.add(UsuarioItem(child.key.toString(), child.child("nombre").value.toString(), child.child("rol").value.toString()))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(users) { user ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = BorderStroke(1.dp, if(user.rol == "Docente") MaterialTheme.colorScheme.primary else Color.DarkGray)
            ) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(user.nombre, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${user.rut} • ${user.rol}", color = Color.Gray, fontSize = 12.sp)
                    }
                    IconButton(onClick = {
                        database.getReference("usuarios_autorizados").child(user.rut).removeValue()
                        Toast.makeText(context, "Eliminado", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = Color(0xFFCF6679))
                    }
                }
            }
        }
    }
}

@Composable
fun AddUserView() {
    var newName by remember { mutableStateOf("") }
    var newRut by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var isTeacher by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val database = Firebase.database

    Column {
        Spacer(modifier = Modifier.height(16.dp))
        AccessTextField(newName, { newName = it }, "Nombre Completo", Icons.Default.Person, MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        AccessTextField(newRut, { newRut = it }, "RUT (Identificador)", Icons.Default.Badge, MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        AccessTextField(newPin, { newPin = it }, "PIN", Icons.Default.Pin, MaterialTheme.colorScheme.primary, keyboardType = KeyboardType.NumberPassword)

        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isTeacher, onCheckedChange = { isTeacher = it })
            Text("¿Es Docente (Admin)?", color = Color.White)
        }
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val rutClean = newRut.replace(".", "").trim()
                val pinClean = newPin.trim()
                val nameClean = newName.trim()

                if (nameClean.isNotEmpty() && rutClean.isNotEmpty() && pinClean.isNotEmpty()) {
                    val userData = mapOf("nombre" to nameClean, "pin" to pinClean, "rol" to if (isTeacher) "Docente" else "Estudiante")
                    database.getReference("usuarios_autorizados").child(rutClean).setValue(userData)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Guardado", Toast.LENGTH_SHORT).show()
                            newName = ""; newRut = ""; newPin = ""; isTeacher = false
                        }
                } else {
                    Toast.makeText(context, "Complete todo", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("GUARDAR USUARIO")
        }
    }
}

// --- UTILS ---
@Composable
fun AccessTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector, activeColor: Color, isPassword: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, color = Color.Gray) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = activeColor) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = activeColor, unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray, cursorColor = activeColor),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
    )
}

@Composable
fun AdminLoginDialog(onDismiss: () -> Unit, onLogin: (String, String) -> Unit) {
    var rut by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Acceso Docente", color = Color.White) },
        text = { Column {
            OutlinedTextField(value = rut, onValueChange = { rut = it }, label = { Text("RUT Docente") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))
        }},
        containerColor = Color(0xFF1E1E1E),
        confirmButton = { Button(onClick = { onLogin(rut, pin) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("ENTRAR") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR", color = Color.Gray) } }
    )
}