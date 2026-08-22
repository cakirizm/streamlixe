package com.streamlivex.android.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.streamlivex.android.R
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

    var managing by
        remember {
            mutableStateOf(false)
        }

    val firstProfileRequester = remember { FocusRequester() }

    LaunchedEffect(profiles, managing, pending) {
        if (!managing && pending == null && profiles.isNotEmpty()) {
            kotlinx.coroutines.delay(120)
            runCatching { firstProfileRequester.requestFocus() }
        }
    }

    if (managing) {
        TvProfileManagerScreen(
            onBack = {
                profiles =
                    store.all()
                managing =
                    false
            },
        )
        return
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(0xFF080B12),
                )
                .padding(42.dp),
        contentAlignment =
            Alignment.Center,
    ) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    14.dp,
                ),
        ) {
            Text(
                "Kim izliyor?",
                color = Color.White,
                fontWeight =
                    FontWeight.SemiBold,
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        18.dp,
                    ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                profiles.forEachIndexed {
                    index,
                    profile ->

                    ProfileCard(
                        profile = profile,
                        modifier =
                            if (index == 0) {
                                Modifier.focusRequester(firstProfileRequester)
                            } else {
                                Modifier
                            },
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

                AddProfileCard {
                    managing = true
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "StreamLiveX logo",
                    modifier = Modifier.size(38.dp),
                )
                Text(
                    "StreamLiveX",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp,
                    ),
            ) {
                ProfileButton(
                    text =
                        "Profilleri Yönet",
                ) {
                    managing = true
                }

                onManage?.let {
                    externalManage ->

                    ProfileButton(
                        text =
                            "Ayarlar",
                        onClick =
                            externalManage,
                    )
                }
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
    var editing by
        remember {
            mutableStateOf<TvProfile?>(
                null,
            )
        }

    editing?.let {
        profile ->

        TvEditProfileScreen(
            profile = profile,
            onBack = {
                editing = null
            },
            onSaved = {
                rows = store.all()
                editing = null
            },
        )
        return
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

                ProfileButton(
                    "Düzenle",
                ) {
                    editing =
                        profile
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
private fun TvEditProfileScreen(
    profile: TvProfile,
    onBack: () -> Unit,
    onSaved: (TvProfile) -> Unit,
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
        remember(profile.id) {
            mutableStateOf(
                profile.name,
            )
        }
    var avatar by
        remember(profile.id) {
            mutableStateOf(
                profile.avatar,
            )
        }
    var kids by
        remember(profile.id) {
            mutableStateOf(
                profile.isKids,
            )
        }
    var pinMode by
        remember(profile.id) {
            mutableStateOf(
                if (
                    profile.pinHash !=
                    null
                ) {
                    "keep"
                } else {
                    "off"
                },
            )
        }
    var pin by
        remember(profile.id) {
            mutableStateOf("")
        }
    var error by
        remember(profile.id) {
            mutableStateOf("")
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(
                        0xFF0D111B,
                    ),
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
                "Profili Düzenle",
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
            color =
                Color.White,
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
            selected =
                kids,
        ) {
            kids = !kids
        }

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    10.dp,
                ),
        ) {
            ProfileButton(
                text =
                    "PIN'i Koru",
                selected =
                    pinMode ==
                        "keep",
            ) {
                pinMode =
                    "keep"
                pin = ""
            }

            ProfileButton(
                text =
                    "PIN'i Kaldır",
                selected =
                    pinMode ==
                        "off",
            ) {
                pinMode =
                    "off"
                pin = ""
            }

            ProfileButton(
                text =
                    "Yeni PIN",
                selected =
                    pinMode ==
                        "new",
            ) {
                pinMode =
                    "new"
            }
        }

        if (
            pinMode ==
            "new"
        ) {
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin =
                        it.filter {
                            ch ->
                            ch.isDigit()
                        }
                            .take(4)
                    error = ""
                },
                label = {
                    Text(
                        "Yeni 4 haneli PIN",
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

        if (
            error.isNotBlank()
        ) {
            Text(
                error,
                color =
                    Color(
                        0xFFF87171,
                    ),
            )
        }

        ProfileButton(
            "Kaydet",
            selected =
                name.isNotBlank(),
        ) {
            if (
                name.isBlank()
            ) {
                error =
                    "Profil adı gerekli."
                return@ProfileButton
            }

            if (
                pinMode ==
                    "new" &&
                pin.length != 4
            ) {
                error =
                    "PIN 4 haneli olmalı."
                return@ProfileButton
            }

            val updated =
                store.update(
                    id =
                        profile.id,
                    name = name,
                    avatar = avatar,
                    isKids = kids,
                    pin =
                        if (
                            pinMode ==
                            "new"
                        ) {
                            pin
                        } else {
                            null
                        },
                    keepExistingPin =
                        pinMode ==
                            "keep",
                )

            if (
                updated != null
            ) {
                onSaved(
                    updated,
                )
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by
        remember {
            mutableStateOf(false)
        }

    Column(
        modifier =
            modifier
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
        ProfileVectorAvatar(
            seed = profile.id + profile.name,
            focused = focused,
            modifier = Modifier.size(82.dp),
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
private fun ProfileVectorAvatar(
    seed: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val palettes = listOf(
        Color(0xFF006C8F) to Color(0xFF16B8C8),
        Color(0xFF49358A) to Color(0xFF9B71E8),
        Color(0xFF8A3B55) to Color(0xFFE46B8B),
        Color(0xFF236A55) to Color(0xFF51C49B),
        Color(0xFF8A5424) to Color(0xFFE5A14B),
    )
    val palette = palettes[(seed.hashCode() and Int.MAX_VALUE) % palettes.size]
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.second, palette.first),
                center = Offset(size.width * .34f, size.height * .25f),
                radius = size.width * .78f,
            ),
            radius = size.minDimension * .49f,
            center = center,
        )
        drawCircle(
            color = if (focused) Color(0xFF7DE3FF) else Color(0x55FFFFFF),
            radius = size.minDimension * .47f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = if (focused) 3.dp.toPx() else 1.dp.toPx(),
            ),
        )
        drawCircle(
            color = Color(0xFFF1C7A5),
            radius = size.width * .145f,
            center = Offset(size.width * .50f, size.height * .38f),
        )
        drawOval(
            color = Color(0xFF12212B),
            topLeft = Offset(size.width * .27f, size.height * .57f),
            size = Size(size.width * .46f, size.height * .31f),
        )
        drawArc(
            color = Color(0xFFF7D5B7),
            startAngle = 205f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(size.width * .405f, size.height * .38f),
            size = Size(size.width * .19f, size.height * .15f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx()),
        )
        drawCircle(Color(0xFF24313A), size.width * .012f, Offset(size.width * .455f, size.height * .36f))
        drawCircle(Color(0xFF24313A), size.width * .012f, Offset(size.width * .545f, size.height * .36f))
    }
}

@Composable
private fun AddProfileCard(
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
                    onClick =
                        onClick,
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
            "＋",
            color =
                Color.White,
            style =
                MaterialTheme
                    .typography
                    .displayMedium,
        )
        Text(
            "Profil Ekle",
            color =
                Color.White,
            fontWeight =
                FontWeight.Bold,
        )
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
