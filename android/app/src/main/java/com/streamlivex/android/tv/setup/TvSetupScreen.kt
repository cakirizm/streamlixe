package com.streamlivex.android.tv.setup

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.streamlivex.android.tv.data.TvProviderConfig
import com.streamlivex.android.tv.data.XtreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "streamlivex_tv"
private const val KEY_SERVER = "xtream_server"
private const val KEY_USERNAME = "xtream_username"
private const val KEY_PASSWORD = "xtream_password"
private const val KEY_PROVIDER_NAME = "xtream_provider_name"

object TvProviderStorage {
    fun load(context: Context): TvProviderConfig? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val server = prefs.getString(KEY_SERVER, "").orEmpty().trim()
        val username = prefs.getString(KEY_USERNAME, "").orEmpty().trim()
        val password = prefs.getString(KEY_PASSWORD, "").orEmpty()
        val name =
            prefs.getString(KEY_PROVIDER_NAME, "Oynatma Listem")
                .orEmpty()
                .ifBlank { "Oynatma Listem" }

        if (
            server.isBlank() ||
            username.isBlank() ||
            password.isBlank()
        ) {
            return null
        }

        return TvProviderConfig(
            name = name,
            server = server,
            username = username,
            password = password,
        )
    }

    fun save(
        context: Context,
        provider: TvProviderConfig,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROVIDER_NAME, provider.name)
            .putString(KEY_SERVER, provider.server.trim())
            .putString(KEY_USERNAME, provider.username.trim())
            .putString(KEY_PASSWORD, provider.password)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}

private enum class SetupMode {
    Qr,
    Manual,
}

