package com.streamlivex.android.tv.profile

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun TvProfileSelectScreen(
    onSelected: (TvProfile) -> Unit,
    onManage: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val store =
        remember {
            TvProfileStore(context)
        }
    var profiles by
        remember {
            mutableStateOf(
                store.all(),
            )
        }
    var pending by
        remember {
            mutableStateOf<TvProfile?>(
                null,
            )
        }
    var pin by
        remember {
            mutableStateOf("")
        }
    var error by
        remember {
            mutableStateOf("")
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(0xFF080B12),
                )
                .padding(42.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize(),
            verticalArrangement =
                Arrangement.spacedBy(
                    24.dp,
                ),
        ) {
            Text(
                "Kim izliyor?",
                color = Color.White,
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme
                        .typography
                        .displaySmall,
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        18.dp,
                    ),
            ) {
                profiles.forEach {
                    profile ->

                    ProfileCard(
                        profile = profile,
                    ) {
                        if (
                            profile.pinHash ==
                            null
                        ) {
                            onSelected(
                                profile,
                            )
                        } else {
                            pending =
                                profile
                            pin = ""
                            error = ""
                        }
                    }
                }
            }

            onManage?.let {
                ProfileButton(
                    text =
                        "Profilleri Yönet",
                    onClick = it,
                )
            }
        }

        if (pending != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Color(
                                0xCC000000,
                            ),
                        ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Column(
                    modifier =
                        Modifier
                            .width(420.dp)
                            .background(
                                Color(
                                    0xFF111827,
                                ),
                                RoundedCornerShape(
                                    16.dp,
                                ),
                            )
                            .padding(24.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            14.dp,
                        ),
                ) {
                    Text(
                        "${pending!!.avatar} ${pending!!.name}",
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
                        "4 haneli PIN",
                        color =
                            Color(
                                0xFF94A3B8,
                            ),
                    )

                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            value ->

                            pin =
                                value
                                    .filter {
                                        it.isDigit()
                                    }
                                    .take(4)
                            error = ""
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        singleLine = true,
                        visualTransformation =
                            PasswordVisualTransformation(),
                    )

                    if (error.isNotBlank()) {
                        Text(
                            error,
                            color =
                                Color(
                                    0xFFF87171,
                                ),
                        )
                    }

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                10.dp,
                            ),
                    ) {
                        ProfileButton(
                            text = "Geri",
                        ) {
                            pending =
                                null
                        }

                        ProfileButton(
                            text = "Aç",
                            selected =
                                pin.length ==
                                    4,
                        ) {
                            val row =
                                pending!!
                            if (
                                store.verifyPin(
                                    row,
                                    pin,
                                )
                            ) {
                                onSelected(
                                    row,
                                )
                            } else {
                                error =
                                    "PIN yanlış."
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvProfileManagerScreen(
    onBack: () -> Unit,
) {
    BackHandler(
        onBack = onBack,
    )

    val context =
        LocalContext.current
    val store =
        remember {
            TvProfileStore(
                context,
            )
        }

    var rows by
        remember {
            mutableStateOf(
                store.all(),
            )
        }
    var adding by
        remember {
            mutableStateOf(false)
        }

    if (adding) {
        TvCreateProfileScreen(
            onBack = {
                adding = false
            },
            onCreated = {
                rows = store.all()
                adding = false
            },
        )
        return
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(0xFF0D111B),
                )
                .padding(30.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                14.dp,
            ),
    ) {
        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp,
                    ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                ProfileButton(
                    "← Geri",
                    onClick = onBack,
                )
                Text(
                    "Profilleri Yönet",
                    color =
                        Color.White,
                    fontWeight =
                        FontWeight.Bold,
                    style =
                        MaterialTheme
                            .typography
                            .headlineMedium,
                )
            }
        }

        items(
            count = rows.size,
            key = {
                rows[it].id
            },
        ) {
            index ->

            val profile =
                rows[index]

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Color(
                                0xFF151C28,
                            ),
                            RoundedCornerShape(
                                12.dp,
                            ),
                        )
                        .padding(16.dp),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(
                        14.dp,
                    ),
            ) {
                Text(
                    profile.avatar,
                    style =
                        MaterialTheme
                            .typography
                            .headlineLarge,
                )

                Column(
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                ) {
                    Text(
                        profile.name,
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                    )

                    Text(
                        buildString {
                            if (
                                profile.isKids
                            ) {
                                append(
                                    "Çocuk Profili",
                                )
                            } else {
                                append(
                                    "Standart Profil",
                                )
                            }
                            if (
                                profile.pinHash !=
                                null
                            ) {
                                append(
                                    " · PIN korumalı",
                                )
                            }
                        },
                        color =
                            Color(
                                0xFF94A3B8,
                            ),
                    )
                }

                if (rows.size > 1) {
                    ProfileButton(
                        "Sil",
                    ) {
                        store.remove(
                            profile.id,
                        )
                        rows =
                            store.all()
                    }
                }
            }
        }

        item {
            ProfileButton(
                "＋ Yeni Profil",
            ) {
                adding = true
            }
        }
    }
}

