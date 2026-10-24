package com.firkat.fillin.feature.budgetsharing.ui

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetinvitation.ui.BudgetInvitationState
import com.firkat.fillin.feature.budgetinvitation.ui.BudgetInvitationViewModel
import com.firkat.fillin.ui.component.PrimaryButton
import com.firkat.fillin.ui.component.SettingsScaffold
import com.firkat.fillin.ui.theme.FillinTheme
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FamilyAccessRoute(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    invitations: BudgetInvitationViewModel = koinViewModel(),
    joining: FamilyBudgetJoinViewModel = koinViewModel(),
) {
    val invitation by invitations.state.collectAsStateWithLifecycle()
    val join by joining.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.family_share_invitation)
    val shareMessage = stringResource(R.string.family_share_message)
    FamilyAccessScreen(
        invitation = invitation,
        join = join,
        onBack = onBack,
        onSignIn = onSignIn,
        onCreateCode = invitations::create,
        onShareCode = {
            invitations.shareCode { code ->
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareMessage.format(code))
                context.startActivity(Intent.createChooser(intent, shareTitle))
            }
        },
        onRevokeCode = invitations::revoke,
        onRefreshCode = invitations::refresh,
        onCodeChange = joining::editCode,
        onJoin = joining::join,
    ) { FamilyMembersSection() }
}

