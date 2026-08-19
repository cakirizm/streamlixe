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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    fun load(
        context: Context,
    ): TvProviderConfig? {
        val prefs =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )

        val server =
            prefs
                .getString(
                    KEY_SERVER,
                    "",
                )
                .orEmpty()
                .trim()

        val username =
            prefs
                .getString(
                    KEY_USERNAME,
                    "",
                )
                .orEmpty()
                .trim()

        val password =
            prefs
                .getString(
                    KEY_PASSWORD,
                    "",
                )
                .orEmpty()

        val name =
            prefs
                .getString(
                    KEY_PROVIDER_NAME,
                    "Oynatma Listem",
                )
                .orEmpty()
                .ifBlank {
                    "Oynatma Listem"
                }

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
        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )
            .edit()
            .putString(
                KEY_PROVIDER_NAME,
                provider.name,
            )
            .putString(
                KEY_SERVER,
                provider.server.trim(),
            )
            .putString(
                KEY_USERNAME,
                provider.username.trim(),
            )
            .putString(
                KEY_PASSWORD,
                provider.password,
            )
            .apply()
    }

    fun clear(
        context: Context,
    ) {
        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )
            .edit()
            .clear()
            .apply()
    }
}

private enum class TvSetupMode {
    Qr,
    Manual,
}