@Composable
private fun TvCreateProfileScreen(
    onBack: () -> Unit,
    onCreated: (TvProfile) -> Unit,
) {
    BackHandler(
        onBack = onBack,
    )

    val context =
        LocalContext.current
    val store =
        remember {
            TvProfileStore(
                context,
            )
        }

    var name by
        remember {
            mutableStateOf("")
        }
    var avatar by
        remember {
            mutableStateOf("🙂")
        }
    var kids by
        remember {
            mutableStateOf(false)
        }
    var pinEnabled by
        remember {
            mutableStateOf(false)
        }
    var pin by
        remember {
            mutableStateOf("")
        }
    var error by
        remember {
            mutableStateOf("")
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(0xFF0D111B),
                )
                .padding(32.dp),
        verticalArrangement =
            Arrangement.spacedBy(
                14.dp,
            ),
    ) {
        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    12.dp,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            ProfileButton(
                "← Geri",
                onClick = onBack,
            )
            Text(
                "Yeni Profil",
                color = Color.White,
                fontWeight =
                    FontWeight.Bold,
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
            )
        }

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                error = ""
            },
            label = {
                Text("Profil adı")
            },
            modifier =
                Modifier.width(
                    430.dp,
                ),
            singleLine = true,
        )

        Text(
            "Avatar",
            color = Color.White,
            fontWeight =
                FontWeight.Bold,
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    10.dp,
                ),
        ) {
            listOf(
                "🙂",
                "😎",
                "👩",
                "👨",
                "🧒",
                "🦊",
            ).forEach {
                item ->

                ProfileButton(
                    text = item,
                    selected =
                        avatar == item,
                ) {
                    avatar = item
                }
            }
        }

        ProfileButton(
            text =
                if (kids) {
                    "Çocuk Profili: Açık"
                } else {
                    "Çocuk Profili: Kapalı"
                },
            selected = kids,
        ) {
            kids = !kids
            if (
                kids &&
                avatar == "🙂"
            ) {
                avatar = "🧒"
            }
        }

        ProfileButton(
            text =
                if (pinEnabled) {
                    "PIN: Açık"
                } else {
                    "PIN: Kapalı"
                },
            selected =
                pinEnabled,
        ) {
            pinEnabled =
                !pinEnabled
            if (!pinEnabled) {
                pin = ""
            }
        }

        if (pinEnabled) {
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin =
                        it.filter {
                            ch ->
                            ch.isDigit()
                        }.take(4)
                    error = ""
                },
                label = {
                    Text(
                        "4 haneli PIN",
                    )
                },
                visualTransformation =
                    PasswordVisualTransformation(),
                modifier =
                    Modifier.width(
                        430.dp,
                    ),
                singleLine = true,
            )
        }

        if (error.isNotBlank()) {
            Text(
                error,
                color =
                    Color(
                        0xFFF87171,
                    ),
            )
        }

        ProfileButton(
            "Profili Oluştur",
            selected =
                name.isNotBlank(),
        ) {
            if (name.isBlank()) {
                error =
                    "Profil adı gerekli."
                return@ProfileButton
            }

            if (
                pinEnabled &&
                pin.length != 4
            ) {
                error =
                    "PIN 4 haneli olmalı."
                return@ProfileButton
            }

            val row =
                store.add(
                    name = name,
                    avatar = avatar,
                    isKids = kids,
                    pin =
                        if (
                            pinEnabled
                        ) {
                            pin
                        } else {
                            null
                        },
                )
            onCreated(row)
        }
    }
}

@Composable
private fun ProfileCard(
    profile: TvProfile,
    onClick: () -> Unit,
) {
    var focused by
        remember {
            mutableStateOf(false)
        }

    Column(
        modifier =
            Modifier
                .width(180.dp)
                .background(
                    if (focused) {
                        Color(
                            0xFF2563EB,
                        )
                    } else {
                        Color(
                            0xFF151C28,
                        )
                    },
                    RoundedCornerShape(
                        16.dp,
                    ),
                )
                .onFocusChanged {
                    focused =
                        it.isFocused
                }
                .clickable(
                    onClick = onClick,
                )
                .padding(20.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp,
            ),
    ) {
        Text(
            profile.avatar,
            style =
                MaterialTheme
                    .typography
                    .displayMedium,
        )
        Text(
            profile.name,
            color = Color.White,
            fontWeight =
                FontWeight.Bold,
        )
        if (profile.isKids) {
            Text(
                "Çocuk",
                color =
                    Color(
                        0xFF93C5FD,
                    ),
            )
        }
        if (
            profile.pinHash != null
        ) {
            Text(
                "🔒",
                color =
                    Color(
                        0xFFCBD5E1,
                    ),
            )
        }
    }
}

@Composable
private fun ProfileButton(
    text: String,
    selected: Boolean = false,
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
                        focused ||
                        selected
                    ) {
                        Color(
                            0xFF2563EB,
                        )
                    } else {
                        Color(
                            0xFF273449,
                        )
                    },
                    RoundedCornerShape(
                        9.dp,
                    ),
                )
                .onFocusChanged {
                    focused =
                        it.isFocused
                }
                .clickable(
                    onClick = onClick,
                )
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
    ) {
        Text(
            text,
            color = Color.White,
            fontWeight =
                FontWeight.Bold,
        )
    }
}