@Composable
fun FamilyAccessScreen(
    invitation: BudgetInvitationState,
    join: FamilyJoinState,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onCreateCode: () -> Unit,
    onShareCode: () -> Unit,
    onRevokeCode: () -> Unit,
    onRefreshCode: () -> Unit,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
    membersContent: @Composable () -> Unit = {},
) {
    SettingsScaffold(
        title = stringResource(R.string.profile_family_access),
        onBack = onBack,
        modifier = modifier.testTag("family_access_screen")
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (join.session !is AuthSession.Authenticated) {
                Text(
                    text = stringResource(R.string.family_sign_in_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
                PrimaryButton(
                    text = stringResource(R.string.profile_sign_in),
                    onClick = onSignIn, Modifier.fillMaxWidth()
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.family_invite_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.family_invite_hint), style = MaterialTheme.typography.bodySmall)
                    val target = invitation.target
                    when {
                        target == null -> Text(stringResource(R.string.family_cloud_required), style = MaterialTheme.typography.bodySmall)
                        target.role != "OWNER" -> Text(stringResource(R.string.invitation_owner_only), style = MaterialTheme.typography.bodySmall)
                        else -> {
                            invitation.current?.let { current ->
                                val deadline = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(current.expiresAt))
                                FamilyInvitationCard(invitation.code, deadline, invitation.busy, onShareCode, onRevokeCode)
                            }
                            if (invitation.expired) Text(stringResource(R.string.invitation_expired), style = MaterialTheme.typography.bodySmall)
                            PrimaryButton(stringResource(R.string.invitation_create), onCreateCode, Modifier.fillMaxWidth(), enabled = !invitation.busy, isLoading = invitation.busy)
                            // The API reveals a code once. A restored invitation can still be revoked.
                            TextButton(onClick = onRefreshCode, enabled = !invitation.busy) { Text(stringResource(R.string.invitation_refresh)) }
                            invitation.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FamilyJoinForm(join.code, join.busy, onCodeChange, onJoin)
                join.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                join.joinedName?.let { Text(stringResource(R.string.family_joined, it)) }
                membersContent()
            }
        }
    }
}

@Composable
private fun FamilyInvitationCard(code: String?, deadline: String, busy: Boolean, onShareClick: () -> Unit, onRevokeClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val readableCode = code?.split('-')?.chunked(2)?.joinToString("\n") { it.joinToString("-") }
                Text(readableCode ?: stringResource(R.string.family_invitation_hidden), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontFamily = if (code == null) FontFamily.Default else FontFamily.Monospace)
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                    Text(stringResource(R.string.family_invitation_active), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(stringResource(R.string.invitation_expires, deadline), style = MaterialTheme.typography.bodySmall)
            if (code == null) Text(stringResource(R.string.invitation_code_hidden), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onShareClick,
                    enabled = code != null && !busy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.family_share))
                }
                TextButton(onClick = onRevokeClick, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.family_invitation_revoke))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyJoinForm(code: String, busy: Boolean, onCodeChange: (String) -> Unit, onJoinClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.family_join_short_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.family_join_hint), style = MaterialTheme.typography.bodySmall)
        FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                enabled = !busy,
                label = { Text(stringResource(R.string.family_join_code)) },
                placeholder = { Text(stringResource(R.string.family_code_example)) },
                modifier = Modifier.weight(1f).widthIn(min = 200.dp),
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
            )
            Button(onClick = onJoinClick, enabled = !busy && code.isNotBlank(), modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.family_join_short))
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Preview(name = "Invitation code")
@Preview(name = "Invitation code dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FamilyInvitationPreview() { FillinTheme { FamilyInvitationCard("ABCDE-FGHJK-MNPQR-STVW0", "31.10.2026, 23:59", false, {}, {}) } }

@Preview(name = "Invitation restored")
@Composable
private fun FamilyInvitationHiddenPreview() { FillinTheme { FamilyInvitationCard(null, "31.10.2026, 23:59", false, {}, {}) } }

@Preview(name = "Invitation busy")
@Composable
private fun FamilyInvitationBusyPreview() { FillinTheme { FamilyInvitationCard("ABCDE-FGHJK-MNPQR-STVW0", "31.10.2026, 23:59", true, {}, {}) } }

@Preview(name = "Invitation large text", widthDp = 320, fontScale = 2f)
@Composable
private fun FamilyInvitationLargeTextPreview() { FillinTheme { FamilyInvitationCard("ABCDE-FGHJK-MNPQR-STVW0", "31.10.2026, 23:59", false, {}, {}) } }

@Preview(name = "Join empty")
@Preview(name = "Join empty dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FamilyJoinEmptyPreview() { FillinTheme { FamilyJoinForm("", false, {}, {}) } }

@Preview(name = "Join filled")
@Composable
private fun FamilyJoinFilledPreview() { FillinTheme { FamilyJoinForm("ABCDE-FGHJK-MNPQR-STVW0", false, {}, {}) } }

@Preview(name = "Join busy")
@Composable
private fun FamilyJoinBusyPreview() { FillinTheme { FamilyJoinForm("ABCDE-FGHJK-MNPQR-STVW0", true, {}, {}) } }

@Preview(name = "Join large text", widthDp = 320, fontScale = 2f)
@Composable
private fun FamilyJoinLargeTextPreview() { FillinTheme { FamilyJoinForm("ABCDE-FGHJK-MNPQR-STVW0", false, {}, {}) } }

@Preview(name = "Family guest", widthDp = 393, heightDp = 852)
@Preview(name = "Family guest dark", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FamilyAccessGuestPreview() { FillinTheme { FamilyAccessScreen(BudgetInvitationState(), FamilyJoinState(AuthSession.Anonymous), {}, {}, {}, {}, {}, {}, {}, {}) } }

@Preview(name = "Family owner", widthDp = 393, heightDp = 852)
@Preview(name = "Family owner dark", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FamilyAccessOwnerPreview() {
    FillinTheme {
        FamilyAccessScreen(
            BudgetInvitationState(
                target = com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget("preview", "preview", "family", "OWNER"),
                current = com.firkat.fillin.feature.budgetinvitation.domain.BudgetInvitation("preview", "family", "2026-10-31T18:59:00Z"),
                code = "ABCDE-FGHJK-MNPQR-STVW0",
            ),
            FamilyJoinState(AuthSession.Authenticated(com.firkat.fillin.feature.auth.domain.AuthUser("preview", "Анна Иванова", "anna@example.com"))),
            {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(name = "Restored invitation busy")
@Composable
private fun FamilyInvitationHiddenBusyPreview() { FillinTheme { FamilyInvitationCard(null, "31.10.2026, 23:59", true, {}, {}) } }

@Preview(name = "Join on tablet", widthDp = 840)
@Composable
private fun FamilyJoinExpandedPreview() { FillinTheme { FamilyJoinForm("ABCDE-FGHJK-MNPQR-STVW0", false, {}, {}) } }
