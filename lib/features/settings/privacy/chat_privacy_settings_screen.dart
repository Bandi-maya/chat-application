import 'package:flutter/material.dart';

import '../../../data/services/api_backend_service.dart';
import '../../../domain/models/chat_user_settings.dart';
import '../../../injection/locator.dart';
import '../../../ui/core/design_system/design_system.dart';

/// Full-featured Chat Privacy & Presence Settings matching SaaS UI.
/// Controls read receipts (blue ticks), audience selectors, freeze last seen,
/// online visibility, typing indicators, and account activation status.
class ChatPrivacySettingsScreen extends StatefulWidget {
  final ThemeConfig? theme;
  final int initialIndex;

  const ChatPrivacySettingsScreen({
    super.key,
    this.theme,
    this.initialIndex = 0,
  });

  @override
  State<ChatPrivacySettingsScreen> createState() =>
      _ChatPrivacySettingsScreenState();
}

class _ChatPrivacySettingsScreenState extends State<ChatPrivacySettingsScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabController;
  late final ApiBackendService _apiService;
  bool _loading = false;
  String? _toastMessage;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(
      length: 3,
      vsync: this,
      initialIndex: widget.initialIndex.clamp(0, 2),
    );
    _apiService = locator<ApiBackendService>();
    _apiService.addListener(_handleApiStateChanged);
    _loadSettings();
  }

  @override
  void dispose() {
    _apiService.removeListener(_handleApiStateChanged);
    _tabController.dispose();
    super.dispose();
  }

  void _handleApiStateChanged() {
    if (mounted) setState(() {});
  }

  Future<void> _loadSettings() async {
    setState(() => _loading = true);
    await _apiService.loadChatSettings();
    if (mounted) setState(() => _loading = false);
  }

  void _showToast(String message) {
    if (!mounted) return;
    setState(() => _toastMessage = message);
    Future.delayed(const Duration(milliseconds: 2800), () {
      if (mounted && _toastMessage == message) {
        setState(() => _toastMessage = null);
      }
    });
  }

  Future<void> _autoSave({
    bool? freezeLastSeen,
    String? whoCanSeeLastSeen,
    String? whoCanSeeOnline,
    bool? readReceiptsEnabled,
    String? whoCanSeeReadReceipts,
    bool? blueTicksAfterReply,
    bool? typingIndicatorsEnabled,
  }) async {
    final updated = await _apiService.updateChatSettings(
      freezeLastSeen: freezeLastSeen,
      whoCanSeeLastSeen: whoCanSeeLastSeen,
      whoCanSeeOnline: whoCanSeeOnline,
      readReceiptsEnabled: readReceiptsEnabled,
      whoCanSeeReadReceipts: whoCanSeeReadReceipts,
      blueTicksAfterReply: blueTicksAfterReply,
      typingIndicatorsEnabled: typingIndicatorsEnabled,
    );
    if (updated != null) {
      _showToast('Settings saved successfully');
    }
  }

  Future<void> _confirmToggleDeactivation(ChatUserSettings settings) async {
    final isDeactivating = !settings.isDeactivated;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(isDeactivating ? 'Deactivate Chat Account?' : 'Reactivate Chat Account?'),
        content: Text(
          isDeactivating
              ? 'Deactivating will freeze your chat availability, hide your live status from team members, and prevent sending or receiving new messages. You can reactivate anytime.'
              : 'Reactivating your account will instantly allow team members to message you and restore your live presence.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: isDeactivating ? Colors.redAccent : Colors.green,
            ),
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(isDeactivating ? 'Deactivate' : 'Reactivate'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    setState(() => _loading = true);
    if (isDeactivating) {
      await _apiService.deactivateChatAccount(reason: 'User requested in app');
      _showToast('Account deactivated. Messaging disabled.');
    } else {
      await _apiService.reactivateChatAccount();
      _showToast('Account reactivated successfully!');
    }
    if (mounted) setState(() => _loading = false);
  }

  @override
  Widget build(BuildContext context) {
    final theme = widget.theme ?? ThemePresets.midnight;
    final colors = context.colors;
    final isDark = theme.brightness == Brightness.dark;
    final settings = _apiService.chatUserSettings ??
        ChatUserSettings(
          userId: _apiService.currentUser?.id ?? '',
        );

    return Scaffold(
      backgroundColor: colors.background,
      appBar: AppBar(
        backgroundColor: colors.surface,
        elevation: 0,
        leading: IconButton(
          icon: Icon(Icons.arrow_back_rounded, color: colors.foreground),
          onPressed: () => Navigator.of(context).pop(),
        ),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(
                  'Chat Privacy & Presence',
                  style: TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.bold,
                    color: colors.foreground,
                  ),
                ),
                if (settings.isDeactivated) ...[
                  const SizedBox(width: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                    decoration: BoxDecoration(
                      color: Colors.redAccent.withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                        color: Colors.redAccent.withValues(alpha: 0.3),
                      ),
                    ),
                    child: const Text(
                      'Deactivated',
                      style: TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                        color: Colors.redAccent,
                      ),
                    ),
                  ),
                ],
              ],
            ),
            Text(
              'Control blue ticks, read receipts & last seen',
              style: TextStyle(
                fontSize: 12,
                color: colors.foregroundSecondary,
              ),
            ),
          ],
        ),
        bottom: TabBar(
          controller: _tabController,
          labelColor: theme.accentColor,
          unselectedLabelColor: colors.foregroundSecondary,
          indicatorColor: theme.accentColor,
          indicatorWeight: 2.5,
          tabs: const [
            Tab(
              icon: Icon(Icons.done_all_rounded, size: 18),
              text: 'Blue Ticks',
            ),
            Tab(
              icon: Icon(Icons.remove_red_eye_outlined, size: 18),
              text: 'Presence',
            ),
            Tab(
              icon: Icon(Icons.lock_person_outlined, size: 18),
              text: 'Account',
            ),
          ],
        ),
      ),
      body: Stack(
        children: [
          TabBarView(
            controller: _tabController,
            children: [
              _buildReadReceiptsTab(theme, colors, isDark, settings),
              _buildPresenceTab(theme, colors, isDark, settings),
              _buildAccountTab(theme, colors, isDark, settings),
            ],
          ),
          if (_toastMessage != null)
            Positioned(
              top: 12,
              left: 20,
              right: 20,
              child: AnimatedOpacity(
                duration: const Duration(milliseconds: 200),
                opacity: _toastMessage != null ? 1.0 : 0.0,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  decoration: BoxDecoration(
                    color: colors.surface,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(
                      color: theme.accentColor.withValues(alpha: 0.4),
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.15),
                        blurRadius: 10,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: Row(
                    children: [
                      Icon(Icons.check_circle_rounded, color: theme.accentColor, size: 20),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          _toastMessage!,
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: colors.foreground,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          if (_loading)
            Container(
              color: Colors.black.withValues(alpha: 0.2),
              child: const Center(
                child: CircularProgressIndicator(),
              ),
            ),
        ],
      ),
    );
  }

  // =========================================================================
  // TAB 1: READ RECEIPTS & BLUE TICKS
  // =========================================================================
  Widget _buildReadReceiptsTab(
    ThemeConfig theme,
    AppColors colors,
    bool isDark,
    ChatUserSettings settings,
  ) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Master Blue Ticks Card
        _buildSettingsCard(
          colors: colors,
          isDark: isDark,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 38,
                    height: 38,
                    decoration: BoxDecoration(
                      color: settings.readReceiptsEnabled
                          ? Colors.lightBlue.withValues(alpha: 0.15)
                          : colors.foregroundSecondary.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(
                      Icons.done_all_rounded,
                      color: settings.readReceiptsEnabled
                          ? Colors.lightBlue
                          : colors.foregroundSecondary,
                      size: 20,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Read Receipts (Blue Ticks)',
                          style: TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.bold,
                            color: colors.foreground,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'When enabled, contacts can see blue double-ticks when you have read their messages. If turned off, ticks remain grey and you will not see blue ticks from others.',
                          style: TextStyle(
                            fontSize: 12,
                            color: colors.foregroundSecondary,
                            height: 1.35,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  Switch.adaptive(
                    value: settings.readReceiptsEnabled,
                    activeColor: Colors.lightBlue,
                    onChanged: (val) {
                      _autoSave(readReceiptsEnabled: val);
                    },
                  ),
                ],
              ),
              const SizedBox(height: 12),
              const Divider(height: 1),
              const SizedBox(height: 10),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Status:',
                    style: TextStyle(fontSize: 12, color: colors.foregroundSecondary),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                    decoration: BoxDecoration(
                      color: settings.readReceiptsEnabled
                          ? Colors.lightBlue.withValues(alpha: 0.12)
                          : colors.foregroundSecondary.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: settings.readReceiptsEnabled
                            ? Colors.lightBlue.withValues(alpha: 0.3)
                            : Colors.transparent,
                      ),
                    ),
                    child: Text(
                      settings.readReceiptsEnabled ? 'Blue Ticks ON' : 'Blue Ticks OFF',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                        color: settings.readReceiptsEnabled
                            ? Colors.lightBlue
                            : colors.foregroundSecondary,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),

        const SizedBox(height: 18),

        // Who Can See My Blue Ticks
        Text(
          'Who Can See My Blue Ticks',
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.bold,
            color: colors.foreground,
          ),
        ),
        const SizedBox(height: 8),
        _buildAudienceSelector(
          colors: colors,
          theme: theme,
          currentValue: settings.whoCanSeeReadReceipts,
          options: const ['everyone', 'contacts', 'nobody'],
          labels: const ['Everyone', 'My Contacts', 'Nobody'],
          onSelected: (opt) => _autoSave(whoCanSeeReadReceipts: opt),
        ),

        const SizedBox(height: 18),

        // Blue Ticks After Reply
        _buildSettingsCard(
          colors: colors,
          isDark: isDark,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          'Blue Ticks After Reply',
                          style: TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.bold,
                            color: colors.foreground,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: theme.accentColor.withValues(alpha: 0.15),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text(
                            'Delayed',
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.bold,
                              color: theme.accentColor,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Hold read receipts until you reply. The sender will only see that you read their messages (blue ticks) after you send a response in the conversation.',
                      style: TextStyle(
                        fontSize: 12,
                        color: colors.foregroundSecondary,
                        height: 1.35,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Switch.adaptive(
                value: settings.blueTicksAfterReply,
                activeColor: theme.accentColor,
                onChanged: (val) {
                  _autoSave(blueTicksAfterReply: val);
                },
              ),
            ],
          ),
        ),
      ],
    );
  }

  // =========================================================================
  // TAB 2: PRESENCE & LAST SEEN
  // =========================================================================
  Widget _buildPresenceTab(
    ThemeConfig theme,
    AppColors colors,
    bool isDark,
    ChatUserSettings settings,
  ) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Freeze Last Seen Card
        _buildSettingsCard(
          colors: colors,
          isDark: isDark,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: settings.freezeLastSeen
                      ? Colors.cyan.withValues(alpha: 0.15)
                      : colors.foregroundSecondary.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(
                  Icons.ac_unit_rounded,
                  color: settings.freezeLastSeen ? Colors.cyan : colors.foregroundSecondary,
                  size: 20,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Freeze Last Seen',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                        color: colors.foreground,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Prevents the server from updating your last seen timestamp. Other team members will only see your frozen timestamp whenever you are offline.',
                      style: TextStyle(
                        fontSize: 12,
                        color: colors.foregroundSecondary,
                        height: 1.35,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Switch.adaptive(
                value: settings.freezeLastSeen,
                activeColor: Colors.cyan,
                onChanged: (val) {
                  _autoSave(freezeLastSeen: val);
                },
              ),
            ],
          ),
        ),

        const SizedBox(height: 18),

        // Who Can See My Last Seen
        Text(
          'Who Can See My Last Seen',
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.bold,
            color: colors.foreground,
          ),
        ),
        const SizedBox(height: 8),
        _buildAudienceSelector(
          colors: colors,
          theme: theme,
          currentValue: settings.whoCanSeeLastSeen,
          options: const ['everyone', 'contacts', 'nobody'],
          labels: const ['Everyone', 'My Contacts', 'Nobody'],
          onSelected: (opt) => _autoSave(whoCanSeeLastSeen: opt),
        ),

        const SizedBox(height: 18),

        // Who Can See When Online
        Text(
          "Who Can See When I'm Online",
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.bold,
            color: colors.foreground,
          ),
        ),
        const SizedBox(height: 8),
        _buildAudienceSelector(
          colors: colors,
          theme: theme,
          currentValue: settings.whoCanSeeOnline,
          options: const ['everyone', 'same_as_last_seen', 'nobody'],
          labels: const ['Everyone', 'Same as Last Seen', 'Nobody'],
          onSelected: (opt) => _autoSave(whoCanSeeOnline: opt),
        ),

        const SizedBox(height: 18),

        // Typing Indicators
        _buildSettingsCard(
          colors: colors,
          isDark: isDark,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Typing Indicators',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                        color: colors.foreground,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Show a typing indicator to other participants in a chat room when you are composing a message.',
                      style: TextStyle(
                        fontSize: 12,
                        color: colors.foregroundSecondary,
                        height: 1.35,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Switch.adaptive(
                value: settings.typingIndicatorsEnabled,
                activeColor: theme.accentColor,
                onChanged: (val) {
                  _autoSave(typingIndicatorsEnabled: val);
                },
              ),
            ],
          ),
        ),
      ],
    );
  }

  // =========================================================================
  // TAB 3: ACCOUNT STATUS & DEACTIVATION
  // =========================================================================
  Widget _buildAccountTab(
    ThemeConfig theme,
    AppColors colors,
    bool isDark,
    ChatUserSettings settings,
  ) {
    final isDeactivated = settings.isDeactivated;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Status Banner Card
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: isDeactivated
                ? Colors.redAccent.withValues(alpha: 0.12)
                : Colors.green.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: isDeactivated
                  ? Colors.redAccent.withValues(alpha: 0.3)
                  : Colors.green.withValues(alpha: 0.3),
            ),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: isDeactivated ? Colors.redAccent : Colors.green,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(
                  isDeactivated
                      ? Icons.person_off_rounded
                      : Icons.verified_user_rounded,
                  color: Colors.white,
                  size: 24,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      isDeactivated
                          ? 'Account Deactivated'
                          : 'Chat Account Active',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: colors.foreground,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      isDeactivated
                          ? 'Your account is currently deactivated. You cannot send or receive new chat messages or voice/video calls.'
                          : 'Your account is active. You can freely collaborate, message, and call team members.',
                      style: TextStyle(
                        fontSize: 12,
                        color: colors.foregroundSecondary,
                        height: 1.35,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),

        const SizedBox(height: 20),

        // Action Card
        _buildSettingsCard(
          colors: colors,
          isDark: isDark,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'ACCOUNT ACTION',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 0.8,
                  color: colors.foregroundSecondary,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                isDeactivated
                    ? 'Reactivating your account will instantly allow team members to message you and restore your live status across the workspace.'
                    : 'Deactivating will freeze your chat availability, mark your status as deactivated, and prevent calls or messages until you reactivate.',
                style: TextStyle(
                  fontSize: 12,
                  color: colors.foregroundSecondary,
                  height: 1.35,
                ),
              ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  style: FilledButton.styleFrom(
                    backgroundColor: isDeactivated
                        ? Colors.green
                        : Colors.redAccent.withValues(alpha: 0.15),
                    foregroundColor: isDeactivated ? Colors.white : Colors.redAccent,
                    padding: const EdgeInsets.symmetric(vertical: 13),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  icon: Icon(
                    isDeactivated ? Icons.check_circle_rounded : Icons.pause_circle_filled_rounded,
                    size: 20,
                  ),
                  label: Text(
                    isDeactivated ? 'Reactivate My Chat Account' : 'Deactivate Chat Account',
                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                  ),
                  onPressed: () => _confirmToggleDeactivation(settings),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  // =========================================================================
  // HELPER WIDGETS
  // =========================================================================
  Widget _buildSettingsCard({
    required AppColors colors,
    required bool isDark,
    required Widget child,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: colors.borderSubtle,
          width: 0.9,
        ),
      ),
      child: child,
    );
  }

  Widget _buildAudienceSelector({
    required AppColors colors,
    required ThemeConfig theme,
    required String currentValue,
    required List<String> options,
    required List<String> labels,
    required ValueChanged<String> onSelected,
  }) {
    return Row(
      children: List.generate(options.length, (index) {
        final opt = options[index];
        final label = labels[index];
        final isSelected = currentValue.toLowerCase() == opt.toLowerCase();

        return Expanded(
          child: Padding(
            padding: EdgeInsets.only(
              left: index == 0 ? 0 : 4,
              right: index == options.length - 1 ? 0 : 4,
            ),
            child: InkWell(
              onTap: () => onSelected(opt),
              borderRadius: BorderRadius.circular(12),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 160),
                padding: const EdgeInsets.symmetric(vertical: 11, horizontal: 4),
                decoration: BoxDecoration(
                  color: isSelected
                      ? theme.accentColor.withValues(alpha: 0.14)
                      : colors.surface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: isSelected
                        ? theme.accentColor
                        : colors.borderSubtle,
                    width: isSelected ? 1.5 : 0.9,
                  ),
                ),
                child: Center(
                  child: Text(
                    label,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
                      color: isSelected ? theme.accentColor : colors.foreground,
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
      }),
    );
  }
}
