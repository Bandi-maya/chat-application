import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../ui/core/design_system/design_system.dart';
import '../design_system/specular_glass_card.dart';
import '../design_system/spring_interactive_scale.dart';
import 'audio_capability_service.dart';
import 'audio_policy_coordinator.dart';
import 'audio_settings_controller.dart';
import 'audio_types.dart';

/// Apple-styled Audio & Dual-Speaker Routing settings screen.
/// Features tactile spring micro-interactions, capability badges, and diagnostic verification table.
class AudioSettingsScreen extends StatelessWidget {
  final AudioSettingsController settingsController;
  final AudioCapabilityService capabilityService;
  final AudioPolicyCoordinator coordinator;

  const AudioSettingsScreen({
    super.key,
    required this.settingsController,
    required this.capabilityService,
    required this.coordinator,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: colors.background,
      appBar: AppBar(
        title: const Text('Audio & Speakers'),
        backgroundColor: colors.background,
        elevation: 0,
        surfaceTintColor: Colors.transparent,
      ),
      body: ListenableBuilder(
        listenable: Listenable.merge([settingsController, capabilityService, coordinator]),
        builder: (context, _) {
          final dualStatus = capabilityService.communicationDualStatus;
          final activeRoute = capabilityService.activeCommunicationRoute;
          final availableRoutes = capabilityService.availableCommunicationRoutes;

          return ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            children: [
              // 1. Status Hero Banner
              _buildCapabilityHero(
                context: context,
                colors: colors,
                isDark: isDark,
                dualStatus: dualStatus,
                activeRoute: activeRoute,
              ),
              const SizedBox(height: 20),

              // 2. Audio Routing Preferences Section
              _buildSectionHeader(context, 'ROUTING PREFERENCE', colors),
              const SizedBox(height: 8),
              _buildGroupedCard(
                colors: colors,
                isDark: isDark,
                children: [
                  _buildRadioRow(
                    context: context,
                    colors: colors,
                    title: 'Automatic (Recommended)',
                    subtitle: 'Uses native device stereo and smart VoIP routing',
                    selected: settingsController.preferenceMode == AudioPreferenceMode.automatic,
                    onTap: () => settingsController.setPreferenceMode(AudioPreferenceMode.automatic),
                  ),
                  _buildDivider(colors),
                  _buildRadioRow(
                    context: context,
                    colors: colors,
                    title: 'Standard Stereo Playback',
                    subtitle: 'Preserves operating system standard media routes',
                    selected: settingsController.preferenceMode == AudioPreferenceMode.preferStereo,
                    onTap: () => settingsController.setPreferenceMode(AudioPreferenceMode.preferStereo),
                  ),
                  _buildDivider(colors),
                  _buildRadioRow(
                    context: context,
                    colors: colors,
                    title: 'Dual-Speaker Communication',
                    subtitle: dualStatus == DualOutputStatus.unsupported
                        ? 'Unsupported on this device hardware/OS'
                        : 'Engage earpiece + speakerphone on verified devices',
                    selected: settingsController.preferenceMode == AudioPreferenceMode.experimentalDualOutput,
                    enabled: dualStatus != DualOutputStatus.unsupported,
                    onTap: () => settingsController.setPreferenceMode(AudioPreferenceMode.experimentalDualOutput),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // 3. Available Routes Section
              _buildSectionHeader(context, 'ACTIVE & AVAILABLE ROUTES', colors),
              const SizedBox(height: 8),
              _buildGroupedCard(
                colors: colors,
                isDark: isDark,
                children: [
                  for (int i = 0; i < availableRoutes.length; i++) ...[
                    if (i > 0) _buildDivider(colors),
                    _buildRouteRow(
                      context: context,
                      colors: colors,
                      route: availableRoutes[i],
                      isActive: availableRoutes[i] == activeRoute,
                      onTap: () => capabilityService.setPreferredRoute(
                        AudioUseCase.communication,
                        availableRoutes[i],
                      ),
                    ),
                  ],
                ],
              ),
              const SizedBox(height: 24),

              // 4. Evidence-based Device Test Matrix
              _buildSectionHeader(context, 'VERIFIED DEVICE TEST DATASET', colors),
              const SizedBox(height: 8),
              _buildDeviceTestMatrix(context, colors, isDark),
              const SizedBox(height: 36),
            ],
          );
        },
      ),
    );
  }

  Widget _buildCapabilityHero({
    required BuildContext context,
    required dynamic colors,
    required bool isDark,
    required DualOutputStatus dualStatus,
    required AudioOutputRoute activeRoute,
  }) {
    final (badgeText, badgeBg, badgeFg) = switch (dualStatus) {
      DualOutputStatus.active => ('Active & Verified', const Color(0xFF34C759).withValues(alpha: 0.15), const Color(0xFF34C759)),
      DualOutputStatus.notVerified => ('Available (Standard Route)', colors.accent.withValues(alpha: 0.15), colors.accent),
      DualOutputStatus.unavailable => ('Temporarily Unavailable', const Color(0xFFFF9500).withValues(alpha: 0.15), const Color(0xFFFF9500)),
      DualOutputStatus.unsupported => ('Unsupported by OS/Hardware', colors.foregroundSecondary.withValues(alpha: 0.15), colors.foregroundSecondary),
      DualOutputStatus.failed => ('Activation Failed', const Color(0xFFFF3B30).withValues(alpha: 0.15), const Color(0xFFFF3B30)),
    };

    return SpecularGlassCard(
      blur: 12,
      borderRadius: 20,
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Icon(Icons.speaker_group_rounded, color: colors.accent, size: 26),
                  const SizedBox(width: 10),
                  Text(
                    'Dual-Speaker Engine',
                    style: TextStyle(
                      color: colors.foregroundPrimary,
                      fontWeight: FontWeight.w700,
                      fontSize: 17,
                    ),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: badgeBg,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: badgeFg.withValues(alpha: 0.3)),
                ),
                child: Text(
                  badgeText,
                  style: TextStyle(
                    color: badgeFg,
                    fontWeight: FontWeight.w600,
                    fontSize: 12,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            'Smart audio policy coordinates native earpiece and bottom loudspeaker routing across VoIP calls, media, and notification sounds without breaking Bluetooth or microphone AEC.',
            style: TextStyle(
              color: colors.foregroundSecondary,
              fontSize: 13,
              height: 1.4,
            ),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Text(
                'Active Output: ',
                style: TextStyle(color: colors.foregroundSecondary, fontSize: 13),
              ),
              Text(
                activeRoute.name,
                style: TextStyle(
                  color: colors.foregroundPrimary,
                  fontWeight: FontWeight.w600,
                  fontSize: 13,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildGroupedCard({
    required dynamic colors,
    required bool isDark,
    required List<Widget> children,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1C1C1E) : colors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: colors.borderSubtle, width: 0.8),
        boxShadow: [
          BoxShadow(
            color: colors.shadow.withValues(alpha: isDark ? 0.3 : 0.04),
            blurRadius: 10,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: children,
        ),
      ),
    );
  }

  Widget _buildSectionHeader(BuildContext context, String title, dynamic colors) {
    return Padding(
      padding: const EdgeInsets.only(left: 14, bottom: 2),
      child: Text(
        title,
        style: TextStyle(
          color: colors.foregroundSecondary,
          fontSize: 12,
          fontWeight: FontWeight.w600,
          letterSpacing: 0.6,
        ),
      ),
    );
  }

  Widget _buildRadioRow({
    required BuildContext context,
    required dynamic colors,
    required String title,
    required String subtitle,
    required bool selected,
    bool enabled = true,
    required VoidCallback onTap,
  }) {
    return SpringInteractiveScale(
      scaleDownFactor: 0.98,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: enabled
              ? () {
                  HapticFeedback.selectionClick();
                  onTap();
                }
              : null,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: TextStyle(
                          color: enabled ? colors.foregroundPrimary : colors.foregroundSecondary.withValues(alpha: 0.5),
                          fontWeight: FontWeight.w600,
                          fontSize: 15,
                        ),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        subtitle,
                        style: TextStyle(
                          color: colors.foregroundSecondary,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
                Icon(
                  selected ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded,
                  color: selected ? colors.accent : colors.foregroundSecondary.withValues(alpha: 0.4),
                  size: 22,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildRouteRow({
    required BuildContext context,
    required dynamic colors,
    required AudioOutputRoute route,
    required bool isActive,
    required VoidCallback onTap,
  }) {
    final (icon, name) = switch (route) {
      AudioOutputRoute.receiver => (Icons.phone_in_talk_rounded, 'Earpiece Receiver'),
      AudioOutputRoute.speaker => (Icons.volume_up_rounded, 'Bottom Loudspeaker'),
      AudioOutputRoute.bluetooth => (Icons.bluetooth_audio_rounded, 'Bluetooth Device'),
      AudioOutputRoute.wiredHeadset => (Icons.headphones_rounded, 'Wired Headset'),
      AudioOutputRoute.usb => (Icons.usb_rounded, 'USB Audio Interface'),
      AudioOutputRoute.systemDefault => (Icons.devices_other_rounded, 'System Default'),
    };

    return SpringInteractiveScale(
      scaleDownFactor: 0.98,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: () {
            HapticFeedback.selectionClick();
            onTap();
          },
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              children: [
                Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    color: colors.accent.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(icon, color: colors.accent, size: 20),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Text(
                    name,
                    style: TextStyle(
                      color: colors.foregroundPrimary,
                      fontWeight: FontWeight.w600,
                      fontSize: 14.5,
                    ),
                  ),
                ),
                if (isActive)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: const Color(0xFF34C759).withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Text(
                      'ACTIVE',
                      style: TextStyle(
                        color: Color(0xFF34C759),
                        fontWeight: FontWeight.w700,
                        fontSize: 11,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDeviceTestMatrix(BuildContext context, dynamic colors, bool isDark) {
    final records = AudioSettingsController.verifiedDeviceRecords;

    return Container(
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1C1C1E) : colors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: colors.borderSubtle, width: 0.8),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: ListView.separated(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          itemCount: records.length,
          separatorBuilder: (_, __) => _buildDivider(colors),
          itemBuilder: (context, idx) {
            final rec = records[idx];
            final status = rec['testStatus'] as String;
            final (color, icon) = switch (status) {
              'Verified Dual Output' => (const Color(0xFF34C759), Icons.check_circle_outline),
              'Native Stereo' => (const Color(0xFF007AFF), Icons.music_note_rounded),
              'Standard Single Route' => (const Color(0xFF8E8E93), Icons.radio_button_checked),
              'Unsupported or Failed' => (const Color(0xFFFF3B30), Icons.cancel_outlined),
              _ => (const Color(0xFFFF9500), Icons.help_outline),
            };

            return Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              child: Row(
                children: [
                  Icon(icon, color: color, size: 20),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Text(
                              rec['deviceModel'] as String,
                              style: TextStyle(
                                color: colors.foregroundPrimary,
                                fontWeight: FontWeight.w600,
                                fontSize: 13.5,
                              ),
                            ),
                            const SizedBox(width: 6),
                            Text(
                              '(${rec['osVersion']})',
                              style: TextStyle(
                                color: colors.foregroundSecondary,
                                fontSize: 11.5,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 2),
                        Text(
                          rec['verificationMethod'] as String,
                          style: TextStyle(
                            color: colors.foregroundSecondary,
                            fontSize: 11.5,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: color.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      status,
                      style: TextStyle(
                        color: color,
                        fontSize: 10.5,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _buildDivider(dynamic colors) {
    return Divider(
      height: 1,
      thickness: 0.8,
      color: colors.borderSubtle.withValues(alpha: 0.6),
      indent: 16,
      endIndent: 16,
    );
  }
}
