import 'package:flutter/material.dart';

import '../../../domain/models/connection_health.dart';
import '../../../data/services/connection_health_service.dart';
import '../../../injection/locator.dart';
import '../theme/semantic_colors.dart';
import 'connection_detail_sheet.dart';
import 'connection_health_indicator.dart';

/// Subtle global status strip that surfaces degraded/offline network states
/// without permanently wasting vertical screen real-estate when healthy.
class GlobalConnectionBanner extends StatefulWidget {
  final ConnectionHealthService? service;

  const GlobalConnectionBanner({super.key, this.service});

  @override
  State<GlobalConnectionBanner> createState() => _GlobalConnectionBannerState();
}

class _GlobalConnectionBannerState extends State<GlobalConnectionBanner>
    with SingleTickerProviderStateMixin {
  late final ConnectionHealthService _service;
  late final AnimationController _controller;
  late final Animation<double> _expandAnimation;
  ConnectionHealth _lastHealth = ConnectionHealth.excellent;
  bool _showRestoredToast = false;

  @override
  void initState() {
    super.initState();
    _service = widget.service ??
        (locator.isRegistered<ConnectionHealthService>()
            ? locator<ConnectionHealthService>()
            : ConnectionHealthService());
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 280),
    );
    _expandAnimation = CurvedAnimation(
      parent: _controller,
      curve: Curves.easeOutCubic,
      reverseCurve: Curves.easeInCubic,
    );

    _lastHealth = _service.health;
    _service.addListener(_onServiceChanged);
    _updateBannerState(animate: false);
  }

  void _onServiceChanged() {
    final newHealth = _service.health;
    if (_lastHealth != ConnectionHealth.excellent &&
        newHealth == ConnectionHealth.excellent) {
      // Temporarily show "Connected" restored pill
      setState(() => _showRestoredToast = true);
      Future.delayed(const Duration(seconds: 3), () {
        if (mounted) setState(() => _showRestoredToast = false);
      });
    }
    _lastHealth = newHealth;
    _updateBannerState(animate: true);
  }

  void _updateBannerState({required bool animate}) {
    final shouldShow = _service.health != ConnectionHealth.excellent || _showRestoredToast;
    if (shouldShow) {
      if (animate) {
        _controller.forward();
      } else {
        _controller.value = 1.0;
      }
    } else {
      if (animate) {
        _controller.reverse();
      } else {
        _controller.value = 0.0;
      }
    }
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _service.removeListener(_onServiceChanged);
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = _service.state;
    if (state.health == ConnectionHealth.excellent && !_showRestoredToast && _controller.isDismissed) {
      return const SizedBox.shrink();
    }

    final appColors = Theme.of(context).extension<AppColors>();
    final (bgColor, fgColor, icon, text) = switch (state.health) {
      ConnectionHealth.excellent => (
        appColors?.connectionExcellent ?? const Color(0xFF10B981),
        Colors.white,
        Icons.check_circle_rounded,
        'Connection restored',
      ),
      ConnectionHealth.weak => (
        appColors?.connectionWeak.withValues(alpha: 0.15) ?? const Color(0x26F59E0B),
        appColors?.connectionWeak ?? const Color(0xFFF59E0B),
        Icons.wifi_rounded,
        'Weak connection — messages may take longer',
      ),
      ConnectionHealth.poor => (
        appColors?.connectionPoor.withValues(alpha: 0.15) ?? const Color(0x26EF4444),
        appColors?.connectionPoor ?? const Color(0xFFEF4444),
        Icons.cloud_off_rounded,
        state.backendReachable
            ? 'Connection unstable — retrying automatically'
            : 'Chat service unreachable — retrying…',
      ),
      ConnectionHealth.offline => (
        appColors?.connectionOffline.withValues(alpha: 0.18) ?? const Color(0x2E71717A),
        appColors?.foreground ?? Colors.white,
        Icons.signal_wifi_off_rounded,
        state.queuedMessagesCount > 0
            ? 'No internet — ${state.queuedMessagesCount} messages waiting to send'
            : 'No internet connection — waiting for network',
      ),
    };

    return SizeTransition(
      sizeFactor: _expandAnimation,
      child: GestureDetector(
        onTap: () => ConnectionDetailSheet.show(context),
        behavior: HitTestBehavior.opaque,
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          color: bgColor,
          child: Row(
            children: [
              Icon(icon, size: 16, color: fgColor),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  text,
                  style: TextStyle(
                    fontSize: 12.5,
                    fontWeight: FontWeight.w600,
                    color: fgColor,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(width: 6),
              ConnectionHealthIndicator(health: state.health, size: 14),
            ],
          ),
        ),
      ),
    );
  }
}
