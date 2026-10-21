import 'package:flutter/material.dart';

import '../../data/services/api_backend_service.dart';
import '../../injection/locator.dart';
import '../../ui/core/theme/theme_config.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../chats/main_navigation_shell.dart';

class PlanSubscriptionScreen extends StatefulWidget {
  final String organizationId;
  final String organizationName;
  final bool isNewRegistration;

  const PlanSubscriptionScreen({
    super.key,
    required this.organizationId,
    required this.organizationName,
    this.isNewRegistration = false,
  });

  @override
  State<PlanSubscriptionScreen> createState() => _PlanSubscriptionScreenState();
}

class _PlanSubscriptionScreenState extends State<PlanSubscriptionScreen> {
  final ApiBackendService _backend = locator<ApiBackendService>();

  bool _isLoadingPlans = true;
  bool _isSubmitting = false;
  bool _isAnnual = false;
  String? _selectedPlanId;
  String? _errorMessage;
  List<Map<String, dynamic>> _plans = [];

  @override
  void initState() {
    super.initState();
    _loadPlans();
  }

  Future<void> _loadPlans() async {
    setState(() {
      _isLoadingPlans = true;
      _errorMessage = null;
    });

    try {
      final plans = await _backend.getAvailablePlans(
        organizationId: widget.organizationId,
      );
      if (!mounted) return;
      setState(() {
        _plans = plans;
        if (_plans.isNotEmpty) {
          // Default to the first free or highlighted plan
          final defaultPlan = _plans.firstWhere(
            (p) => p['highlighted'] == true || p['tier'] == 'free',
            orElse: () => _plans.first,
          );
          _selectedPlanId = defaultPlan['id']?.toString();
        } else {
          _selectedPlanId = null;
        }
        _isLoadingPlans = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _errorMessage = e.toString().replaceFirst('Exception: ', '');
        _plans = [];
        _selectedPlanId = null;
        _isLoadingPlans = false;
      });
    }
  }

