package com.firkat.fillin.feature.profile.ui

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationDraft
import com.firkat.fillin.feature.profile.domain.ProfileBudget
import com.firkat.fillin.ui.component.PrimaryButton
import com.firkat.fillin.ui.theme.FillinTheme
import com.firkat.fillin.ui.icon.FillinIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProfileRoute(
    user: AuthUser?,
    authBusy: Boolean,
    onAccountClick: () -> Unit,
    onFamilyAccessClick: () -> Unit,
    onCloudSyncClick: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(
        state = state,
        user = user,
        authBusy = authBusy,
        onAccountClick = onAccountClick,
        onBudgetClick = viewModel::selectBudget,
        onCreateBudgetClick = viewModel::openCreation,
        onFamilyAccessClick = onFamilyAccessClick,
        onCloudSyncClick = onCloudSyncClick,
        onRetry = viewModel::refresh,
    )
    state.creation?.let { draft ->
        ProfileBudgetCreationDialog(
            draft = draft,
            busy = state.busy,
            pending = state.pendingCreation,
            error = state.error,
            onDraftChange = viewModel::editCreation,
            onCreateCloudClick = viewModel::createCloud,
            onCreateLocalClick = viewModel::createLocal,
            onDismiss = viewModel::closeCreation,
            signedIn = user != null,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    user: AuthUser?,
    authBusy: Boolean,
    onAccountClick: () -> Unit,
    onBudgetClick: (String) -> Unit,
    onCreateBudgetClick: () -> Unit,
    onFamilyAccessClick: () -> Unit,
    onCloudSyncClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag("profile_screen"),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.navigation_profile), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (user != null) IconButton(onClick = onAccountClick, enabled = !authBusy) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.auth_sign_out), Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 32.dp),
        ) {
            if (user != null) {
                ProfileAccount(user.displayName, user.email)
            } else {
                PrimaryButton(stringResource(R.string.profile_sign_in), onAccountClick, Modifier.fillMaxWidth(), enabled = !authBusy, isLoading = authBusy)
            }
            Spacer(Modifier.height(48.dp))
            Text(stringResource(R.string.profile_budgets), style = MaterialTheme.typography.titleMedium, fontSize = 18.sp, fontWeight = FontWeight.Normal)
            Spacer(Modifier.height(12.dp))
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("profile_loading"))
            state.budgets.forEachIndexed { index, budget ->
                ProfileBudgetCard(
                    name = budget.name ?: if (budget.family) stringResource(R.string.profile_family_budget_name) else stringResource(R.string.profile_local_budget_name, state.budgets.take(index + 1).count { !it.family }),
                    family = budget.family,
                    selected = budget.selected,
                    enabled = !state.busy,
                    onClick = { onBudgetClick(budget.id) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
            PrimaryButton(stringResource(R.string.profile_create_budget), onCreateBudgetClick, Modifier.fillMaxWidth(), enabled = !state.busy)
            if (state.busy && state.creation == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            if (state.error != null && state.creation == null) {
                Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = onRetry, enabled = !state.busy) { Text(stringResource(R.string.budget_profile_retry)) }
            }
            Spacer(Modifier.height(40.dp))
            ProfileSettingRow(
                title = stringResource(R.string.profile_family_access),
                subtitle = stringResource(R.string.profile_family_subtitle),
                icon = FillinIcons.FamilyAccess,
                onClick = onFamilyAccessClick,
            )
            Spacer(Modifier.height(8.dp))
            ProfileSettingRow(
                title = stringResource(R.string.profile_cloud_sync),
                subtitle = stringResource(R.string.profile_cloud_subtitle),
                icon = FillinIcons.CloudUpload,
                onClick = onCloudSyncClick,
            )
        }
    }
}

@Composable
private fun ProfileAccount(name: String?, email: String?, modifier: Modifier = Modifier) {
    val displayName = name?.takeIf(String::isNotBlank) ?: stringResource(R.string.auth_google_account)
    val initials = displayName.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(initials, style = MaterialTheme.typography.bodyMedium) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(displayName, style = MaterialTheme.typography.titleMedium, fontSize = 20.sp, fontWeight = FontWeight.Normal)
            email?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ProfileBudgetCard(
    name: String,
    family: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { this.selected = selected; role = Role.RadioButton },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(if (family) R.string.profile_family_budget else R.string.profile_personal_budget), style = MaterialTheme.typography.bodySmall)
            }
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun ProfileSettingRow(title: String, subtitle: String, icon: Painter, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.heightIn(min = 64.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.wrapContentSize()) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun ProfileBudgetCreationDialog(
    draft: BudgetCreationDraft,
    busy: Boolean,
    pending: Boolean,
    error: String?,
    onDraftChange: (BudgetCreationDraft) -> Unit,
    onCreateCloudClick: () -> Unit,
    onCreateLocalClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    signedIn: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        modifier = modifier,
        title = { Text(stringResource(R.string.profile_new_budget)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (signedIn) OutlinedTextField(draft.name, { onDraftChange(draft.copy(name = it)) }, label = { Text(stringResource(R.string.cloud_budget_name)) }, enabled = !busy && !pending, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.openingBalance, { onDraftChange(draft.copy(openingBalance = it)) }, label = { Text(stringResource(R.string.cloud_budget_opening_balance)) }, enabled = !busy && !pending, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.accountingStartedOn, { onDraftChange(draft.copy(accountingStartedOn = it)) }, label = { Text(stringResource(R.string.cloud_budget_start_date)) }, enabled = !busy && !pending, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (pending) Text(stringResource(R.string.cloud_budget_create_pending))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (signedIn && !pending) TextButton(onClick = onCreateLocalClick, enabled = !busy) { Text(stringResource(R.string.profile_create_local)) }
            }
        },
        confirmButton = { TextButton(onClick = if (signedIn) onCreateCloudClick else onCreateLocalClick, enabled = !busy) { Text(stringResource(if (!signedIn) R.string.budget_setup_create else if (pending) R.string.cloud_budget_create_resume else R.string.cloud_budget_create)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cloud_budget_create_cancel)) } },
    )
}