@Composable
fun TvSetupScreen(
    onConnected: (TvProviderConfig) -> Unit,
) {
    val context = LocalContext.current
    val pairingClient =
        remember {
            TvPairingClient()
        }

    val xtreamClient =
        remember {
            XtreamClient()
        }

    var mode by
        remember {
            mutableStateOf(
                TvSetupMode.Qr,
            )
        }

    var pairSession by
        remember {
            mutableStateOf<TvPairSession?>(
                null,
            )
        }

    var pairError by
        remember {
            mutableStateOf("")
        }

    var pairStatus by
        remember {
            mutableStateOf(
                "QR kod hazırlanıyor...",
            )
        }

    var pairRetry by
        remember {
            mutableIntStateOf(0)
        }

    var pairBusy by
        remember {
            mutableStateOf(false)
        }

    var providerName by
        remember {
            mutableStateOf(
                "Oynatma Listem",
            )
        }

    var server by
        remember {
            mutableStateOf("")
        }

    var username by
        remember {
            mutableStateOf("")
        }

    var password by
        remember {
            mutableStateOf("")
        }

    var manualError by
        remember {
            mutableStateOf("")
        }

    var manualStatus by
        remember {
            mutableStateOf("")
        }

    var manualBusy by
        remember {
            mutableStateOf(false)
        }

    var manualPendingProvider by
        remember {
            mutableStateOf<TvProviderConfig?>(
                null,
            )
        }

    suspend fun validateAndConnect(
        provider: TvProviderConfig,
        onStatus: (String) -> Unit,
    ): Result<TvProviderConfig> {
        onStatus(
            "Hesap doğrulanıyor ve canlı kanallar kontrol ediliyor...",
        )

        return withContext(
            Dispatchers.IO,
        ) {
            xtreamClient
                .loadLiveLibrary(
                    provider,
                )
                .map { library ->
                    if (
                        library.channels.isEmpty()
                    ) {
                        throw IllegalStateException(
                            "Hesap doğrulandı ancak canlı kanal bulunamadı.",
                        )
                    }

                    provider
                }
        }
    }

    /*
     * QR ekranı açıldığında oturum otomatik oluşturulur.
     * Web tarafındaki mevcut StreamLiveX pairing API ile aynı akışı kullanır:
     * POST /api/pair { action:create }
     * GET /api/pair?code=...
     */
    LaunchedEffect(
        mode,
        pairRetry,
    ) {
        if (
            mode != TvSetupMode.Qr
        ) {
            return@LaunchedEffect
        }

        pairBusy = true
        pairError = ""
        pairSession = null
        pairStatus =
            "QR kod hazırlanıyor..."

        val createResult =
            withContext(
                Dispatchers.IO,
            ) {
                pairingClient
                    .createSession()
            }

        val session =
            createResult
                .getOrElse { throwable ->
                    pairBusy = false
                    pairError =
                        throwable.message
                            ?: "QR kod oluşturulamadı."
                    return@LaunchedEffect
                }

        pairSession = session
        pairBusy = false
        pairStatus =
            "Telefonunla QR kodu okut"

        while (
            mode == TvSetupMode.Qr
        ) {
            delay(
                2_500,
            )

            val statusResult =
                withContext(
                    Dispatchers.IO,
                ) {
                    pairingClient
                        .checkStatus(
                            session.code,
                        )
                }

            statusResult
                .onFailure { throwable ->
                    /*
                     * Geçici ağ hatasında QR'ı hemen bozma.
                     * Kullanıcı aynı QR üzerinde kalmaya devam etsin.
                     */
                    pairStatus =
                        throwable.message
                            ?: "Eşleştirme kontrol ediliyor..."
                }
                .onSuccess { status ->
                    when (
                        status
                    ) {
                        TvPairStatus.Waiting -> {
                            pairStatus =
                                "Telefondan bağlantı bekleniyor..."
                        }

                        TvPairStatus.Expired -> {
                            pairError =
                                "QR kodun süresi doldu."
                            pairStatus = ""
                            return@LaunchedEffect
                        }

                        is TvPairStatus.Ready -> {
                            pairBusy = true
                            pairStatus =
                                "Telefon eşleşti. IPTV hesabı doğrulanıyor..."
                            pairError = ""

                            val validation =
                                validateAndConnect(
                                    provider =
                                        status.provider,
                                    onStatus = {
                                        pairStatus =
                                            it
                                    },
                                )

                            validation
                                .onFailure { throwable ->
                                    pairBusy = false
                                    pairError =
                                        throwable.message
                                            ?: "Xtream hesabına bağlanılamadı."
                                }
                                .onSuccess { provider ->
                                    TvProviderStorage
                                        .save(
                                            context = context,
                                            provider = provider,
                                        )

                                    pairStatus =
                                        "Bağlandı."

                                    onConnected(
                                        provider,
                                    )

                                    return@LaunchedEffect
                                }

                            return@LaunchedEffect
                        }
                    }
                }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(
                        0xFF080B12,
                    ),
                ),
        contentAlignment =
            Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal =
                            64.dp,
                    ),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(
                    42.dp,
                ),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(
                            0.78f,
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        12.dp,
                    ),
            ) {
                Text(
                    text =
                        "StreamLiveX",
                    color =
                        Color.White,
                    fontWeight =
                        FontWeight.Bold,
                    style =
                        MaterialTheme
                            .typography
                            .displaySmall,
                )

                Text(
                    text =
                        "TV Oynatıcı",
                    color =
                        Color(
                            0xFF60A5FA,
                        ),
                    fontWeight =
                        FontWeight.SemiBold,
                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall,
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            8.dp,
                        ),
                )

                Text(
                    text =
                        if (
                            mode ==
                            TvSetupMode.Qr
                        ) {
                            "Telefonla bağlan"
                        } else {
                            "Xtream hesabını bağla"
                        },
                    color =
                        Color.White,
                    fontWeight =
                        FontWeight.Bold,
                    style =
                        MaterialTheme
                            .typography
                            .headlineMedium,
                )

                Text(
                    text =
                        if (
                            mode ==
                            TvSetupMode.Qr
                        ) {
                            "QR kodu telefonunla okut. Oynatma listesi bilgilerini TV kumandasıyla yazmadan StreamLiveX üzerinden bu TV'ye aktar."
                        } else {
                            "İstersen Xtream Codes bilgilerini kumandayla manuel olarak da girebilirsin."
                        },
                    color =
                        Color(
                            0xFF94A3B8,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge,
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp,
                        ),
                ) {
                    SetupTab(
                        title =
                            "QR ile Bağlan",
                        selected =
                            mode ==
                                TvSetupMode.Qr,
                        onClick = {
                            mode =
                                TvSetupMode.Qr
                        },
                    )

                    SetupTab(
                        title =
                            "Xtream Codes",
                        selected =
                            mode ==
                                TvSetupMode.Manual,
                        onClick = {
                            mode =
                                TvSetupMode.Manual
                        },
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .weight(
                            1.22f,
                        )
                        .background(
                            color =
                                Color(
                                    0xFF111827,
                                ),
                            shape =
                                RoundedCornerShape(
                                    18.dp,
                                ),
                        )
                        .padding(
                            26.dp,
                        ),
            ) {
                when (
                    mode
                ) {
                    TvSetupMode.Qr -> {
                        QrSetupPanel(
                            session =
                                pairSession,
                            busy =
                                pairBusy,
                            status =
                                pairStatus,
                            error =
                                pairError,
                            onRetry = {
                                pairRetry += 1
                            },
                        )
                    }

                    TvSetupMode.Manual -> {
                        ManualSetupPanel(
                            providerName =
                                providerName,
                            server =
                                server,
                            username =
                                username,
                            password =
                                password,
                            busy =
                                manualBusy,
                            status =
                                manualStatus,
                            error =
                                manualError,
                            onProviderNameChanged = {
                                providerName =
                                    it
                            },
                            onServerChanged = {
                                server =
                                    it
                                manualError =
                                    ""
                                manualStatus =
                                    ""
                            },
                            onUsernameChanged = {
                                username =
                                    it
                                manualError =
                                    ""
                                manualStatus =
                                    ""
                            },
                            onPasswordChanged = {
                                password =
                                    it
                                manualError =
                                    ""
                                manualStatus =
                                    ""
                            },
                            onConnect = {
                                val cleanServer =
                                    server
                                        .trim()
                                        .trimEnd(
                                            '/',
                                        )

                                val cleanUsername =
                                    username
                                        .trim()

                                when {
                                    cleanServer
                                        .isBlank() -> {
                                        manualError =
                                            "Sunucu adresini gir."
                                    }

                                    !cleanServer
                                        .startsWith(
                                            "http://",
                                        ) &&
                                        !cleanServer
                                            .startsWith(
                                                "https://",
                                            ) -> {
                                        manualError =
                                            "Sunucu adresi http:// veya https:// ile başlamalı."
                                    }

                                    cleanUsername
                                        .isBlank() -> {
                                        manualError =
                                            "Kullanıcı adını gir."
                                    }

                                    password
                                        .isBlank() -> {
                                        manualError =
                                            "Şifreyi gir."
                                    }

                                    else -> {
                                        manualBusy =
                                            true
                                        manualError =
                                            ""

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

                                        /*
                                         * Manuel buton bir coroutine başlatacak state ile tetiklenir.
                                         */
                                        manualPendingProvider =
                                            provider
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    val pendingProvider =
        manualPendingProvider

    LaunchedEffect(
        pendingProvider,
    ) {
        val provider =
            pendingProvider
                ?: return@LaunchedEffect

        manualStatus =
            "Hesap doğrulanıyor..."

        val result =
            validateAndConnect(
                provider = provider,
                onStatus = {
                    manualStatus =
                        it
                },
            )

        manualPendingProvider =
            null
        manualBusy =
            false

        result
            .onFailure { throwable ->
                manualError =
                    throwable.message
                        ?: "Xtream hesabına bağlanılamadı."
                manualStatus =
                    ""
            }
            .onSuccess {
                TvProviderStorage.save(
                    context = context,
                    provider = provider,
                )

                onConnected(
                    provider,
                )
            }
    }
}

@Composable
private fun SetupTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .background(
                    color =
                        if (
                            selected
                        ) {
                            Color(
                                0xFF2563EB,
                            )
                        } else {
                            Color(
                                0xFF172033,
                            )
                        },
                    shape =
                        RoundedCornerShape(
                            9.dp,
                        ),
                )
                .clickable(
                    onClick =
                        onClick,
                )
                .padding(
                    horizontal =
                        16.dp,
                    vertical =
                        11.dp,
                ),
    ) {
        Text(
            text =
                title,
            color =
                Color.White,
            fontWeight =
                FontWeight.Bold,
        )
    }
}

@Composable
private fun QrSetupPanel(
    session: TvPairSession?,
    busy: Boolean,
    status: String,
    error: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        Text(
            text =
                "QR ile Bağlan",
            color =
                Color.White,
            fontWeight =
                FontWeight.Bold,
            style =
                MaterialTheme
                    .typography
                    .headlineSmall,
        )

        Text(
            text =
                "Telefon kameranla QR kodu okut",
            color =
                Color(
                    0xFF94A3B8,
                ),
        )

        if (
            session != null
        ) {
            Box(
                modifier =
                    Modifier
                        .size(
                            250.dp,
                        )
                        .background(
                            Color.White,
                            RoundedCornerShape(
                                12.dp,
                            ),
                        )
                        .padding(
                            12.dp,
                        ),
                contentAlignment =
                    Alignment.Center,
            ) {
                QrImage(
                    value =
                        session.pairUrl,
                    size =
                        470,
                )
            }

            Text(
                text =
                    session.code,
                color =
                    Color.White,
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                textAlign =
                    TextAlign.Center,
            )

            Text(
                text =
                    "streamlivex.com/pair/${session.code}",
                color =
                    Color(
                        0xFF60A5FA,
                    ),
                textAlign =
                    TextAlign.Center,
            )
        } else if (
            busy
        ) {
            CircularProgressIndicator()
        }

        if (
            status.isNotBlank()
        ) {
            Text(
                text =
                    status,
                color =
                    Color(
                        0xFFCBD5E1,
                    ),
                textAlign =
                    TextAlign.Center,
            )
        }

        if (
            error.isNotBlank()
        ) {
            Text(
                text =
                    error,
                color =
                    Color(
                        0xFFF87171,
                    ),
                textAlign =
                    TextAlign.Center,
            )

            Button(
                onClick =
                    onRetry,
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
                    "Yeni QR Oluştur",
                )
            }
        }
    }
}

@Composable
private fun ManualSetupPanel(
    providerName: String,
    server: String,
    username: String,
    password: String,
    busy: Boolean,
    status: String,
    error: String,
    onProviderNameChanged: (String) -> Unit,
    onServerChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        Text(
            text =
                "Xtream Codes",
            color =
                Color.White,
            fontWeight =
                FontWeight.Bold,
            style =
                MaterialTheme
                    .typography
                    .headlineSmall,
        )

        OutlinedTextField(
            value =
                providerName,
            onValueChange =
                onProviderNameChanged,
            modifier =
                Modifier.fillMaxWidth(),
            label = {
                Text(
                    "Liste adı",
                )
            },
            singleLine =
                true,
            enabled =
                !busy,
        )

        OutlinedTextField(
            value =
                server,
            onValueChange =
                onServerChanged,
            modifier =
                Modifier.fillMaxWidth(),
            label = {
                Text(
                    "Sunucu adresi",
                )
            },
            placeholder = {
                Text(
                    "http://sunucu.com:8080",
                )
            },
            singleLine =
                true,
            enabled =
                !busy,
        )

        OutlinedTextField(
            value =
                username,
            onValueChange =
                onUsernameChanged,
            modifier =
                Modifier.fillMaxWidth(),
            label = {
                Text(
                    "Kullanıcı adı",
                )
            },
            singleLine =
                true,
            enabled =
                !busy,
        )

        OutlinedTextField(
            value =
                password,
            onValueChange =
                onPasswordChanged,
            modifier =
                Modifier.fillMaxWidth(),
            label = {
                Text(
                    "Şifre",
                )
            },
            visualTransformation =
                PasswordVisualTransformation(),
            singleLine =
                true,
            enabled =
                !busy,
        )

        if (
            busy
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp,
                    ),
            ) {
                CircularProgressIndicator()

                Text(
                    text =
                        status.ifBlank {
                            "Xtream hesabına bağlanılıyor..."
                        },
                    color =
                        Color(
                            0xFFCBD5E1,
                        ),
                )
            }
        }

        if (
            error.isNotBlank()
        ) {
            Text(
                text =
                    error,
                color =
                    Color(
                        0xFFF87171,
                    ),
            )
        }

        Button(
            onClick =
                onConnect,
            enabled =
                !busy,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        54.dp,
                    ),
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
                text =
                    if (
                        busy
                    ) {
                        "Bağlanıyor..."
                    } else {
                        "Bağlan"
                    },
                fontWeight =
                    FontWeight.Bold,
            )
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
                value =
                    value,
                size =
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
        IntArray(
            size * size,
        )

    for (
        y in
        0 until size
    ) {
        for (
            x in
            0 until size
        ) {
            pixels[
                y * size +
                    x
            ] =
                if (
                    matrix[
                        x,
                        y,
                    ]
                ) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.WHITE
                }
        }
    }

    return Bitmap
        .createBitmap(
            size,
            size,
            Bitmap.Config.RGB_565,
        )
        .apply {
            setPixels(
                pixels,
                0,
                size,
                0,
                0,
                size,
                size,
            )
        }
}