  Future<void> _handleSubscribe() async {
    if (_selectedPlanId == null || _isSubmitting) return;

    setState(() {
      _isSubmitting = true;
      _errorMessage = null;
    });

    try {
      final success = await _backend.subscribeToPlan(
        organizationId: widget.organizationId,
        planId: _selectedPlanId!,
        durationMonths: _isAnnual ? 12 : 1,
      );

      if (!mounted) return;

      if (success) {
        final planObj = _plans.firstWhere(
          (p) => p['id']?.toString() == _selectedPlanId,
          orElse: () => {'name': 'Selected Plan'},
        );
        final planName = planObj['name'] ?? 'Plan';

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: const Color(0xFF10B981),
            content: Text(
              'Subscribed to $planName! Workspace "${widget.organizationName}" is now active.',
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            behavior: SnackBarBehavior.floating,
          ),
        );

        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(builder: (_) => const MainNavigationShell()),
          (route) => false,
        );
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _errorMessage = e.toString().replaceFirst('Exception: ', '');
        _isSubmitting = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = locator<ThemeController>().globalTheme;

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: widget.isNewRegistration
            ? null
            : IconButton(
                icon: Icon(
                  Icons.arrow_back_rounded,
                  color: theme.primaryTextColor,
                ),
                onPressed: () => Navigator.of(context).pop(),
              ),
        automaticallyImplyLeading: !widget.isNewRegistration,
      ),
      body: SafeArea(
        child: _isLoadingPlans
            ? Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    CircularProgressIndicator(
                      color: theme.accentColor,
                      strokeWidth: 2.5,
                    ),
                    const SizedBox(height: 16),
                    Text(
                      'Loading available workspace plans...',
                      style: TextStyle(
                        color: theme.secondaryTextColor,
                        fontSize: 14,
                      ),
                    ),
                  ],
                ),
              )
            : SingleChildScrollView(
                padding: const EdgeInsets.symmetric(
                  horizontal: 24.0,
                  vertical: 12.0,
                ),
                child: Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 580),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.center,
                      children: [
                        // Header Badge
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 14,
                            vertical: 6,
                          ),
                          decoration: BoxDecoration(
                            color: theme.accentColor.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(20),
                            border: Border.all(
                              color: theme.accentColor.withValues(alpha: 0.3),
                            ),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                Icons.workspace_premium_rounded,
                                size: 16,
                                color: theme.accentColor,
                              ),
                              const SizedBox(width: 6),
                              Text(
                                widget.isNewRegistration
                                    ? 'Step 2: Activate Workspace'
                                    : 'Organization Subscription',
                                style: TextStyle(
                                  color: theme.accentColor,
                                  fontSize: 12.5,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 16),

                        Text(
                          widget.isNewRegistration
                              ? 'Choose a Workspace Plan'
                              : 'Plans for ${widget.organizationName}',
                          style: TextStyle(
                            color: theme.primaryTextColor,
                            fontSize: 26,
                            fontWeight: FontWeight.w800,
                            letterSpacing: -0.4,
                          ),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Select the subscription plan that best fits "${widget.organizationName}". You can change or upgrade at any time.',
                          style: TextStyle(
                            color: theme.secondaryTextColor,
                            fontSize: 14,
                            height: 1.4,
                          ),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 24),

                        // Billing Cycle Switcher (Monthly / Annual)
                        _buildBillingCycleSwitcher(theme),
                        const SizedBox(height: 24),

                        if (_errorMessage != null)
                          Container(
                            padding: const EdgeInsets.all(12),
                            margin: const EdgeInsets.only(bottom: 20),
                            decoration: BoxDecoration(
                              color: theme.dangerColor.withValues(alpha: 0.1),
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(
                                color: theme.dangerColor.withValues(alpha: 0.3),
                              ),
                            ),
                            child: Row(
                              children: [
                                Icon(
                                  Icons.error_outline_rounded,
                                  color: theme.dangerColor,
                                  size: 20,
                                ),
                                const SizedBox(width: 10),
                                Expanded(
                                  child: Text(
                                    _errorMessage!,
                                    style: TextStyle(
                                      color: theme.dangerColor,
                                      fontSize: 13,
                                      fontWeight: FontWeight.w500,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),

                        // Plan Cards List from API
                        if (_plans.isEmpty)
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 24,
                              vertical: 36,
                            ),
                            margin: const EdgeInsets.only(bottom: 20),
                            decoration: BoxDecoration(
                              color: theme.surfaceColor.withValues(alpha: 0.5),
                              borderRadius: BorderRadius.circular(16),
                              border: Border.all(
                                color: theme.dividerColor.withValues(alpha: 0.25),
                              ),
                            ),
                            child: Column(
                              children: [
                                Icon(
                                  Icons.layers_clear_rounded,
                                  size: 44,
                                  color: theme.secondaryTextColor.withValues(alpha: 0.7),
                                ),
                                const SizedBox(height: 12),
                                Text(
                                  'No Subscription Plans Found',
                                  style: TextStyle(
                                    color: theme.primaryTextColor,
                                    fontSize: 16,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                                const SizedBox(height: 6),
                                Text(
                                  'No active product plans were returned from the server.',
                                  style: TextStyle(
                                    color: theme.secondaryTextColor,
                                    fontSize: 13,
                                  ),
                                  textAlign: TextAlign.center,
                                ),
                                const SizedBox(height: 16),
                                OutlinedButton.icon(
                                  onPressed: _isLoadingPlans ? null : _loadPlans,
                                  icon: const Icon(Icons.refresh_rounded, size: 18),
                                  label: const Text('Retry Loading Plans'),
                                ),
                              ],
                            ),
                          )
                        else
                          ..._plans.map((plan) => _buildPlanCard(theme, plan)),
                        const SizedBox(height: 16),

                        // Action Button
                        SizedBox(
                          width: double.infinity,
                          height: 52,
                          child: ElevatedButton(
                            onPressed: (_isSubmitting || _selectedPlanId == null || _plans.isEmpty)
                                ? null
                                : _handleSubscribe,
                            style: ElevatedButton.styleFrom(
                              backgroundColor: theme.accentColor,
                              foregroundColor: theme.onAccentColor,
                              disabledBackgroundColor:
                                  theme.accentColor.withValues(alpha: 0.6),
                              elevation: 2,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(16),
                              ),
                            ),
                            child: _isSubmitting
                                ? SizedBox(
                                    width: 22,
                                    height: 22,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2.2,
                                      valueColor: AlwaysStoppedAnimation<Color>(
                                        theme.onAccentColor,
                                      ),
                                    ),
                                  )
                                : Row(
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    children: [
                                      Text(
                                        _getButtonText(),
                                        style: TextStyle(
                                          color: theme.onAccentColor,
                                          fontSize: 16,
                                          fontWeight: FontWeight.w700,
                                        ),
                                      ),
                                      const SizedBox(width: 8),
                                      Icon(
                                        Icons.arrow_forward_rounded,
                                        size: 18,
                                        color: theme.onAccentColor,
                                      ),
                                    ],
                                  ),
                          ),
                        ),

                        if (widget.isNewRegistration &&
                            _plans.any((p) => p['tier'] == 'free' || ((p['monthly_price'] as num?)?.toDouble() ?? 1.0) == 0.0)) ...[
                          const SizedBox(height: 16),
                          TextButton(
                            onPressed: _isSubmitting
                                ? null
                                : () {
                                    final freePlan = _plans.firstWhere(
                                      (p) => p['tier'] == 'free' || ((p['monthly_price'] as num?)?.toDouble() ?? 1.0) == 0.0,
                                      orElse: () => _plans.first,
                                    );
                                    setState(() {
                                      _selectedPlanId =
                                          freePlan['id']?.toString();
                                    });
                                    _handleSubscribe();
                                  },
                            child: Text(
                              'Start with Free Plan (${_plans.firstWhere((p) => p['tier'] == 'free' || ((p['monthly_price'] as num?)?.toDouble() ?? 1.0) == 0.0)['name'] ?? 'Free'})',
                              style: TextStyle(
                                color: theme.secondaryTextColor,
                                fontSize: 13.5,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ],
                        const SizedBox(height: 24),
                      ],
                    ),
                  ),
                ),
              ),
      ),
    );
  }

  String _getButtonText() {
    if (_plans.isEmpty) {
      return 'No Plans Available';
    }
    final selected = _plans.firstWhere(
      (p) => p['id']?.toString() == _selectedPlanId,
      orElse: () => {},
    );
    final isFree = selected['tier'] == 'free' ||
        (selected['monthly_price'] != null &&
            (selected['monthly_price'] as num) == 0);
    if (isFree) {
      return 'Activate Free Plan';
    }
    return 'Subscribe & Activate Workspace';
  }

  Widget _buildBillingCycleSwitcher(ThemeConfig theme) {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: theme.surfaceColor.withValues(alpha: 0.6),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: theme.dividerColor.withValues(alpha: 0.25),
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildBillingTab(
            theme: theme,
            label: 'Monthly Billing',
            isSelected: !_isAnnual,
            onTap: () => setState(() => _isAnnual = false),
          ),
          _buildBillingTab(
            theme: theme,
            label: 'Annual Billing',
            badge: 'Save 20%',
            isSelected: _isAnnual,
            onTap: () => setState(() => _isAnnual = true),
          ),
        ],
      ),
    );
  }

  Widget _buildBillingTab({
    required ThemeConfig theme,
    required String label,
    required bool isSelected,
    required VoidCallback onTap,
    String? badge,
  }) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(10),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 14),
          decoration: BoxDecoration(
            color: isSelected ? theme.accentColor : Colors.transparent,
            borderRadius: BorderRadius.circular(10),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                label,
                style: TextStyle(
                  color:
                      isSelected ? theme.onAccentColor : theme.secondaryTextColor,
                  fontSize: 13,
                  fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                ),
              ),
              if (badge != null) ...[
                const SizedBox(width: 6),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: isSelected
                        ? Colors.white.withValues(alpha: 0.25)
                        : const Color(0xFF10B981).withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    badge,
                    style: TextStyle(
                      color: isSelected
                          ? Colors.white
                          : const Color(0xFF10B981),
                      fontSize: 10,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPlanCard(ThemeConfig theme, Map<String, dynamic> plan) {
    final planId = plan['id']?.toString() ?? '';
    final name = plan['name']?.toString() ?? 'Plan';
    final isHighlighted = plan['highlighted'] == true;
    final isSelected = _selectedPlanId == planId;
    final monthlyPrice = (plan['monthly_price'] as num?)?.toDouble() ?? 0.0;
    final annualPrice = (plan['annual_price'] as num?)?.toDouble() ?? 0.0;
    final currency = plan['currency']?.toString() ?? 'INR';
    final currencySymbol = currency.toUpperCase() == 'INR' ? '₹' : '\$';
    final price = _isAnnual ? annualPrice : monthlyPrice;
    final period = _isAnnual ? '/year' : '/month';
    final description = plan['description']?.toString();
    final productName = plan['product_name']?.toString();

    final List<String> features = [];
    final rawFeatures = plan['features'];
    if (rawFeatures is List) {
      for (final f in rawFeatures) {
        if (f != null && f.toString().trim().isNotEmpty) {
          features.add(f.toString().trim());
        }
      }
    }

    return GestureDetector(
      onTap: () => setState(() => _selectedPlanId = planId),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        margin: const EdgeInsets.only(bottom: 16),
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: isSelected
              ? theme.accentColor.withValues(alpha: 0.08)
              : theme.surfaceColor.withValues(alpha: 0.6),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(
            color: isSelected
                ? theme.accentColor
                : (isHighlighted
                    ? theme.accentColor.withValues(alpha: 0.4)
                    : theme.dividerColor.withValues(alpha: 0.25)),
            width: isSelected ? 2.0 : 1.2,
          ),
          boxShadow: isSelected
              ? [
                  BoxShadow(
                    color: theme.accentColor.withValues(alpha: 0.15),
                    blurRadius: 10,
                    offset: const Offset(0, 4),
                  ),
                ]
              : null,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Row(
                    children: [
                      Text(
                        name,
                        style: TextStyle(
                          color: theme.primaryTextColor,
                          fontSize: 18,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      if (productName != null && productName.isNotEmpty) ...[
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 7,
                            vertical: 2.5,
                          ),
                          decoration: BoxDecoration(
                            color: theme.accentColor.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text(
                            productName.toUpperCase(),
                            style: TextStyle(
                              color: theme.accentColor,
                              fontSize: 9.5,
                              fontWeight: FontWeight.w700,
                              letterSpacing: 0.3,
                            ),
                          ),
                        ),
                      ],
                      if (isHighlighted) ...[
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 3,
                          ),
                          decoration: BoxDecoration(
                            color: theme.accentColor,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            'RECOMMENDED',
                            style: TextStyle(
                              color: theme.onAccentColor,
                              fontSize: 9.5,
                              fontWeight: FontWeight.w800,
                              letterSpacing: 0.4,
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                Container(
                  width: 22,
                  height: 22,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: isSelected
                          ? theme.accentColor
                          : theme.secondaryTextColor.withValues(alpha: 0.4),
                      width: 2,
                    ),
                    color: isSelected ? theme.accentColor : Colors.transparent,
                  ),
                  child: isSelected
                      ? Icon(
                          Icons.check_rounded,
                          size: 14,
                          color: theme.onAccentColor,
                        )
                      : null,
                ),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text(
                  price <= 0 ? 'Free' : '$currencySymbol$price',
                  style: TextStyle(
                    color: theme.primaryTextColor,
                    fontSize: 26,
                    fontWeight: FontWeight.w900,
                    letterSpacing: -0.5,
                  ),
                ),
                if (price > 0) ...[
                  const SizedBox(width: 4),
                  Text(
                    period,
                    style: TextStyle(
                      color: theme.secondaryTextColor,
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              ],
            ),
            if (description != null && description.isNotEmpty) ...[
              const SizedBox(height: 6),
              Text(
                description,
                style: TextStyle(
                  color: theme.secondaryTextColor,
                  fontSize: 13,
                ),
              ),
            ],
            if (features.isNotEmpty) ...[
              const SizedBox(height: 14),
              Divider(
                color: theme.dividerColor.withValues(alpha: 0.2),
                height: 1,
              ),
              const SizedBox(height: 12),
              ...features.map((feature) => Padding(
                    padding: const EdgeInsets.only(bottom: 8.0),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(
                          Icons.check_circle_rounded,
                          size: 16,
                          color: const Color(0xFF10B981),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            feature,
                            style: TextStyle(
                              color: theme.primaryTextColor.withValues(alpha: 0.85),
                              fontSize: 13,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ),
                      ],
                    ),
                  )),
            ],
          ],
        ),
      ),
    );
  }
}
