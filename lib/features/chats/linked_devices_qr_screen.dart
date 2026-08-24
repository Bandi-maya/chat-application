import 'dart:async';

import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/contact_relationship_service.dart';
import '../../domain/models/other_models.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/widgets/app_brand_icon.dart';
import 'chat_detail_screen.dart';

class LinkedDevicesQrScreen extends StatefulWidget {
  final ChatyDataStore dataStore;
  final ContactRelationshipService relationshipService;
  final ChatyPreferencesController preferencesController;
  final ThemeController themeController;
  final int initialIndex;
  final bool qrOnly;
  final bool devicesOnly;

  const LinkedDevicesQrScreen({
    super.key,
    required this.dataStore,
    required this.relationshipService,
    required this.preferencesController,
    required this.themeController,
    this.initialIndex = 0,
    this.qrOnly = false,
    this.devicesOnly = false,
  });

  @override
  State<LinkedDevicesQrScreen> createState() => _LinkedDevicesQrScreenState();
}

class _LinkedDevicesQrScreenState extends State<LinkedDevicesQrScreen>
    with SingleTickerProviderStateMixin {
  late final TabController? _tabs;
  final MobileScannerController _scanner = MobileScannerController(
    formats: const <BarcodeFormat>[BarcodeFormat.qrCode],
    detectionSpeed: DetectionSpeed.noDuplicates,
  );
  List<LinkedDevice> _devices = const <LinkedDevice>[];
  bool _loadingDevices = true;
  bool _scanBusy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    if (widget.devicesOnly) {
      _tabs = null;
      _loadDevices();
    } else if (widget.qrOnly) {
      _tabs = TabController(
        length: 2,
        vsync: this,
        initialIndex: widget.initialIndex.clamp(0, 1),
      );
    } else {
      _tabs = TabController(
        length: 3,
        vsync: this,
        initialIndex: widget.initialIndex.clamp(0, 2),
      );
      _loadDevices();
    }
  }

  @override
  void dispose() {
    _tabs?.dispose();
    unawaited(_scanner.dispose());
    super.dispose();
  }

  Future<void> _loadDevices() async {
    try {
      await widget.relationshipService.registerCurrentDevice();
      final devices = await widget.relationshipService.linkedDevices();
      if (!mounted) return;
      setState(() {
        _devices = devices;
        _loadingDevices = false;
        _error = null;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _loadingDevices = false;
        _error = error.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _revoke(LinkedDevice device) async {
    if (device.isCurrentDevice) return;
    final confirmed = await ChatyConfirmDialog.show(
      context,
      title: 'Log out ${device.deviceName}?',
      message:
          'This device will be marked revoked and removed from your active device list.',
      confirmLabel: 'Log out',
      destructive: true,
    );
    if (confirmed != true) return;
    try {
      await widget.relationshipService.revokeDevice(device.id);
      await _loadDevices();
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  String get _myQrPayload =>
      'chaty://contact/${widget.dataStore.currentUser.username}';

  Future<void> _handleBarcode(BarcodeCapture capture) async {
    if (_scanBusy) return;
    final raw = capture.barcodes
        .map((barcode) => barcode.rawValue)
        .whereType<String>()
        .firstOrNull;
    if (raw == null || raw.isEmpty) return;
    final uri = Uri.tryParse(raw);
    if (uri == null ||
        uri.scheme.toLowerCase() != 'chaty' ||
        uri.host.toLowerCase() != 'contact' ||
        uri.pathSegments.isEmpty) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('This is not a Chaty contact QR code.')),
        );
      }
      return;
    }
    final username = Uri.decodeComponent(
      uri.pathSegments.first,
    ).replaceFirst('@', '').trim();
    if (username.isEmpty) return;
    if (username.toLowerCase() ==
        widget.dataStore.currentUser.username.toLowerCase()) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('This QR belongs to your current account.'),
          ),
        );
      }
      return;
    }
    setState(() => _scanBusy = true);
    try {
      final results = await widget.dataStore.searchUsersRemote(username);
      final user = results
          .where(
            (item) => item.username.toLowerCase() == username.toLowerCase(),
          )
          .firstOrNull;
      if (user == null) {
        throw Exception('Chaty user @$username could not be found.');
      }
      final conversation = await widget.dataStore.getOrCreateDirectConversation(
        user,
      );
      if (!mounted) return;
      await _scanner.stop();
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (_) => ChatDetailScreen(
            conversationId: conversation.id,
            theme: widget.themeController.globalTheme,
            dataStore: widget.dataStore,
            preferencesController: widget.preferencesController,
            themeController: widget.themeController,
          ),
        ),
      );
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(error.toString().replaceFirst('Exception: ', '')),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _scanBusy = false);
    }
  }

  String _lastActive(LinkedDevice device) {
    final value = device.lastActiveAt.toLocal();
    final now = DateTime.now();
    final diff = now.difference(value);
    if (diff.inMinutes < 1) return 'Active now';
    if (diff.inMinutes < 60) return 'Active ${diff.inMinutes}m ago';
    if (diff.inHours < 24) return 'Active ${diff.inHours}h ago';
    return 'Active ${value.day}/${value.month}/${value.year}';
  }

  Widget _buildDevicesView(ThemeData themeData) {
    if (_loadingDevices) {
      return ListView(
        children: const [
          SizedBox(height: 220),
          Center(child: CircularProgressIndicator(strokeWidth: 2.2)),
        ],
      );
    }

    if (_error != null) {
      return RefreshIndicator(
        onRefresh: _loadDevices,
        child: ListView(
          padding: const EdgeInsets.symmetric(vertical: 40),
          children: [
            ChatyEmptyState(
              icon: Icons.error_outline_rounded,
              title: 'Could not load devices',
              message: _error!,
              actionLabel: 'Try again',
              onAction: _loadDevices,
            ),
          ],
        ),
      );
    }

    if (_devices.isEmpty) {
      return RefreshIndicator(
        onRefresh: _loadDevices,
        child: ListView(
          padding: const EdgeInsets.symmetric(vertical: 40),
          children: [
            ChatyEmptyState(
              icon: Icons.devices_rounded,
              title: 'No linked devices',
              message:
                  'Link your computer or tablet by scanning the QR code in the Link Device tab.',
              actionLabel: _tabs != null ? 'Link new device' : null,
              onAction: _tabs != null
                  ? () {
                      _tabs.animateTo(0);
                    }
                  : null,
            ),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: _loadDevices,
      child: ListView(
        padding: const EdgeInsets.symmetric(
          horizontal: ChatySpacing.base,
          vertical: ChatySpacing.md,
        ),
        children: [
          ChatyGroupedSection(
            title: 'Active Devices',
            children: [
              for (final device in _devices)
                ChatyListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(ChatySpacing.sm),
                    decoration: BoxDecoration(
                      color: themeData.colorScheme.primary.withValues(
                        alpha: 0.12,
                      ),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      Icons.devices_other_rounded,
                      color: themeData.colorScheme.primary,
                      size: 20,
                    ),
                  ),
                        title: Text(
                          device.deviceName,
                          style: TextStyle(
                            color: themeData.colorScheme.onSurface,
                            fontWeight: FontWeight.w600,
                            fontSize: 15,
                          ),
                        ),
                        subtitle: Text(
                          '${device.platform}${device.location.isEmpty ? '' : ' • ${device.location}'}\n${_lastActive(device)}',
                          style: ChatyTypography.caption(
                            themeData.colorScheme.onSurface.withValues(
                              alpha: 0.6,
                            ),
                          ),
                        ),
                        trailing: device.isCurrentDevice
                            ? Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 8,
                                  vertical: 4,
                                ),
                                decoration: BoxDecoration(
                                  color: themeData.colorScheme.primary
                                      .withValues(alpha: 0.12),
                                  borderRadius: BorderRadius.circular(
                                    ChatyRadius.full,
                                  ),
                                ),
                                child: Text(
                                  'This Device',
                                  style: TextStyle(
                                    color: themeData.colorScheme.primary,
                                    fontSize: 11,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              )
                            : ChatyIconButton(
                                tooltip: 'Log out device',
                                icon: Icons.logout_rounded,
                                color: context.colors.error,
                                onPressed: () => _revoke(device),
                              ),
                      ),
                  ],
                ),
              ],
            ),
    );
  }

  Widget _buildMyQrView(ThemeData themeData) {
    final colors = context.colors;

    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(ChatySpacing.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.all(ChatySpacing.lg),
              decoration: BoxDecoration(
                color: colors.surface,
                borderRadius: BorderRadius.circular(ChatyRadius.xl),
                boxShadow: [
                  BoxShadow(
                    color: colors.shadow,
                    blurRadius: 20,
                    offset: const Offset(0, 8),
                  ),
                ],
              ),
              child: Stack(
                alignment: Alignment.center,
                children: [
                  QrImageView(
                    data: _myQrPayload,
                    size: 230,
                    version: QrVersions.auto,
                    errorCorrectionLevel: QrErrorCorrectLevel.H,
                    eyeStyle: QrEyeStyle(
                      eyeShape: QrEyeShape.square,
                      color: colors.foreground,
                    ),
                    dataModuleStyle: QrDataModuleStyle(
                      dataModuleShape: QrDataModuleShape.square,
                      color: colors.foreground,
                    ),
                  ),
                  // Center quiet-zone badge with high-contrast Chaty mark
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: colors.surface,
                      shape: BoxShape.circle,
                      boxShadow: [
                        BoxShadow(
                          color: colors.shadow.withValues(alpha: 0.15),
                          blurRadius: 6,
                          spreadRadius: 1,
                        ),
                      ],
                    ),
                    child: Center(
                      child: ChatyBrandMark(
                        variant: ChatyBrandIconVariant.filled,
                        size: 26,
                        color: colors.primary,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: ChatySpacing.lg),
            Text(
              widget.dataStore.currentUser.displayName,
              style: ChatyTypography.headline(colors.foreground),
            ),
            const SizedBox(height: 2),
            Text(
              '@${widget.dataStore.currentUser.username}',
              style: ChatyTypography.caption(colors.foregroundSecondary),
            ),
            const SizedBox(height: ChatySpacing.md),
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 380),
              child: Text(
                'Scan this code to start an instant encrypted chat without phone numbers.',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: colors.foregroundSecondary,
                  fontSize: 13.5,
                  height: 1.45,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildScannerView() {
    final colors = context.colors;

    return Stack(
      fit: StackFit.expand,
      children: [
        MobileScanner(controller: _scanner, onDetect: _handleBarcode),
        Positioned(
          left: 24,
          right: 24,
          top: 28,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: colors.surfaceElevated.withValues(alpha: 0.85),
              borderRadius: BorderRadius.circular(ChatyRadius.full),
            ),
            child: Text(
              'Point camera at a Chaty contact QR code',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: colors.foreground,
                fontSize: 13,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ),
        Center(
          child: Container(
            width: 240,
            height: 240,
            decoration: BoxDecoration(
              border: Border.all(color: colors.primary, width: 2.5),
              borderRadius: BorderRadius.circular(ChatyRadius.xl),
            ),
          ),
        ),
        if (_scanBusy)
          ColoredBox(
            color: colors.background.withValues(alpha: 0.6),
            child: Center(
              child: CircularProgressIndicator(
                strokeWidth: 2.2,
                color: colors.primary,
              ),
            ),
          ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeData = Theme.of(context);

    if (widget.devicesOnly) {
      return ChatyScaffold(
        appBar: const ChatyAppBar(
          title: 'Linked Devices',
          leading: ChatyBackButton(),
        ),
        body: _buildDevicesView(themeData),
      );
    }

    if (widget.qrOnly) {
      return ChatyScaffold(
        appBar: ChatyAppBar(
          title: 'QR Code & Scanner',
          leading: const ChatyBackButton(),
          bottom: TabBar(
            controller: _tabs,
            indicatorColor: themeData.colorScheme.primary,
            labelColor: themeData.colorScheme.primary,
            unselectedLabelColor: themeData.colorScheme.onSurface.withValues(
              alpha: 0.55,
            ),
            tabs: const [
              Tab(icon: Icon(Icons.qr_code_2_rounded), text: 'My QR'),
              Tab(icon: Icon(Icons.qr_code_scanner_rounded), text: 'Scan'),
            ],
          ),
        ),
        body: TabBarView(
          controller: _tabs,
          children: [_buildMyQrView(themeData), _buildScannerView()],
        ),
      );
    }

    return ChatyScaffold(
      appBar: ChatyAppBar(
        title: 'Linked Devices & QR',
        leading: const ChatyBackButton(),
        bottom: TabBar(
          controller: _tabs,
          indicatorColor: themeData.colorScheme.primary,
          labelColor: themeData.colorScheme.primary,
          unselectedLabelColor: themeData.colorScheme.onSurface.withValues(
            alpha: 0.55,
          ),
          tabs: const [
            Tab(icon: Icon(Icons.devices_rounded), text: 'Devices'),
            Tab(icon: Icon(Icons.qr_code_2_rounded), text: 'My QR'),
            Tab(icon: Icon(Icons.qr_code_scanner_rounded), text: 'Scan'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabs,
        children: [
          _buildDevicesView(themeData),
          _buildMyQrView(themeData),
          _buildScannerView(),
        ],
      ),
    );
  }
}
