package com.firkat.fillin.feature.profile.ui

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.appaccess.ui.AboutAppViewModel
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.firkat.fillin.feature.auth.ui.GoogleSignInLauncher
import com.firkat.fillin.feature.budgetmanagement.ui.BudgetManagementViewModel
import com.firkat.fillin.feature.budgetmanagement.ui.PendingBudgetDeletion
import com.firkat.fillin.feature.budgetsetup.ui.accessErrorResource
import com.firkat.fillin.feature.profile.domain.DeletionPhase
import com.firkat.fillin.feature.profile.domain.ProfileBudget
import com.firkat.fillin.feature.profile.domain.ProfileDeletionState
import com.firkat.fillin.feature.profile.ui.bottomsheets.ProfileBottomSheetHost
import com.firkat.fillin.ui.component.AccountIdentity
import com.firkat.fillin.ui.component.PrimaryButton
import com.firkat.fillin.ui.component.SaldomiButton
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.icon.FillinIcons
import com.firkat.fillin.ui.theme.FillinTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProfileRoute(
    user: AuthUser?,
    authBusy: Boolean,
    onAccountClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onFamilyAccessClick: () -> Unit,
    onCloudSyncClick: () -> Unit,
    onCreateBudgetClick: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
    onMethodsClick: () -> Unit = onAccountClick,
    deletionViewModel: ProfileDeletionViewModel = koinViewModel(),
    bottomBarOverlap: Dp = 0.dp,
    budgetReadOnly: Boolean = false
) {
    val about: AboutAppViewModel = koinViewModel()
    val installationId by about.installationId.collectAsStateWithLifecycle()
    val accessChecking by about.checking.collectAsStateWithLifecycle()
    val accessRefreshResult by about.refreshResult.collectAsStateWithLifecycle()
    val management: BudgetManagementViewModel = koinViewModel()
    val managementState by management.state.collectAsStateWithLifecycle()
    val pendingDeletions by management.pending.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deletionState by deletionViewModel.deletion.state.collectAsStateWithLifecycle()
    val confirmation by deletionViewModel.confirmation.collectAsStateWithLifecycle()
    val password by deletionViewModel.password.collectAsStateWithLifecycle()
    val googleBusy by deletionViewModel.googleBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val google: GoogleSignInLauncher = koinInject()
    DisposableEffect(deletionViewModel) {
        deletionViewModel.deletion.setVisible(true)
        onDispose { deletionViewModel.deletion.setVisible(false) }
    }
    ProfileBottomSheetHost(
        installationId = installationId,
        accessChecking = accessChecking,
        accessRefreshResult = accessRefreshResult,
        fullAccess = state.fullAccess,
        management = managementState,
        userId = user?.uid,
        confirmation = confirmation,
        onRefreshAccess = about::refresh,
        onCloseAbout = about::close,
        onBudgetNameChanged = management::change,
        onConfirmBudget = management::confirm,
        onCloseBudget = management::close,
        onOpenCurrencyPicker = management::openCurrencyPicker,
        onCurrencySelected = management::selectCurrency,
        onCloseCurrencyPicker = management::closeCurrencyPicker,
        onConfirmCurrencyChange = management::confirmCurrencyChange,
        onDismissCurrencyChange = management::dismissCurrencyConfirmation,
        onSignOut = { deletionViewModel.confirmSignOut(onAccountClick) },
        onAccountConfirmationChanged = deletionViewModel::changeConfirmation,
        onDeleteAccount = deletionViewModel::submit,
        onDismissAccountConfirmation = deletionViewModel::close
    )
    ProfileScreen(
        onManageBudget = { id, name, deleting ->
            viewModel.closeBudgetActions()
            state.budgets.firstOrNull { it.id == id && it.canManage }?.let { budget ->
                management.open(id, name, deleting, budget.family, budget.currency)
            }
        },
        onBudgetActionsClick = viewModel::toggleBudgetActions,
        onAboutClick = about::open,
        onBack = onBack,
        managementContent = {
            pendingDeletions.filter { state.fullAccess || it.uid == null }.forEach {
                PendingBudgetDeletion(
                    it.name,
                    it.error != null && it.error != "sending",
                    management::retry,
                    sending =
                        it.error == "sending",
                    denied = it.error == "access_denied"
                )
            }
        },
        deletionState = deletionState,
        onDeleteClick = deletionViewModel::open,
        deletionContent = {
            DeletionStatusContent(deletionState, deletionViewModel.deletion::retry)
            if (deletionState.phase == DeletionPhase.Reauthenticate) {
                DeletionReauthentication(
                    password,
                    { deletionViewModel.password.value = it },
                    user?.providers?.contains("password") == true,
                    user?.providers?.contains("google.com") == true,
                    googleBusy,
                    deletionViewModel::password,
                    { deletionViewModel.google { google.launch(context) } }
                )
            }
        },
        state = state,
        user = user,
        authBusy = authBusy,
        onAccountClick = { if (user == null) onAccountClick() else deletionViewModel.openSignOut() },
        onMethodsClick = onMethodsClick,
        onBudgetClick = viewModel::selectBudget,
        onCreateBudgetClick = onCreateBudgetClick,
        onCategoriesClick = onCategoriesClick,
        onFamilyAccessClick = onFamilyAccessClick,
        onCloudSyncClick = onCloudSyncClick,
        onRetry = viewModel::refresh,
        bottomBarOverlap = bottomBarOverlap,
        budgetReadOnly = budgetReadOnly
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    user: AuthUser?,
    authBusy: Boolean,
    onAccountClick: () -> Unit,
    onBudgetClick: (String) -> Unit,
    onCreateBudgetClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onFamilyAccessClick: () -> Unit,
    onCloudSyncClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMethodsClick: () -> Unit = onAccountClick,
    deletionState: ProfileDeletionState = ProfileDeletionState(),
    onDeleteClick: () -> Unit = {},
    deletionContent: @Composable () -> Unit = {},
    onManageBudget: (String, String, Boolean) -> Unit = { _, _, _ -> },
    managementContent: @Composable () -> Unit = {},
    onBudgetActionsClick: (String) -> Unit = {},
    onAboutClick: (() -> Unit)? = null,
    bottomBarOverlap: Dp = 0.dp,
    budgetReadOnly: Boolean = false
) {
    Column(
        modifier = modifier
            .testTag("profile_screen")
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 78.dp, bottom = 32.dp + bottomBarOverlap)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_back),
                    contentDescription = null
                )
            }
            Text(
                text = stringResource(R.string.navigation_profile),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (user != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                ProfileAccount(user.displayName, user.email, Modifier.padding(16.dp))
                if (state.fullAccess) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                if (state.fullAccess) {
                    Surface(
                        onClick = onMethodsClick,
                        enabled = !authBusy,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        if (user.emailVerified) {
                                            R.string.email_methods
                                        } else {
                                            R.string.email_verify
                                        }
                                    ),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        } else if (state.fullAccess) {
            SaldomiButton(
                onClick = onAccountClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !authBusy,
                isLoading = authBusy
            ) {
                Text(
                    text = stringResource(R.string.profile_sign_in)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.profile_budgets),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(stringResource(R.string.budget_manage_selection_hint), style = MaterialTheme.typography.bodyMedium)
        if (budgetReadOnly) {
            Text(
                stringResource(R.string.cloud_budget_read_only),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(12.dp))
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("profile_loading"))
        state.budgets.forEachIndexed { index, budget ->
            val displayName =
                budget.name
                    ?: if (budget.family) {
                        stringResource(R.string.profile_family_budget_name)
                    } else {
                        stringResource(
                            R.string.profile_local_budget_name,
                            state.budgets.take(index + 1).count { !it.family }
                        )
                    }
            key(budget.id) {
                ProfileBudgetCard(
                    name = displayName,
                    expanded = state.expandedBudgetId == budget.id,
                    onActionsClick = { onBudgetActionsClick(budget.id) },
                    canManage = budget.canManage,
                    pendingRename = budget.pendingRename,
                    onRename = { onManageBudget(budget.id, displayName, false) },
                    onRemove = { onManageBudget(budget.id, displayName, true) },
                    family = budget.family,
                    currency = budget.currency.name,
                    selected = budget.selected,
                    enabled = !state.busy,
                    onClick = { onBudgetClick(budget.id) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
            }
        }
        managementContent()

        SaldomiOutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onCreateBudgetClick,
            large = true
        ) {
            Text(
                text = stringResource(R.string.profile_create_budget)
            )
        }

        if (state.error != null) {
            Text(
                accessErrorResource(state.error)?.let {
                    stringResource(it)
                } ?: state.error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp)
            )
            TextButton(onClick = onRetry, enabled = !state.busy) {
                Text(stringResource(R.string.budget_profile_retry))
            }
        }
        Spacer(Modifier.height(20.dp))
        ProfileSettingRow(
            title = stringResource(R.string.navigation_categories),
            subtitle = stringResource(R.string.profile_categories_subtitle),
            icon = FillinIcons.Categories,
            onClick = onCategoriesClick
        )
        if (state.fullAccess) {
            Spacer(Modifier.height(8.dp))
            ProfileSettingRow(
                title = stringResource(R.string.profile_family_access),
                subtitle = stringResource(R.string.profile_family_subtitle),
                icon = FillinIcons.FamilyAccess,
                onClick = onFamilyAccessClick
            )
            Spacer(Modifier.height(8.dp))
            ProfileSettingRow(
                title = stringResource(R.string.profile_cloud_sync),
                subtitle = stringResource(R.string.profile_cloud_subtitle),
                icon = FillinIcons.CloudUpload,
                onClick = onCloudSyncClick
            )
        }
        if (onAboutClick != null) {
            TextButton(onClick = onAboutClick) { Text(stringResource(R.string.app_about)) }
        }
        Spacer(Modifier.height(32.dp))
        if (deletionState.phase != DeletionPhase.Idle) deletionContent()
        if (user != null && deletionState.phase in setOf(DeletionPhase.Idle, DeletionPhase.Completed)) {
            Text(
                stringResource(R.string.profile_account_management),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            ProfileAccountAction(
                stringResource(R.string.auth_sign_out),
                false,
                !authBusy && !state.busy,
                onAccountClick,
                Modifier.testTag("sign_out_profile")
            )
            Spacer(Modifier.height(8.dp))
            ProfileAccountAction(
                stringResource(R.string.profile_delete),
                true,
                !authBusy && !state.busy,
                onDeleteClick,
                Modifier.testTag("delete_profile")
            )
        }
    }
}

@Composable
private fun ProfileAccount(
    name: String?,
    email: String?,
    modifier: Modifier = Modifier
) {
    AccountIdentity(name, email, modifier)
}

@Composable
private fun ProfileBudgetCard(
    name: String,
    family: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    canManage: Boolean = false,
    pendingRename: Boolean = false,
    onRename: () -> Unit = {},
    onRemove: () -> Unit = {},
    expanded: Boolean = false,
    onActionsClick: () -> Unit = {},
    currency: String = "KZT"
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) colors.surfaceVariant else colors.surface,
        tween(200),
        label = "Budget background"
    )
    val outline by animateColorAsState(
        if (selected) colors.primary else colors.outlineVariant,
        tween(200),
        label = "Budget outline"
    )
    val outlineWidth by animateDpAsState(
        if (selected) 1.5.dp else 1.dp,
        tween(200),
        label = "Budget outline width"
    )

    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier.semantics {
                this.selected = selected
                role = Role.RadioButton
            },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(outlineWidth, outline),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = container,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                if (family) FillinIcons.FamilyAccess else FillinIcons.Wallet,
                null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (pendingRename) {
                    Text(
                        stringResource(R.string.budget_manage_pending_rename),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    stringResource(
                        if (family) R.string.profile_family_budget else R.string.profile_personal_budget,
                        currency
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (canManage) {
                IconButton(onClick = onActionsClick, enabled = enabled) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.budget_manage_actions))
                }
            }
        }
        if (canManage && expanded) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            FlowRow(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onRename, enabled = enabled) {
                    Icon(Icons.Outlined.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.budget_manage_edit_action))
                }
                TextButton(
                    onClick = onRemove,
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Outlined.DeleteOutline, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.budget_manage_delete_action))
                }
            }
        }
    }
}

