package com.example.chat.ui.subscription

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.ui.auth.AuthBackButton
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/** Success green used by plan_subscription_screen.dart (SnackBar background,
 *  feature check icons, annual-save badge). */
private val SuccessGreen = Color(0xFF10B981)

/** Port of lib/features/subscription/plan_subscription_screen.dart.
 *
 *  Shown after organization registration (isNewRegistration = true, no back
 *  button, optional free-plan shortcut) and from chats-home / account
 *  settings when the workspace needs a plan (isNewRegistration = false).
 *
 *  Backend calls are injected; AppRoot wires ApiBackendService.getAvailablePlans
 *  / subscribeToPlan. [onSubscribed] replaces the Dart
 *  pushAndRemoveUntil(MainNavigationShell) — the host routes home. The Dart
 *  screen shows the success SnackBar and navigates in the same frame; the
 *  SnackBar is launched here for parity but the route change owns the exit. */
@Composable
fun PlanSubscriptionScreen(
    organizationId: String,
    organizationName: String,
    isNewRegistration: Boolean,
    onBack: () -> Unit,
    onSubscribed: (planName: String) -> Unit,
    loadPlans: suspend (organizationId: String) -> List<Map<String, Any?>>,
    subscribe: suspend (organizationId: String, planId: String, durationMonths: Int) -> Unit,
) {
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isLoadingPlans by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isAnnual by remember { mutableStateOf(false) }
    var selectedPlanId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var plans by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }

    fun friendlyError(e: Exception): String =
        (e.message ?: e.toString()).removePrefix("Exception: ")

    fun loadPlansNow() {
        isLoadingPlans = true
        errorMessage = null
        scope.launch {
            try {
                val loaded = loadPlans(organizationId)
                plans = loaded
                selectedPlanId = if (loaded.isNotEmpty()) {
                    // Dart: default to the first highlighted or free-tier plan.
                    val defaultPlan = loaded.firstOrNull {
                        it["highlighted"] == true || it["tier"] == "free"
                    } ?: loaded.first()
                    defaultPlan["id"]?.toString()
                } else {
                    null
                }
                isLoadingPlans = false
            } catch (e: Exception) {
                errorMessage = friendlyError(e)
                plans = emptyList()
                selectedPlanId = null
                isLoadingPlans = false
            }
        }
    }

    fun isFreePlan(plan: Map<String, Any?>): Boolean =
        plan["tier"] == "free" ||
            (plan["monthly_price"] != null && (plan["monthly_price"] as? Number)?.toDouble() == 0.0)

    fun anyFreePlan(): Boolean = plans.any {
        it["tier"] == "free" || (((it["monthly_price"] as? Number)?.toDouble()) ?: 1.0) == 0.0
    }

    fun buttonText(): String {
        if (plans.isEmpty()) return "No Plans Available"
        val selected = plans.firstOrNull { it["id"]?.toString() == selectedPlanId } ?: emptyMap()
        return if (isFreePlan(selected)) "Activate Free Plan" else "Subscribe & Activate Workspace"
    }

    fun handleSubscribe() {
        val planId = selectedPlanId
        if (planId == null || isSubmitting) return
        isSubmitting = true
        errorMessage = null
        scope.launch {
            try {
                subscribe(organizationId, planId, if (isAnnual) 12 else 1)
                val planName = (plans.firstOrNull { it["id"]?.toString() == planId }
                    ?.get("name") ?: "Plan").toString()
                launch {
                    snackbarHostState.showSnackbar(
                        "Subscribed to $planName! Workspace \"$organizationName\" is now active.",
                    )
                }
                onSubscribed(planName)
            } catch (e: Exception) {
                errorMessage = friendlyError(e)
                isSubmitting = false
            }
        }
    }

    LaunchedEffect(organizationId) { loadPlansNow() }

    Scaffold(
        containerColor = Color(theme.backgroundColor),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = SuccessGreen,
                    contentColor = Color.White,
                )
            }
        },
    ) { padding ->
        if (isLoadingPlans) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Color(theme.accentColor), strokeWidth = 2.5.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Loading available workspace plans...",
                    color = Color(theme.secondaryTextColor),
                    fontSize = 14.sp,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Dart AppBar: back arrow only outside the registration flow.
            if (!isNewRegistration) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    AuthBackButton(onClick = onBack)
                }
            }

            Column(
                modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header badge.
                Row(
                    modifier = Modifier
                        .background(
                            Color(argbWithAlpha(theme.accentColor, 0.12)),
                            RoundedCornerShape(20.dp),
                        )
                        .border(
                            1.dp,
                            Color(argbWithAlpha(theme.accentColor, 0.3)),
                            RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.WorkspacePremium,
                        contentDescription = null,
                        tint = Color(theme.accentColor),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isNewRegistration) "Step 2: Activate Workspace" else "Organization Subscription",
                        color = Color(theme.accentColor),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(16.dp))

                Text(
                    if (isNewRegistration) "Choose a Workspace Plan" else "Plans for $organizationName",
                    color = Color(theme.primaryTextColor),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Select the subscription plan that best fits \"$organizationName\". You can change or upgrade at any time.",
                    color = Color(theme.secondaryTextColor),
                    fontSize = 14.sp,
                    lineHeight = (14 * 1.4).sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))

                BillingCycleSwitcher(theme, isAnnual) { isAnnual = it }
                Spacer(Modifier.height(24.dp))

                errorMessage?.let { msg ->
                    Row(
                        modifier = Modifier
                            .padding(bottom = 20.dp)
                            .fillMaxWidth()
                            .background(Color(argbWithAlpha(theme.dangerColor, 0.1)), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(argbWithAlpha(theme.dangerColor, 0.3)), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = Color(theme.dangerColor),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            msg,
                            color = Color(theme.dangerColor),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                if (plans.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .padding(bottom = 20.dp)
                            .fillMaxWidth()
                            .background(Color(argbWithAlpha(theme.surfaceColor, 0.5)), RoundedCornerShape(16.dp))
                            .border(1.dp, Color(argbWithAlpha(theme.dividerColor, 0.25)), RoundedCornerShape(16.dp))
                            .padding(horizontal = 24.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Rounded.LayersClear,
                            contentDescription = null,
                            tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.7)),
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No Subscription Plans Found",
                            color = Color(theme.primaryTextColor),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "No active product plans were returned from the server.",
                            color = Color(theme.secondaryTextColor),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { loadPlansNow() }, enabled = !isLoadingPlans) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry Loading Plans")
                        }
                    }
                } else {
                    plans.forEach { plan ->
                        PlanCard(
                            theme = theme,
                            plan = plan,
                            isAnnual = isAnnual,
                            isSelected = selectedPlanId == (plan["id"]?.toString() ?: ""),
                            onSelect = { selectedPlanId = plan["id"]?.toString() ?: "" },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { handleSubscribe() },
                    enabled = !isSubmitting && selectedPlanId != null && plans.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(theme.accentColor),
                        contentColor = Color(theme.onAccentColor),
                        disabledContainerColor = Color(argbWithAlpha(theme.accentColor, 0.6)),
                        disabledContentColor = Color(theme.onAccentColor),
                    ),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.2.dp,
                            color = Color(theme.onAccentColor),
                        )
                    } else {
                        Text(
                            buttonText(),
                            color = Color(theme.onAccentColor),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color(theme.onAccentColor),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (isNewRegistration && anyFreePlan()) {
                    val freePlan = plans.firstOrNull {
                        it["tier"] == "free" || (((it["monthly_price"] as? Number)?.toDouble()) ?: 1.0) == 0.0
                    } ?: plans.first()
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            selectedPlanId = freePlan["id"]?.toString()
                            // Dart calls _handleSubscribe right after setState;
                            // subscribe with the freshly selected id directly.
                            val planId = freePlan["id"]?.toString()
                            if (planId != null && !isSubmitting) {
                                isSubmitting = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        subscribe(organizationId, planId, if (isAnnual) 12 else 1)
                                        val planName = (freePlan["name"] ?: "Plan").toString()
                                        launch {
                                            snackbarHostState.showSnackbar(
                                                "Subscribed to $planName! Workspace \"$organizationName\" is now active.",
                                            )
                                        }
                                        onSubscribed(planName)
                                    } catch (e: Exception) {
                                        errorMessage = friendlyError(e)
                                        isSubmitting = false
                                    }
                                }
                            }
                        },
                        enabled = !isSubmitting,
                    ) {
                        Text(
                            "Start with Free Plan (${(freePlan["name"] ?: "Free")})",
                            color = Color(theme.secondaryTextColor),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun BillingCycleSwitcher(theme: ThemeConfig, isAnnual: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .background(Color(argbWithAlpha(theme.surfaceColor, 0.6)), RoundedCornerShape(14.dp))
            .border(1.dp, Color(argbWithAlpha(theme.dividerColor, 0.25)), RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        BillingTab(theme, "Monthly Billing", !isAnnual, null) { onChange(false) }
        BillingTab(theme, "Annual Billing", isAnnual, "Save 20%") { onChange(true) }
    }
}

@Composable
private fun BillingTab(
    theme: ThemeConfig,
    label: String,
    isSelected: Boolean,
    badge: String?,
    onTap: () -> Unit,
) {
    val background by animateColorAsState(
        if (isSelected) Color(theme.accentColor) else Color.Transparent,
        animationSpec = tween(200),
        label = "billingTabBg",
    )
    Row(
        modifier = Modifier
            .background(background, RoundedCornerShape(10.dp))
            .clickable(onClick = onTap)
            .padding(vertical = 8.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (isSelected) Color(theme.onAccentColor) else Color(theme.secondaryTextColor),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
        if (badge != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                badge,
                color = if (isSelected) Color.White else SuccessGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .background(
                        if (isSelected) Color(argbWithAlpha(0xFFFFFFFF, 0.25))
                        else Color(argbWithAlpha(0xFF10B981, 0.15)),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PlanCard(
    theme: ThemeConfig,
    plan: Map<String, Any?>,
    isAnnual: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val name = plan["name"]?.toString() ?: "Plan"
    val isHighlighted = plan["highlighted"] == true
    val monthlyPrice = (plan["monthly_price"] as? Number)?.toDouble() ?: 0.0
    val annualPrice = (plan["annual_price"] as? Number)?.toDouble() ?: 0.0
    val currency = plan["currency"]?.toString() ?: "INR"
    val currencySymbol = if (currency.uppercase() == "INR") "₹" else "$"
    val price = if (isAnnual) annualPrice else monthlyPrice
    val period = if (isAnnual) "/year" else "/month"
    val description = plan["description"]?.toString()
    val productName = plan["product_name"]?.toString()

    val features = mutableListOf<String>()
    (plan["features"] as? List<*>)?.forEach { f ->
        val text = f?.toString()?.trim()
        if (!text.isNullOrEmpty()) features.add(text)
    }

    val cardBackground by animateColorAsState(
        if (isSelected) Color(argbWithAlpha(theme.accentColor, 0.08))
        else Color(argbWithAlpha(theme.surfaceColor, 0.6)),
        animationSpec = tween(200),
        label = "planCardBg",
    )
    val borderColor = when {
        isSelected -> Color(theme.accentColor)
        isHighlighted -> Color(argbWithAlpha(theme.accentColor, 0.4))
        else -> Color(argbWithAlpha(theme.dividerColor, 0.25))
    }

    Column(
        modifier = Modifier
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = Color(argbWithAlpha(theme.accentColor, 0.15)),
                    spotColor = Color(argbWithAlpha(theme.accentColor, 0.15)),
                ) else Modifier,
            )
            .background(cardBackground, RoundedCornerShape(18.dp))
            .border(if (isSelected) 2.dp else 1.2.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onSelect)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    color = Color(theme.primaryTextColor),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (!productName.isNullOrEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        productName.uppercase(),
                        color = Color(theme.accentColor),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier
                            .background(Color(argbWithAlpha(theme.accentColor, 0.12)), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp),
                    )
                }
                if (isHighlighted) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "RECOMMENDED",
                        color = Color(theme.onAccentColor),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier
                            .background(Color(theme.accentColor), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(
                        2.dp,
                        if (isSelected) Color(theme.accentColor)
                        else Color(argbWithAlpha(theme.secondaryTextColor, 0.4)),
                        CircleShape,
                    )
                    .background(if (isSelected) Color(theme.accentColor) else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color(theme.onAccentColor),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                // Dart interpolates the raw double: 499.0 renders "₹499.0".
                if (price <= 0) "Free" else "$currencySymbol$price",
                color = Color(theme.primaryTextColor),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
            )
            if (price > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    period,
                    color = Color(theme.secondaryTextColor),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        if (!description.isNullOrEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(description, color = Color(theme.secondaryTextColor), fontSize = 13.sp)
        }
        if (features.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Color(argbWithAlpha(theme.dividerColor, 0.2)), thickness = 1.dp)
            Spacer(Modifier.height(12.dp))
            features.forEach { feature ->
                Row(Modifier.padding(bottom = 8.dp)) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        feature,
                        color = Color(argbWithAlpha(theme.primaryTextColor, 0.85)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
