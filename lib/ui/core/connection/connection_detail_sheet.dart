import 'package:flutter/material.dart';

import '../../../domain/models/connection_health.dart';
import '../../../data/services/connection_health_service.dart';
import '../../../injection/locator.dart';
import '../theme/semantic_colors.dart';
import 'connection_health_indicator.dart';

/// Modal bottom sheet presenting a clean, transparent diagnostic overview of
/// the application's connection health without exposing secrets or URLs.
class ConnectionDetailSheet extends StatefulWidget {
  final ConnectionHealthService? service;

  const ConnectionDetailSheet({super.key, this.service});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => const ConnectionDetailSheet(),
    );
  }

  @override
  State<ConnectionDetailSheet> createState() => _ConnectionDetailSheetState();
}

class _ConnectionDetailSheetState extends State<ConnectionDetailSheet> {
  late final ConnectionHealthService _service;
  bool _testing = false;

  @override
  void initState() {
    super.initState();
    _service = widget.service ??
        (locator.isRegistered<ConnectionHealthService>()
            ? locator<ConnectionHealthService>()
            : ConnectionHealthService());
    _service.addListener(_onServiceChanged);
  }

  void _onServiceChanged() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _service.removeListener(_onServiceChanged);
    super.dispose();
  }

  Future<void> _runTest() async {
    setState(() => _testing = true);
    await _service.probeConnection();
    if (mounted) setState(() => _testing = false);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final appColors = theme.extension<AppColors>();
    final state = _service.state;

    final statusColor = switch (state.health) {
      ConnectionHealth.excellent =>
        appColors?.connectionExcellent ?? const Color(0xFF10B981),
      ConnectionHealth.weak =>
        appColors?.connectionWeak ?? const Color(0xFFF59E0B),
      ConnectionHealth.poor =>
        appColors?.connectionPoor ?? const Color(0xFFEF4444),
      ConnectionHealth.offline =>
        appColors?.connectionOffline ?? const Color(0xFF71717A),
    };

    return Container(
      decoration: BoxDecoration(
        color: appColors?.surface ?? theme.scaffoldBackgroundColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.2),
            blurRadius: 20,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      padding: EdgeInsets.fromLTRB(
        20,
        12,
        20,
        MediaQuery.of(context).padding.bottom + 20,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Drag handle
          Center(
            child: Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: appColors?.border ?? Colors.grey.shade400,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 18),

          // Header
          Row(
            children: [
              ConnectionHealthIndicator(health: state.health, size: 22),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Connection Health',
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                        color: appColors?.foreground,
                      ),
                    ),
                    Text(
                      state.displaySubtitle,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: appColors?.foregroundSecondary,
                      ),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: statusColor.withValues(alpha: 0.3)),
                ),
                child: Text(
                  state.displayTitle,
                  style: TextStyle(
                    color: statusColor,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),

          const SizedBox(height: 20),
          Divider(color: appColors?.divider, height: 1),
          const SizedBox(height: 16),

          // Metrics list
          _MetricRow(
            label: 'Network Transport',
            value: switch (state.transport) {
              NetworkTransportType.wifi => 'Wi-Fi',
              NetworkTransportType.cellular => 'Mobile Data',
              NetworkTransportType.ethernet => 'Ethernet',
              NetworkTransportType.vpn => 'VPN',
              NetworkTransportType.other => 'Available',
              NetworkTransportType.none => 'Disconnected',
            },
            icon: switch (state.transport) {
              NetworkTransportType.wifi => Icons.wifi_rounded,
              NetworkTransportType.cellular => Icons.signal_cellular_alt_rounded,
              NetworkTransportType.ethernet => Icons.lan_rounded,
              NetworkTransportType.vpn => Icons.vpn_key_rounded,
              _ => Icons.signal_wifi_off_rounded,
            },
            appColors: appColors,
          ),
          _MetricRow(
            label: 'Chat Backend Service',
            value: state.backendReachable
                ? 'Operational'
                : (state.internetReachable ? 'Unreachable' : 'No Internet'),
            icon: Icons.cloud_done_rounded,
            valueColor: state.backendReachable
                ? appColors?.connectionExcellent
                : appColors?.connectionPoor,
            appColors: appColors,
          ),
          _MetricRow(
            label: 'Realtime Sync',
            value: switch (state.realtimeStatus) {
              RealtimeSocketStatus.connected => 'Connected',
              RealtimeSocketStatus.connecting => 'Connecting…',
              RealtimeSocketStatus.reconnecting => 'Reconnecting…',
              RealtimeSocketStatus.disconnected => 'Disconnected',
            },
            icon: Icons.sync_rounded,
            valueColor: state.realtimeStatus == RealtimeSocketStatus.connected
                ? appColors?.connectionExcellent
                : (state.realtimeStatus == RealtimeSocketStatus.reconnecting
                    ? appColors?.connectionWeak
                    : appColors?.foregroundSecondary),
            appColors: appColors,
          ),
          _MetricRow(
            label: 'Latency',
            value: state.latency != null
                ? '${state.latency!.inMilliseconds} ms'
                : '—',
            icon: Icons.speed_rounded,
            appColors: appColors,
          ),
          _MetricRow(
            label: 'Pending Outgoing Messages',
            value: '${state.queuedMessagesCount}',
            icon: Icons.mark_chat_unread_rounded,
            valueColor: state.queuedMessagesCount > 0
                ? appColors?.connectionWeak
                : appColors?.foreground,
            appColors: appColors,
          ),

          const SizedBox(height: 20),

          // Test connection action button
          SizedBox(
            height: 44,
            child: ElevatedButton.icon(
              onPressed: _testing ? null : _runTest,
              icon: _testing
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.refresh_rounded, size: 18),
              label: Text(_testing ? 'Testing…' : 'Test Connection'),
              style: ElevatedButton.styleFrom(
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _MetricRow extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  final Color? valueColor;
  final AppColors? appColors;

  const _MetricRow({
    required this.label,
    required this.value,
    required this.icon,
    this.valueColor,
    this.appColors,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Icon(icon, size: 18, color: appColors?.iconSecondary ?? Colors.grey),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              label,
              style: TextStyle(
                fontSize: 13.5,
                color: appColors?.foregroundSecondary,
              ),
            ),
          ),
          Text(
            value,
            style: TextStyle(
              fontSize: 13.5,
              fontWeight: FontWeight.w600,
              color: valueColor ?? appColors?.foreground,
            ),
          ),
        ],
      ),
    );
  }
}
