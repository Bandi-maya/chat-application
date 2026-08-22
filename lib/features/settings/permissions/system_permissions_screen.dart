import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:permission_handler/permission_handler.dart';
import '../../../data/services/notification_service.dart';
import '../../../data/services/local_lock_service.dart';
import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/settings_primitives.dart';
import '../../../ui/core/design_system/design_system.dart';

/// System Permissions & Hardware screen.
///
/// Every control here reflects and mutates REAL operating-system state:
/// permission statuses are read through `permission_handler`, biometric
/// availability through `local_auth` (via [LocalLockService]), and the "fetch
/// location" action uses `geolocator` to read the device's actual GPS fix.
/// The OS never lets an app silently revoke its own permission, so a granted
/// toggle deep-links to system settings instead of pretending to turn off.
class SystemPermissionsScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;
  final ChatyNotificationService notificationService;

  const SystemPermissionsScreen({
    super.key,
    required this.preferencesController,
    required this.notificationService,
  });

  @override
  State<SystemPermissionsScreen> createState() =>
      _SystemPermissionsScreenState();
}

class _SystemPermissionsScreenState extends State<SystemPermissionsScreen>
    with WidgetsBindingObserver {
  // Real OS permission statuses. Null means "not read yet" (checking), so the
  // UI never claims a permission is granted before actually asking the system.
  PermissionStatus? _notification;
  PermissionStatus? _photos;
  PermissionStatus? _location;
  PermissionStatus? _camera;
  PermissionStatus? _microphone;
  PermissionStatus? _contacts;
  // Biometrics have no grant/revoke permission; report real hardware
  // availability via local_auth instead of faking a toggle.
  bool _biometricsAvailable = false;
  bool _loading = true;

  LocalLockService get _lock => locator<LocalLockService>();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refreshAll();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Re-read after returning from the system settings page so the switches
    // reflect any change the user made outside the app.
    if (state == AppLifecycleState.resumed) {
      _refreshAll();
    }
  }

  Future<void> _refreshAll() async {
    final notification = await _safeStatus(Permission.notification);
    final photos = await _safeStatus(Permission.photos);
    final location = await _safeStatus(Permission.locationWhenInUse);
    final camera = await _safeStatus(Permission.camera);
    final microphone = await _safeStatus(Permission.microphone);
    final contacts = await _safeStatus(Permission.contacts);
    var biometrics = false;
    try {
      biometrics = await _lock.canUseBiometrics();
    } catch (_) {}
    if (!mounted) return;
    setState(() {
      _notification = notification;
      _photos = photos;
      _location = location;
      _camera = camera;
      _microphone = microphone;
      _contacts = contacts;
      _biometricsAvailable = biometrics;
      _loading = false;
    });
  }

  Future<PermissionStatus?> _safeStatus(Permission permission) async {
    try {
      return await permission.status;
    } catch (_) {
      return null;
    }
  }

  bool _granted(PermissionStatus? status) =>
      status != null && (status.isGranted || status.isLimited);

  void _snack(String message, {Color? color}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: color,
        duration: const Duration(seconds: 2),
      ),
    );
  }

  Future<void> _toggle(Permission permission, PermissionStatus? status) async {
    try {
      if (_granted(status)) {
        // The OS does not allow an app to revoke its own permission; the user
        // must do it from system settings. Send them there honestly.
        _snack('Turn this off from system settings.');
        await openAppSettings();
        return;
      }
      if (status != null && status.isPermanentlyDenied) {
        _snack('This permission is blocked. Enable it in system settings.');
        await openAppSettings();
      } else {
        await permission.request();
      }
    } catch (_) {
      _snack('Could not update this permission.', color: context.colors.error);
    } finally {
      await _refreshOne(permission);
    }
  }

  Future<void> _refreshOne(Permission permission) async {
    final status = await _safeStatus(permission);
    if (!mounted) return;
    setState(() {
      if (permission == Permission.notification) {
        _notification = status;
      } else if (permission == Permission.photos) {
        _photos = status;
      } else if (permission == Permission.locationWhenInUse) {
        _location = status;
      } else if (permission == Permission.camera) {
        _camera = status;
      } else if (permission == Permission.microphone) {
        _microphone = status;
      } else if (permission == Permission.contacts) {
        _contacts = status;
      }
    });
  }

  void _sendTestNotification() {
    widget.notificationService.triggerEventNotification(
      title: 'Chaty test notification',
      body: 'This is a sample notification from Chaty.',
      icon: Icons.notifications_active_rounded,
      color: context.colors.primary,
    );
    _snack('Test notification sent.');
  }

  Future<void> _fetchLocation() async {
    try {
      final serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) {
        _snack(
          'Location services are turned off on this device.',
          color: context.colors.error,
        );
        return;
      }
      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied ||
          permission == LocationPermission.deniedForever) {
        _snack('Location permission was denied.', color: context.colors.error);
        return;
      }
      final position = await Geolocator.getCurrentPosition();
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: Row(
            children: [
              Icon(Icons.location_on_rounded, color: context.colors.error),
              const SizedBox(width: 8),
              const Expanded(child: Text('Current GPS location')),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Latitude: ${position.latitude.toStringAsFixed(5)}',
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              Text(
                'Longitude: ${position.longitude.toStringAsFixed(5)}',
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 6),
              Text(
                'Accuracy: ${position.accuracy.toStringAsFixed(0)} m',
                style: TextStyle(
                  fontSize: 12,
                  color: context.colors.foregroundSecondary,
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Close'),
            ),
          ],
        ),
      );
    } catch (error) {
      _snack('Could not read location: $error', color: context.colors.error);
    } finally {
      await _refreshOne(Permission.locationWhenInUse);
    }
  }

  Future<void> _testBiometrics() async {
    if (!_biometricsAvailable) {
      _snack(
        'No biometrics are enrolled on this device.',
        color: context.colors.error,
      );
      return;
    }
    final ok = await _lock.authenticateBiometric(
      reason: 'Verify your identity for Chaty',
    );
    _snack(
      ok
          ? 'Biometric verification succeeded.'
          : 'Biometric verification failed or cancelled.',
      color: ok ? context.colors.success : context.colors.error,
    );
  }

  Future<void> _requestAll() async {
    try {
      final results = await <Permission>[
        Permission.notification,
        Permission.photos,
        Permission.locationWhenInUse,
        Permission.camera,
        Permission.microphone,
        Permission.contacts,
      ].request();
      final grantedCount = results.values
          .where((s) => s.isGranted || s.isLimited)
          .length;
      _snack(
        '$grantedCount of ${results.length} permissions granted.',
        color: grantedCount == results.length ? context.colors.success : null,
      );
    } catch (_) {
      _snack('Could not request permissions.', color: context.colors.error);
    } finally {
      await _refreshAll();
    }
  }

  @override
  Widget build(BuildContext context) {
    return ChatySettingsPage(
      title: 'App Permissions & Hardware',
      subtitle: 'Notifications, media, location, camera, mic & more',
      trailingHeaderWidget: IconButton(
        tooltip: 'Re-check permissions',
        icon: const Icon(Icons.refresh_rounded),
        onPressed: _loading ? null : _refreshAll,
      ),
      children: [
        // Live status center, driven by the real permission statuses above.
        ChatyPreviewCard(
          title: 'Permission Status Center',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _buildStatusChip(
                    'Notifications',
                    _loading ? null : _granted(_notification),
                    Icons.notifications_active_rounded,
                  ),
                  _buildStatusChip(
                    'Media & Photos',
                    _loading ? null : _granted(_photos),
                    Icons.perm_media_rounded,
                  ),
                  _buildStatusChip(
                    'Location',
                    _loading ? null : _granted(_location),
                    Icons.location_on_rounded,
                  ),
                  _buildStatusChip(
                    'Camera',
                    _loading ? null : _granted(_camera),
                    Icons.camera_alt_rounded,
                  ),
                  _buildStatusChip(
                    'Microphone',
                    _loading ? null : _granted(_microphone),
                    Icons.mic_rounded,
                  ),
                  _buildStatusChip(
                    'Contacts',
                    _loading ? null : _granted(_contacts),
                    Icons.contacts_rounded,
                  ),
                  _buildStatusChip(
                    'Biometrics',
                    _loading ? null : _biometricsAvailable,
                    Icons.fingerprint_rounded,
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Text(
                _loading
                    ? 'Checking current permission status...'
                    : 'Tap a permission to grant it, or manage it in system settings.',
                style: TextStyle(
                  fontSize: 11.5,
                  color: Theme.of(
                    context,
                  ).textTheme.bodyMedium?.color?.withValues(alpha: 0.6),
                ),
              ),
            ],
          ),
        ),

        // 1. Notifications
        ChatySettingsSection(
          title: 'Push Notifications & Alerts',
          description:
              'Receive alerts for calls, mentions, and incoming messages.',
          children: [
            _permissionSwitch(
              icon: Icons.notifications_active_rounded,
              color: context.colors.primary,
              title: 'Notification Permission',
              subtitle:
                  'Allow Chaty to display alerts and heads-up notifications',
              permission: Permission.notification,
              status: _notification,
            ),
            ChatySettingsTile(
              icon: Icons.send_rounded,
              iconColor: context.colors.info,
              title: 'Send a test notification',
              subtitle: 'Display a sample Chaty notification now',
              onTap: _sendTestNotification,
            ),
          ],
        ),

        // 2. Media, Photos & Storage
        ChatySettingsSection(
          title: 'Media, Photos & File System',
          description:
              'Share images, videos, audio notes, and attachments in chats.',
          children: [
            _permissionSwitch(
              icon: Icons.perm_media_rounded,
              color: context.colors.info,
              title: 'Media & Photo Library',
              subtitle: 'Access the gallery to send photos and video',
              permission: Permission.photos,
              status: _photos,
            ),
          ],
        ),

        // 3. Location
        ChatySettingsSection(
          title: 'Location & Map Coordinates',
          description:
              'Share your live location or drop a pin in a conversation.',
          children: [
            _permissionSwitch(
              icon: Icons.location_on_rounded,
              color: context.colors.warning,
              title: 'Location Permission',
              subtitle: 'Access device GPS to share your location in chats',
              permission: Permission.locationWhenInUse,
              status: _location,
            ),
            ChatySettingsTile(
              icon: Icons.my_location_rounded,
              iconColor: context.colors.warning,
              title: 'Fetch current location',
              subtitle: 'Read your real GPS coordinates now',
              onTap: _fetchLocation,
            ),
          ],
        ),

        // 4. Hardware sensors
        ChatySettingsSection(
          title: 'Hardware & Sensors',
          description:
              'Control camera, microphone, contacts, and biometric access.',
          children: [
            _permissionSwitch(
              icon: Icons.camera_alt_rounded,
              color: context.colors.primary,
              title: 'Camera',
              subtitle: 'Take photos, record stories and start video calls',
              permission: Permission.camera,
              status: _camera,
            ),
            _permissionSwitch(
              icon: Icons.mic_rounded,
              color: context.colors.error,
              title: 'Microphone',
              subtitle: 'Record voice messages and voice calls',
              permission: Permission.microphone,
              status: _microphone,
            ),
            _permissionSwitch(
              icon: Icons.contacts_rounded,
              color: context.colors.success,
              title: 'Contacts',
              subtitle: 'Discover which of your contacts are on Chaty',
              permission: Permission.contacts,
              status: _contacts,
            ),
            ChatySettingsTile(
              icon: Icons.fingerprint_rounded,
              iconColor: context.colors.primary,
              title: 'Biometrics & Fingerprint',
              subtitle: _biometricsAvailable
                  ? 'Tap to test biometric unlock'
                  : 'No enrolled biometrics detected on this device',
              badgeText: _loading
                  ? null
                  : (_biometricsAvailable ? 'Available' : 'Unavailable'),
              badgeColor: _biometricsAvailable
                  ? context.colors.success
                  : context.colors.disabled,
              onTap: _testBiometrics,
            ),
          ],
        ),

        // 5. Bulk actions
        ChatySettingsSection(
          title: 'All System Permissions',
          children: [
            ChatySettingsTile(
              icon: Icons.done_all_rounded,
              iconColor: context.colors.success,
              title: 'Request all permissions',
              subtitle:
                  'Prompt for notifications, media, location, camera, mic & contacts',
              onTap: _requestAll,
            ),
            ChatySettingsTile(
              icon: Icons.settings_rounded,
              iconColor: context.colors.foregroundSecondary,
              title: 'Open system settings',
              subtitle: 'Manage every Chaty permission directly in the OS',
              onTap: () {
                openAppSettings();
              },
            ),
          ],
        ),
      ],
    );
  }

  Widget _permissionSwitch({
    required IconData icon,
    required Color color,
    required String title,
    required String subtitle,
    required Permission permission,
    required PermissionStatus? status,
  }) {
    final granted = _granted(status);
    final blocked = status != null && status.isPermanentlyDenied && !granted;
    return ChatySwitchTile(
      icon: icon,
      iconColor: color,
      title: title,
      subtitle: blocked ? '$subtitle (blocked in settings)' : subtitle,
      value: granted,
      onChanged: (_) => _toggle(permission, status),
    );
  }

  Widget _buildStatusChip(String label, bool? granted, IconData icon) {
    final Color color = granted == null
        ? context.colors.foregroundSecondary
        : (granted ? context.colors.success : context.colors.error);
    final IconData trailingIcon = granted == null
        ? Icons.hourglass_bottom_rounded
        : (granted ? Icons.check : Icons.close);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color, width: 1),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: color),
          const SizedBox(width: 5),
          Text(
            label,
            style: TextStyle(
              fontSize: 11.5,
              fontWeight: FontWeight.w600,
              color: color,
            ),
          ),
          const SizedBox(width: 4),
          Icon(trailingIcon, size: 12, color: color),
        ],
      ),
    );
  }
}