@Composable
fun TvSetupScreen(
    onConnected: (TvProviderConfig) -> Unit,
) {
    val context = LocalContext.current
    val xtreamClient = remember { XtreamClient() }
    val pairingClient = remember { TvPairingClient() }

    var mode by remember { mutableStateOf(SetupMode.Qr) }

    var pairSession by remember { mutableStateOf<TvPairSession?>(null) }
    var pairStatus by remember { mutableStateOf("QR kod hazırlanıyor…") }
    var pairError by remember { mutableStateOf("") }
    var pairRetry by remember { mutableIntStateOf(0) }
    var pairBusy by remember { mutableStateOf(false) }

    var providerName by remember { mutableStateOf("Oynatma Listem") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var manualStatus by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf("") }
    var manualBusy by remember { mutableStateOf(false) }

    suspend fun validate(
        provider: TvProviderConfig,
    ): Result<TvProviderConfig> =
        withContext(Dispatchers.IO) {
            xtreamClient
                .loadLiveLibrary(provider)
                .mapCatching { library ->
                    if (library.channels.isEmpty()) {
                        error(
                            "Hesap doğrulandı ancak canlı kanal bulunamadı.",
                        )
                    }
                    provider
                }
        }

    DisposableEffect(Unit) {
        onDispose {
            pairingClient.close()
        }
    }

    LaunchedEffect(
        mode,
        pairRetry,
    ) {
        if (mode != SetupMode.Qr) return@LaunchedEffect

        pairSession = null
        pairError = ""
        pairStatus = "QR kod hazırlanıyor…"
        pairBusy = true

        val session =
            withContext(Dispatchers.IO) {
                pairingClient.createSession()
            }.getOrElse { throwable ->
                pairBusy = false
                pairError =
                    throwable.message
                        ?: "QR kod oluşturulamadı."
                return@LaunchedEffect
            }

        pairSession = session
        pairBusy = false
        pairStatus = "Telefonunla QR kodu okut · Aynı Wi‑Fi ağına bağlı ol"

        while (mode == SetupMode.Qr) {
            delay(2_500)

            val result =
                withContext(Dispatchers.IO) {
                    pairingClient.checkStatus(session.code)
                }

            if (result.isFailure) {
                pairStatus =
                    result.exceptionOrNull()?.message
                        ?: "Bağlantı kontrol ediliyor…"
                continue
            }

            val status = result.getOrThrow()

            when (status) {
                TvPairStatus.Waiting -> {
                    pairStatus =
                        "Telefondan yerel ağ bağlantısı bekleniyor…"
                }

                TvPairStatus.Expired -> {
                    pairStatus = ""
                    pairError =
                        "QR kodun süresi doldu."
                    return@LaunchedEffect
                }

                is TvPairStatus.Ready -> {
                    pairBusy = true
                    pairError = ""
                    pairStatus =
                        "Telefon eşleşti. Hesap doğrulanıyor…"

                    val validated =
                        validate(status.provider)

                    validated
                        .onFailure { throwable ->
                            pairBusy = false
                            pairStatus = ""
                            pairError =
                                throwable.message
                                    ?: "Xtream hesabına bağlanılamadı."
                        }
                        .onSuccess { provider ->
                            TvProviderStorage.save(
                                context,
                                provider,
                            )
                            pairStatus = "Bağlandı."
                            onConnected(provider)
                        }

                    return@LaunchedEffect
                }
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFF080B12)),
        contentAlignment =
            Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 64.dp),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(40.dp),
        ) {
            Column(
                modifier = Modifier.weight(0.78f),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "StreamLiveX",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style =
                        MaterialTheme
                            .typography
                            .displaySmall,
                )

                Text(
                    "TV Oynatıcı",
                    color = Color(0xFF60A5FA),
                    fontWeight =
                        FontWeight.SemiBold,
                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    if (mode == SetupMode.Qr) {
                        "Telefonla bağlan"
                    } else {
                        "Xtream hesabını bağla"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style =
                        MaterialTheme
                            .typography
                            .headlineMedium,
                )

                Text(
                    if (mode == SetupMode.Qr) {
                        "QR kodu telefonunla okut. Telefon ve TV aynı Wi‑Fi / yerel ağda olmalı. Bilgiler doğrudan TV'ye aktarılır."
                    } else {
                        "Xtream Codes bilgilerini kumandayla manuel olarak gir."
                    },
                    color = Color(0xFF94A3B8),
                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge,
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp),
                ) {
                    SetupTab(
                        title = "QR ile Bağlan",
                        selected =
                            mode == SetupMode.Qr,
                    ) {
                        mode = SetupMode.Qr
                    }

                    SetupTab(
                        title = "Xtream Codes",
                        selected =
                            mode == SetupMode.Manual,
                    ) {
                        mode = SetupMode.Manual
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .weight(1.22f)
                        .background(
                            Color(0xFF111827),
                            RoundedCornerShape(18.dp),
                        )
                        .padding(26.dp),
            ) {
                if (mode == SetupMode.Qr) {
                    QrPanel(
                        session = pairSession,
                        status = pairStatus,
                        error = pairError,
                        busy = pairBusy,
                        onRetry = {
                            pairRetry += 1
                        },
                    )
                } else {
                    Column(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalArrangement =
                            Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Xtream Codes",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style =
                                MaterialTheme
                                    .typography
                                    .headlineSmall,
                        )

                        OutlinedTextField(
                            value = providerName,
                            onValueChange = {
                                providerName = it
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            label = {
                                Text("Liste adı")
                            },
                            singleLine = true,
                            enabled = !manualBusy,
                        )

                        OutlinedTextField(
                            value = server,
                            onValueChange = {
                                server = it
                                manualError = ""
                                manualStatus = ""
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            label = {
                                Text("Sunucu adresi")
                            },
                            placeholder = {
                                Text(
                                    "http://sunucu.com:8080",
                                )
                            },
                            singleLine = true,
                            enabled = !manualBusy,
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                manualError = ""
                                manualStatus = ""
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            label = {
                                Text("Kullanıcı adı")
                            },
                            singleLine = true,
                            enabled = !manualBusy,
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                manualError = ""
                                manualStatus = ""
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            label = {
                                Text("Şifre")
                            },
                            visualTransformation =
                                PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !manualBusy,
                        )

                        if (manualBusy) {
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                horizontalArrangement =
                                    Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    manualStatus
                                        .ifBlank {
                                            "Xtream hesabına bağlanılıyor…"
                                        },
                                    color =
                                        Color(0xFFCBD5E1),
                                )
                            }
                        }

                        if (manualError.isNotBlank()) {
                            Text(
                                manualError,
                                color = Color(0xFFF87171),
                            )
                        }

                        Button(
                            onClick = {
                                val cleanServer =
                                    server
                                        .trim()
                                        .trimEnd('/')

                                val cleanUsername =
                                    username.trim()

                                when {
                                    cleanServer.isBlank() -> {
                                        manualError =
                                            "Sunucu adresini gir."
                                    }

                                    !cleanServer.startsWith(
                                        "http://",
                                    ) &&
                                        !cleanServer.startsWith(
                                            "https://",
                                        ) -> {
                                        manualError =
                                            "Sunucu adresi http:// veya https:// ile başlamalı."
                                    }

                                    cleanUsername.isBlank() -> {
                                        manualError =
                                            "Kullanıcı adını gir."
                                    }

                                    password.isBlank() -> {
                                        manualError =
                                            "Şifreyi gir."
                                    }

                                    else -> {
                                        val provider =
                                            TvProviderConfig(
                                                name =
                                                    providerName
                                                        .trim()
                                                        .ifBlank {
                                                            "Oynatma Listem"
                                                        },
                                                server =
                                                    cleanServer,
                                                username =
                                                    cleanUsername,
                                                password =
                                                    password,
                                            )

                                        manualBusy = true
                                        manualError = ""
                                        manualStatus =
                                            "Hesap doğrulanıyor…"

                                        Thread {
                                            val result =
                                                xtreamClient
                                                    .loadLiveLibrary(
                                                        provider,
                                                    )

                                            android.os.Handler(
                                                android.os.Looper
                                                    .getMainLooper(),
                                            ).post {
                                                manualBusy = false

                                                result
                                                    .onFailure {
                                                        manualStatus =
                                                            ""
                                                        manualError =
                                                            it.message
                                                                ?: "Xtream hesabına bağlanılamadı."
                                                    }
                                                    .onSuccess { library ->
                                                        if (
                                                            library.channels
                                                                .isEmpty()
                                                        ) {
                                                            manualError =
                                                                "Hesap doğrulandı ancak canlı kanal bulunamadı."
                                                            return@onSuccess
                                                        }

                                                        TvProviderStorage
                                                            .save(
                                                                context,
                                                                provider,
                                                            )

                                                        onConnected(
                                                            provider,
                                                        )
                                                    }
                                            }
                                        }.start()
                                    }
                                }
                            },
                            enabled = !manualBusy,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                            colors =
                                ButtonDefaults
                                    .buttonColors(
                                        containerColor =
                                            Color(
                                                0xFF2563EB,
                                            ),
                                    ),
                        ) {
                            Text(
                                if (manualBusy) {
                                    "Bağlanıyor…"
                                } else {
                                    "Bağlan"
                                },
                                fontWeight =
                                    FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by
        remember {
            mutableStateOf(false)
        }

    Box(
        modifier =
            Modifier
                .background(
                    if (
                        focused
                    ) {
                        Color(0xFF2563EB)
                    } else if (
                        selected
                    ) {
                        Color(0xFF1D4ED8)
                    } else {
                        Color(0xFF172033)
                    },
                    RoundedCornerShape(9.dp),
                )
                .onFocusChanged {
                    focused = it.isFocused
                }
                .clickable(
                    onClick = onClick,
                )
                .padding(
                    horizontal = 16.dp,
                    vertical = 11.dp,
                ),
    ) {
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun QrPanel(
    session: TvPairSession?,
    status: String,
    error: String,
    busy: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "QR ile Bağlan",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style =
                MaterialTheme
                    .typography
                    .headlineSmall,
        )

        if (session != null) {
            Box(
                modifier =
                    Modifier
                        .size(250.dp)
                        .background(
                            Color.White,
                            RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                contentAlignment =
                    Alignment.Center,
            ) {
                QrImage(
                    value = session.pairUrl,
                    size = 480,
                )
            }

            Text(
                session.code,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
            )

            Text(
                session.pairUrl,
                color = Color(0xFF60A5FA),
                textAlign = TextAlign.Center,
            )
        } else if (busy) {
            CircularProgressIndicator()
        }

        if (status.isNotBlank()) {
            Text(
                status,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center,
            )
        }

        if (error.isNotBlank()) {
            Text(
                error,
                color = Color(0xFFF87171),
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = onRetry,
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                Color(0xFF2563EB),
                        ),
            ) {
                Text("Yeni QR Oluştur")
            }
        }
    }
}

@Composable
private fun QrImage(
    value: String,
    size: Int,
) {
    val bitmap =
        remember(
            value,
            size,
        ) {
            createQrBitmap(
                value,
                size,
            )
        }

    Image(
        bitmap =
            bitmap.asImageBitmap(),
        contentDescription =
            "StreamLiveX eşleştirme QR kodu",
        modifier =
            Modifier.fillMaxSize(),
    )
}

private fun createQrBitmap(
    value: String,
    size: Int,
): Bitmap {
    val matrix =
        QRCodeWriter()
            .encode(
                value,
                BarcodeFormat.QR_CODE,
                size,
                size,
            )

    val pixels =
        IntArray(size * size)

    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] =
                if (matrix.get(x, y)) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.WHITE
                }
        }
    }

    return Bitmap
        .createBitmap(
            pixels,
            size,
            size,
            Bitmap.Config.RGB_565,
        )
}