@Composable
private fun ProfileSettingRow(
    title: String,
    subtitle: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun ProfileAccountAction(
    title: String,
    destructive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (destructive) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(if (destructive) Icons.Outlined.DeleteOutline else Icons.AutoMirrored.Outlined.Logout, null)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        }
    }
}

@Preview(name = "Account")
@Preview(name = "Account dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileAccountPreview() {
    FillinTheme { ProfileAccount("Анна Иванова", "anna@example.com") }
}

@Preview(name = "Account fallback")
@Composable
private fun ProfileAccountFallbackPreview() {
    FillinTheme { ProfileAccount(null, null) }
}

@Preview(name = "Account long", widthDp = 320, fontScale = 2f)
@Composable
private fun ProfileAccountLongPreview() {
    FillinTheme { ProfileAccount("Анна Александровна Иванова", "anna.ivanova@example.com") }
}

@Preview(name = "Selected family budget")
@Preview(name = "Selected family budget dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileBudgetSelectedPreview() {
    FillinTheme { ProfileBudgetCard("Основной бюджет", true, true, true, {}) }
}

@Preview(name = "Personal budget")
@Preview(name = "Personal budget dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileBudgetPersonalPreview() {
    FillinTheme { ProfileBudgetCard("Накопления", false, false, true, {}) }
}

