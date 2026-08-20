import 'dart:async';

import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../data/repositories/mock_data_store.dart';
import '../../data/services/contact_relationship_service.dart';
import '../../domain/models/other_models.dart';
import '../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../chats/chat_detail_screen.dart';

class LinkedDevicesQrScreen extends StatefulWidget {
  final MockDataStore dataStore;
  final ContactRelationshipService relationshipService;
  final ChatyPreferencesController preferencesController;
  final ThemeController themeController;

  const LinkedDevicesQrScreen({
    super.key,
    required this.dataStore,
    required this.relationshipService,
    required this.preferencesController,
    required this.themeController,
  });

  @override
  State<LinkedDevicesQrScreen> createState() => _LinkedDevicesQrScreenState();
}

class _LinkedDevicesQrScreenState extends State<LinkedDevicesQrScreen> with SingleTickerProviderStateMixin {
  late final TabController _tabs;
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
    _tabs = TabController(length: 3, vsync: this);
    _loadDevices();
  }

  @override
  void dispose() {
    _tabs.dispose();
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
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Log out ${device.deviceName}?'),
        content: const Text('This device will be marked revoked and removed from your active device list.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Log out')),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await widget.relationshipService.revokeDevice(device.id);
      await _loadDevices();
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$error')));
    }
  }

  String get _myQrPayload => 'chaty://contact/${widget.dataStore.currentUser.username}';

  Future<void> _handleBarcode(BarcodeCapture capture) async {
    if (_scanBusy) return;
    final raw = capture.barcodes.map((barcode) => barcode.rawValue).whereType<String>().firstOrNull;
    if (raw == null || raw.isEmpty) return;
    final uri = Uri.tryParse(raw);
    if (uri == null || uri.scheme.toLowerCase() != 'chaty' || uri.host.toLowerCase() != 'contact' || uri.pathSegments.isEmpty) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('This is not a Chaty contact QR code.')));
      return;
    }
    final username = Uri.decodeComponent(uri.pathSegments.first).replaceFirst('@', '').trim();
    if (username.isEmpty) return;
    if (username.toLowerCase() == widget.dataStore.currentUser.username.toLowerCase()) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('This QR belongs to your current account.')));
      return;
    }
    setState(() => _scanBusy = true);
    try {
      final results = await widget.dataStore.searchUsersRemote(username);
      final user = results.where((item) => item.username.toLowerCase() == username.toLowerCase()).firstOrNull;
      if (user == null) throw Exception('Chaty user @$username could not be found.');
      final conversation = await widget.dataStore.getOrCreateDirectConversation(user);
      if (!mounted) return;
      await _scanner.stop();
      Navigator.of(context).pushReplacement(MaterialPageRoute(
        builder: (_) => ChatDetailScreen(
          conversationId: conversation.id,
          theme: widget.themeController.globalTheme,
          dataStore: widget.dataStore,
          preferencesController: widget.preferencesController,
          themeController: widget.themeController,
        ),
      ));
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.toString().replaceFirst('Exception: ', ''))));
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

  @override
  Widget build(BuildContext context) {
    final theme = widget.themeController.globalTheme;
    return Scaffold(
      backgroundColor: theme.backgroundColor,
      appBar: AppBar(
        backgroundColor: theme.surfaceColor,
        foregroundColor: theme.primaryTextColor,
        title: const Text('Linked devices & QR'),
        bottom: TabBar(
          controller: _tabs,
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
          RefreshIndicator(
            onRefresh: _loadDevices,
            child: _loadingDevices
                ? const ListView(children: [SizedBox(height: 220), Center(child: CircularProgressIndicator())])
                : ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      if (_error != null) Padding(padding: const EdgeInsets.only(bottom: 12), child: Text(_error!, style: TextStyle(color: theme.dangerColor))),
                      Text('Devices signed into this Chaty account', style: TextStyle(color: theme.primaryTextColor, fontWeight: FontWeight.w800, fontSize: 16)),
                      const SizedBox(height: 8),
                      ..._devices.map((device) => Card(
                            color: theme.surfaceColor,
                            child: ListTile(
                              leading: CircleAvatar(backgroundColor: theme.cardColor, child: Icon(Icons.devices_other_rounded, color: theme.accentColor)),
                              title: Text(device.deviceName, style: TextStyle(color: theme.primaryTextColor, fontWeight: FontWeight.w700)),
                              subtitle: Text('${device.platform}${device.location.isEmpty ? '' : ' • ${device.location}'}\n${_lastActive(device)}', style: TextStyle(color: theme.secondaryTextColor)),
                              isThreeLine: true,
                              trailing: device.isCurrentDevice
                                  ? Chip(label: const Text('This device'), backgroundColor: theme.accentColor.withValues(alpha: 0.12))
                                  : IconButton(tooltip: 'Log out device', onPressed: () => _revoke(device), icon: Icon(Icons.logout_rounded, color: theme.dangerColor)),
                            ),
                          )),
                    ],
                  ),
          ),
          Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    padding: const EdgeInsets.all(18),
                    decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(24)),
                    child: QrImageView(data: _myQrPayload, size: 245, version: QrVersions.auto),
                  ),
                  const SizedBox(height: 18),
                  Text(widget.dataStore.currentUser.displayName, style: TextStyle(color: theme.primaryTextColor, fontWeight: FontWeight.w800, fontSize: 20)),
                  const SizedBox(height: 4),
                  Text('@${widget.dataStore.currentUser.username}', style: TextStyle(color: theme.secondaryTextColor)),
                  const SizedBox(height: 14),
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 420),
                    child: Text(
                      'Another Chaty user can scan this code to open a direct conversation. Messaging works immediately; voice/video calls remain locked until both people accept the contact request.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: theme.secondaryTextColor, height: 1.45),
                    ),
                  ),
                ],
              ),
            ),
          ),
          Stack(
            fit: StackFit.expand,
            children: [
              MobileScanner(controller: _scanner, onDetect: _handleBarcode),
              Positioned(
                left: 24,
                right: 24,
                top: 28,
                child: Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: Colors.black.withValues(alpha: 0.68), borderRadius: BorderRadius.circular(14)),
                  child: const Text('Scan a Chaty contact QR code', textAlign: TextAlign.center, style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                ),
              ),
              Center(
                child: Container(
                  width: 250,
                  height: 250,
                  decoration: BoxDecoration(border: Border.all(color: Colors.white, width: 3), borderRadius: BorderRadius.circular(24)),
                ),
              ),
              if (_scanBusy)
                ColoredBox(color: Colors.black38, child: const Center(child: CircularProgressIndicator(color: Colors.white))),
            ],
          ),
        ],
      ),
    );
  }
}
