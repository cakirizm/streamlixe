package com.streamlivex.android.tv.setup

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.streamlivex.android.tv.data.TvProviderConfig

private const val PREFS_NAME = "streamlivex_tv"
private const val KEY_SERVER = "xtream_server"
private const val KEY_USERNAME = "xtream_username"
private const val KEY_PASSWORD = "xtream_password"
private const val KEY_PROVIDER_NAME = "xtream_provider_name"

object TvProviderStorage {

    fun load(context: Context): TvProviderConfig? {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

        val server = prefs
            .getString(KEY_SERVER, "")
            .orEmpty()
            .trim()

        val username = prefs
            .getString(KEY_USERNAME, "")
            .orEmpty()
            .trim()

        val password = prefs
            .getString(KEY_PASSWORD, "")
            .orEmpty()

        val name = prefs
            .getString(KEY_PROVIDER_NAME, "Oynatma Listem")
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

    fun clear(context: Context) {
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

@Composable
fun TvSetupScreen(
    onConnected: (TvProviderConfig) -> Unit,
) {
    val context = LocalContext.current

    var providerName by remember {
        mutableStateOf("Oynatma Listem")
    }

    var server by remember {
        mutableStateOf("")
    }

    var username by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var error by remember {
        mutableStateOf("")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080B12)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(0.85f)
                    .padding(end = 54.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "StreamLiveX",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.displaySmall,
                )

                Text(
                    text = "TV Oynatıcı",
                    color = Color(0xFF60A5FA),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.headlineSmall,
                )

                Spacer(
                    modifier = Modifier.height(8.dp),
                )

                Text(
                    text = "IPTV hesabını bağla",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium,
                )

                Text(
                    text = "Xtream Codes hesabını bir kez ekle. StreamLiveX bilgilerini bu TV'de saklar ve sonraki açılışlarda otomatik yükler.",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1.15f)
                    .background(
                        color = Color(0xFF111827),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Xtream Codes",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                )

                OutlinedTextField(
                    value = providerName,
                    onValueChange = {
                        providerName = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Liste adı")
                    },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = server,
                    onValueChange = {
                        server = it
                        error = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Sunucu adresi")
                    },
                    placeholder = {
                        Text("http://sunucu.com:8080")
                    },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        error = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Kullanıcı adı")
                    },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Şifre")
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    singleLine = true,
                )

                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        color = Color(0xFFF87171),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(
                    modifier = Modifier.height(4.dp),
                )

                Button(
                    onClick = {
                        val cleanServer =
                            server.trim().trimEnd('/')

                        val cleanUsername =
                            username.trim()

                        when {
                            cleanServer.isBlank() -> {
                                error =
                                    "Sunucu adresini gir."
                            }

                            !cleanServer.startsWith("http://") &&
                                !cleanServer.startsWith("https://") -> {
                                error =
                                    "Sunucu adresi http:// veya https:// ile başlamalı."
                            }

                            cleanUsername.isBlank() -> {
                                error =
                                    "Kullanıcı adını gir."
                            }

                            password.isBlank() -> {
                                error =
                                    "Şifreyi gir."
                            }

                            else -> {
                                val provider =
                                    TvProviderConfig(
                                        name = providerName
                                            .trim()
                                            .ifBlank {
                                                "Oynatma Listem"
                                            },
                                        server = cleanServer,
                                        username =
                                            cleanUsername,
                                        password =
                                            password,
                                    )

                                TvProviderStorage.save(
                                    context = context,
                                    provider = provider,
                                )

                                onConnected(provider)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            Color(0xFF2563EB),
                    ),
                ) {
                    Text(
                        text = "Bağlan",
                        fontWeight = FontWeight.Bold,
                    )
                }

                TextButton(
                    onClick = {
                        server = ""
                        username = ""
                        password = ""
                        error = ""
                    },
                    modifier = Modifier.align(
                        Alignment.CenterHorizontally,
                    ),
                ) {
                    Text("Alanları temizle")
                }
            }
        }
    }
}
