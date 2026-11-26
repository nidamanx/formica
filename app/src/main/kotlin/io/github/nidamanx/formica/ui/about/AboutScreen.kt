// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.about

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.sync.appVersionName
import io.github.nidamanx.formica.ui.components.AppLogo
import io.github.nidamanx.formica.ui.components.BackTopBar

private const val AUTHOR = "Nicola Davide Mannarelli"
private const val SOURCE_URL = "https://github.com/nidamanx/formica"

/** About: what Formica does, privacy, permissions, author, license and source code. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember { appVersionName(context) }
    // The installed build's own name: "Formica-D" in debug builds
    val appName = remember { context.applicationInfo.loadLabel(context.packageManager).toString() }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.about_title), onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(appName, version)
            TextSection(R.drawable.ic_about_how, R.string.about_how_title, R.string.about_how_text)
            TextSection(R.drawable.ic_about_family, R.string.about_family_title, R.string.about_family_text)
            TextSection(R.drawable.ic_about_name, R.string.about_name_title, R.string.about_name_text)
            TextSection(R.drawable.ic_about_privacy, R.string.about_privacy_title, R.string.about_privacy_text)
            Section(R.drawable.ic_about_permissions, R.string.about_permissions_title) {
                listOf(
                    R.string.about_perm_notifications,
                    R.string.about_perm_boot,
                    R.string.about_perm_wake_lock,
                    R.string.about_perm_receiver,
                ).forEach { Bullet(stringResource(it)) }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Small(stringResource(R.string.about_author, AUTHOR))
                Small(stringResource(R.string.about_license))
                SourceLink()
            }
        }
    }
}

@Composable
private fun Header(appName: String, version: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogo(size = 96.dp, shape = CircleShape)
        Text(
            appName,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(top = 12.dp)
                .semantics { heading() },
        )
        Text(
            stringResource(R.string.about_version, version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(@DrawableRes icon: Int, @StringRes title: Int, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Decorative: the title beside it says it all
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
            }
            content()
        }
    }
}

@Composable
private fun TextSection(@DrawableRes icon: Int, @StringRes title: Int, @StringRes text: Int) {
    Section(icon, title) { Text(stringResource(text), style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("\u2022", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Small(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** The repository address, opened in the browser (Formica itself has no internet access). */
@Composable
private fun SourceLink() {
    val label = stringResource(R.string.about_source, SOURCE_URL)
    val start = label.indexOf(SOURCE_URL)
    val linkStyle = TextLinkStyles(
        SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    val text = buildAnnotatedString {
        append(label.substring(0, start))
        withLink(LinkAnnotation.Url(SOURCE_URL, linkStyle)) { append(SOURCE_URL) }
        append(label.substring(start + SOURCE_URL.length))
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