@Preview(name = "Family budget")
@Composable
private fun ProfileBudgetFamilyPreview() {
    FillinTheme { ProfileBudgetCard("Семейный бюджет", true, false, true, {}) }
}

@Preview(name = "Selected personal budget")
@Composable
private fun ProfileBudgetPersonalSelectedPreview() {
    FillinTheme { ProfileBudgetCard("Накопления", false, true, true, {}) }
}

@Preview(name = "Disabled budget")
@Composable
private fun ProfileBudgetDisabledPreview() {
    FillinTheme { ProfileBudgetCard("Накопления", false, false, false, {}) }
}

@Preview(name = "Long budget", widthDp = 320, fontScale = 2f)
@Preview(name = "Long budget dark", widthDp = 320, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileBudgetLongPreview() {
    FillinTheme { ProfileBudgetCard("Накопления на большую семейную поездку", false, true, true, {}) }
}

@Preview(name = "Family setting")
@Preview(name = "Family setting dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileFamilySettingPreview() {
    FillinTheme {
        ProfileSettingRow(
            stringResource(
                R.string.profile_family_access
            ),
            stringResource(R.string.profile_family_subtitle),
            FillinIcons.FamilyAccess,
            {
            }
        )
    }
}

@Preview(name = "Categories setting")
@Preview(name = "Categories setting dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Categories setting large font", widthDp = 320, fontScale = 2f)
@Composable
private fun ProfileCategoriesSettingPreview() {
    FillinTheme {
        ProfileSettingRow(
            stringResource(
                R.string.navigation_categories
            ),
            stringResource(R.string.profile_categories_subtitle),
            FillinIcons.Categories,
            {
            }
        )
    }
}

@Preview(name = "Cloud setting", widthDp = 320, fontScale = 2f)
@Preview(name = "Cloud setting dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileCloudSettingPreview() {
    FillinTheme {
        ProfileSettingRow(
            stringResource(
                R.string.profile_cloud_sync
            ),
            stringResource(R.string.profile_cloud_subtitle),
            FillinIcons.CloudUpload,
            {
            }
        )
    }
}

@Preview(name = "Profile guest", widthDp = 393, heightDp = 760)
@Preview(name = "Profile guest dark", widthDp = 393, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileGuestPreview() {
    FillinTheme { ProfileScreen(previewProfileState(), null, false, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Profile account", widthDp = 393, heightDp = 760)
@Preview(name = "Profile account dark", widthDp = 393, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Profile large font", widthDp = 320, heightDp = 760, fontScale = 2f)
@Composable
private fun ProfileAuthenticatedPreview() {
    FillinTheme {
        ProfileScreen(previewProfileState(), AuthUser("preview", "Анна Иванова", "anna@example.com"), false, {
        }, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Profile expanded", widthDp = 840)
@Composable
private fun ProfileExpandedPreview() {
    FillinTheme { ProfileScreen(previewProfileState(), null, false, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Selected disabled budget")
@Composable
private fun ProfileBudgetSelectedDisabledPreview() {
    FillinTheme { ProfileBudgetCard("Основной бюджет", true, true, false, {}) }
}

@Preview(name = "Budget on tablet", widthDp = 840)
@Composable
private fun ProfileBudgetExpandedPreview() {
    FillinTheme { ProfileBudgetCard("Накопления", false, true, true, {}) }
}

private fun previewProfileState() = ProfileUiState(
    loading = false,
    budgets =
        listOf(
            ProfileBudget("family", "Основной бюджет", true, true),
            ProfileBudget("local", "Накопления", false, false)
        )
)

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SignOutActionPreview() {
    FillinTheme { ProfileAccountAction(stringResource(R.string.auth_sign_out), false, true, {}) }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeleteActionPreview() {
    FillinTheme { ProfileAccountAction(stringResource(R.string.profile_delete), true, true, {}) }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DisabledAccountActionsPreview() {
    FillinTheme {
        Column {
            ProfileAccountAction(stringResource(R.string.auth_sign_out), false, false, {})
            ProfileAccountAction(stringResource(R.string.profile_delete), true, false, {})
        }
    }
}

@Preview(name = "Budget actions")
@Preview(name = "Budget actions dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Budget actions compact", widthDp = 320, fontScale = 2f)
@Preview(name = "Budget actions wide", widthDp = 840)
@Composable
private fun ProfileBudgetActionsPreview() {
    FillinTheme {
        ProfileBudgetCard("Большой семейный бюджет", true, true, true, {}, canManage = true, expanded = true)
    }
}