@Preview(name = "Account")
@Preview(name = "Account dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileAccountPreview() { FillinTheme { ProfileAccount("Анна Иванова", "anna@example.com") } }

@Preview(name = "Account fallback")
@Composable
private fun ProfileAccountFallbackPreview() { FillinTheme { ProfileAccount(null, null) } }

@Preview(name = "Account long", widthDp = 320, fontScale = 2f)
@Composable
private fun ProfileAccountLongPreview() { FillinTheme { ProfileAccount("Анна Александровна Иванова", "anna.ivanova@example.com") } }

@Preview(name = "Selected family budget")
@Preview(name = "Selected family budget dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileBudgetSelectedPreview() { FillinTheme { ProfileBudgetCard("Основной бюджет", true, true, true, {}) } }

@Preview(name = "Personal budget")
@Composable
private fun ProfileBudgetPersonalPreview() { FillinTheme { ProfileBudgetCard("Накопления", false, false, true, {}) } }

@Preview(name = "Family budget")
@Composable
private fun ProfileBudgetFamilyPreview() { FillinTheme { ProfileBudgetCard("Семейный бюджет", true, false, true, {}) } }

@Preview(name = "Selected personal budget")
@Composable
private fun ProfileBudgetPersonalSelectedPreview() { FillinTheme { ProfileBudgetCard("Накопления", false, true, true, {}) } }

@Preview(name = "Disabled budget")
@Composable
private fun ProfileBudgetDisabledPreview() { FillinTheme { ProfileBudgetCard("Накопления", false, false, false, {}) } }

@Preview(name = "Long budget", widthDp = 320, fontScale = 2f)
@Composable
private fun ProfileBudgetLongPreview() { FillinTheme { ProfileBudgetCard("Накопления на большую семейную поездку", false, true, true, {}) } }

@Preview(name = "Family setting")
@Preview(name = "Family setting dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileFamilySettingPreview() { FillinTheme { ProfileSettingRow(stringResource(R.string.profile_family_access), stringResource(R.string.profile_family_subtitle), FillinIcons.FamilyAccess, {}) } }

@Preview(name = "Cloud setting", widthDp = 320, fontScale = 2f)
@Preview(name = "Cloud setting dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileCloudSettingPreview() { FillinTheme { ProfileSettingRow(stringResource(R.string.profile_cloud_sync), stringResource(R.string.profile_cloud_subtitle), FillinIcons.CloudUpload, {}) } }

@Preview(name = "Profile guest", widthDp = 393, heightDp = 760)
@Preview(name = "Profile guest dark", widthDp = 393, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileGuestPreview() { FillinTheme { ProfileScreen(previewProfileState(), null, false, {}, {}, {}, {}, {}, {}) } }

@Preview(name = "Profile account", widthDp = 393, heightDp = 760)
@Composable
private fun ProfileAuthenticatedPreview() { FillinTheme { ProfileScreen(previewProfileState(), AuthUser("preview", "Анна Иванова", "anna@example.com"), false, {}, {}, {}, {}, {}, {}) } }

@Preview(name = "Profile expanded", widthDp = 840)
@Composable
private fun ProfileExpandedPreview() { FillinTheme { ProfileScreen(previewProfileState(), null, false, {}, {}, {}, {}, {}, {}) } }

@Preview(name = "Create cloud budget")
@Preview(name = "Create cloud budget dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileCreationPreview() { FillinTheme { ProfileBudgetCreationDialog(BudgetCreationDraft("Семейный бюджет", "0", "2026-10-24"), false, false, null, {}, {}, {}, {}) } }

@Preview(name = "Resume creation")
@Composable
private fun ProfileCreationPendingPreview() { FillinTheme { ProfileBudgetCreationDialog(BudgetCreationDraft("Семейный бюджет", "0", "2026-10-24"), false, true, null, {}, {}, {}, {}) } }

@Preview(name = "Creating budget")
@Composable
private fun ProfileCreationBusyPreview() { FillinTheme { ProfileBudgetCreationDialog(BudgetCreationDraft("Семейный бюджет", "0", "2026-10-24"), true, false, null, {}, {}, {}, {}) } }

@Preview(name = "Creation error")
@Composable
private fun ProfileCreationErrorPreview() { FillinTheme { ProfileBudgetCreationDialog(BudgetCreationDraft("", "0", "2026-10-24"), false, false, stringResource(R.string.cloud_budget_create_pending), {}, {}, {}, {}) } }

@Preview(name = "Create personal budget")
@Preview(name = "Create personal budget dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileLocalCreationPreview() { FillinTheme { ProfileBudgetCreationDialog(BudgetCreationDraft("", "0", "2026-10-24"), false, false, null, {}, {}, {}, {}, signedIn = false) } }

@Preview(name = "Selected disabled budget")
@Composable
private fun ProfileBudgetSelectedDisabledPreview() { FillinTheme { ProfileBudgetCard("Основной бюджет", true, true, false, {}) } }

@Preview(name = "Budget on tablet", widthDp = 840)
@Composable
private fun ProfileBudgetExpandedPreview() { FillinTheme { ProfileBudgetCard("Накопления", false, true, true, {}) } }

private fun previewProfileState() = ProfileUiState(loading = false, budgets = listOf(
    ProfileBudget("family", "Основной бюджет", true, true), ProfileBudget("local", "Накопления", false, false),
))
