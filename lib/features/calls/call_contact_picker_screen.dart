import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/call_signaling_service.dart';
import '../../injection/locator.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/widgets/app_avatar.dart';
import 'ongoing_call_screen.dart';

enum CallContactPickerMode { contacts, dialpad }

/// A full-screen contact picker shared by the Calls FABs.
///
/// Dialpad mode opens the system phone keyboard immediately and filters
/// registered Chaty contacts live as the user enters a name or number.
class CallContactPickerScreen extends StatefulWidget {
  final ThemeConfig theme;
  final ChatyDataStore dataStore;
  final CallContactPickerMode initialMode;

  const CallContactPickerScreen({
    super.key,
    required this.theme,
    required this.dataStore,
    this.initialMode = CallContactPickerMode.contacts,
  });

  @override
  State<CallContactPickerScreen> createState() => _CallContactPickerScreenState();
}

class _CallContactPickerScreenState extends State<CallContactPickerScreen> {
  late final TextEditingController _searchController;
  late final FocusNode _searchFocusNode;
  String? _startingContactId;
  late bool _isDialpad;
  bool _showNumberActions = true;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController();
    _searchFocusNode = FocusNode();
    _isDialpad = widget.initialMode == CallContactPickerMode.dialpad;
  }

  @override
  void dispose() {
    _searchController.dispose();
    _searchFocusNode.dispose();
    super.dispose();
  }

  String _initials(String name) {
    final normalized = name.trim();
    return normalized.isEmpty ? '?' : normalized.substring(0, 1).toUpperCase();
  }

  List<dynamic> get _visibleContacts {
    final query = _searchController.text.trim().toLowerCase();
    final queryDigits = query.replaceAll(RegExp(r'[^0-9]'), '');
    final currentUserId = widget.dataStore.currentUser.id;

    return widget.dataStore.contacts.where((contact) {
      if (contact.id == currentUserId) return false;
      if (query.isEmpty) return true;

      final name = contact.displayName.trim().toLowerCase();
      final phone = contact.phone.trim().toLowerCase();
      final contactDigits = phone.replaceAll(RegExp(r'[^0-9]'), '');

      return name.contains(query) ||
          phone.contains(query) ||
          (queryDigits.isNotEmpty && contactDigits.contains(queryDigits));
    }).toList(growable: false);
  }

  Future<void> _startCall(dynamic contact, {required bool isVideo}) async {
    if (_startingContactId != null) return;

    final contactId = contact.id as String;
    final displayName = (contact.displayName as String).trim();
    setState(() => _startingContactId = contactId);

    try {
      await locator<CallSignalingService>().initiateCall(
        remoteUserId: contactId,
        remoteDisplayName: displayName.isEmpty ? 'Chaty contact' : displayName,
        remoteAvatarInitials: _initials(displayName),
        remoteAvatarColorHex: contact.avatarColorHex as String?,
        isVideo: isVideo,
      );

      if (!mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => OngoingCallScreen(theme: widget.theme),
        ),
      );
    } catch (error) {
      if (!mounted) return;
      ChatyToast.show(
        context,
        error.toString().replaceFirst('Bad state: ', ''),
        background: context.colors.error,
      );
    } finally {
      if (mounted) setState(() => _startingContactId = null);
    }
  }

  void _switchMode() {
    setState(() => _isDialpad = !_isDialpad);
    if (!_isDialpad) {
      _searchFocusNode.requestFocus();
    } else {
      _searchFocusNode.unfocus();
    }
  }

  void _appendDigit(String digit) {
    if (_startingContactId != null) return;
    final current = _searchController.text;
    if (current.length >= 25) return;
    _searchController.value = TextEditingValue(
      text: '$current$digit',
      selection: TextSelection.collapsed(offset: current.length + digit.length),
    );
    setState(() {});
    HapticFeedback.selectionClick();
  }

  void _removeDigit() {
    final current = _searchController.text;
    if (current.isEmpty) return;
    _searchController.value = TextEditingValue(
      text: current.substring(0, current.length - 1),
      selection: TextSelection.collapsed(offset: current.length - 1),
    );
    setState(() {});
    HapticFeedback.selectionClick();
  }

  Widget _buildDialKey(
    String digit, {
    String subtitle = '',
    VoidCallback? onPressed,
  }) {
    final colors = context.colors;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(28),
        onTap: onPressed ?? () => _appendDigit(digit),
        child: SizedBox.expand(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                digit,
                style: TextStyle(
                  color: colors.foreground,
                  fontSize: 26,
                  height: 1.0,
                  fontWeight: FontWeight.w500,
                ),
              ),
              if (subtitle.isNotEmpty) ...[
                const SizedBox(height: 4),
                Text(
                  subtitle,
                  style: TextStyle(
                    color: colors.foregroundSecondary,
                    fontSize: 9,
                    letterSpacing: 1.3,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildDialpad() {
    final colors = context.colors;
    const keys = <(String, String)>[
      ('1', ''), ('2', 'ABC'), ('3', 'DEF'),
      ('4', 'GHI'), ('5', 'JKL'), ('6', 'MNO'),
      ('7', 'PQRS'), ('8', 'TUV'), ('9', 'WXYZ'),
      ('*', ''), ('0', '+'), ('#', ''),
    ];
    return Padding(
      padding: const EdgeInsets.fromLTRB(30, 0, 30, 10),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          LayoutBuilder(
            builder: (context, constraints) {
              final keyHeight = ((MediaQuery.sizeOf(context).height - 430) / 4)
                  .clamp(48.0, 66.0)
                  .toDouble();
              return GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: keys.length,
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 3,
                  mainAxisExtent: keyHeight,
                  mainAxisSpacing: 3,
                  crossAxisSpacing: 18,
                ),
                itemBuilder: (context, index) {
                  final (digit, subtitle) = keys[index];
                  return _buildDialKey(digit, subtitle: subtitle);
                },
              );
            },
          ),
          const SizedBox(height: 2),
          Row(
            children: [
              const Spacer(),
              Material(
                color: colors.success,
                shape: const CircleBorder(),
                child: InkWell(
                  customBorder: const CircleBorder(),
                  onTap: () {
                    final digits = _searchController.text.replaceAll(
                      RegExp(r'[^0-9]'),
                      '',
                    );
                    final matches = widget.dataStore.contacts.where((contact) {
                      if (contact.id == widget.dataStore.currentUser.id) {
                        return false;
                      }
                      final phoneDigits = contact.phone.replaceAll(
                        RegExp(r'[^0-9]'),
                        '',
                      );
                      return digits.length >= 7 &&
                          (phoneDigits == digits || phoneDigits.endsWith(digits));
                    }).toList(growable: false);
                    if (matches.length != 1) {
                      ChatyToast.show(
                        context,
                        matches.isEmpty
                            ? 'Enter a registered Chaty number to call.'
                            : 'More than one contact matches. Choose the right contact below.',
                        background: matches.isEmpty ? colors.error : colors.surfaceElevated,
                      );
                      return;
                    }
                    _startCall(matches.single, isVideo: false);
                  },
                  child: const SizedBox(
                    width: 58,
                    height: 58,
                    child: Icon(Icons.call_rounded, color: Colors.white, size: 27),
                  ),
                ),
              ),
              const Spacer(),
              IconButton(
                tooltip: 'Delete last digit',
                onPressed: _searchController.text.isEmpty ? null : _removeDigit,
                icon: Icon(
                  Icons.backspace_outlined,
                  color: _searchController.text.isEmpty
                      ? colors.foregroundTertiary
                      : colors.foregroundSecondary,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final contacts = _visibleContacts;
    final contactCount = contacts.length;
    final contactsLabel =
        contactCount == 1 ? '1 contact' : '$contactCount contacts';
    final isStartingCall = _startingContactId != null;
    final query = _searchController.text.trim();

    return Scaffold(
      backgroundColor: colors.background,
      appBar: AppBar(
        automaticallyImplyLeading: false,
        backgroundColor: colors.background,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          tooltip: 'Back',
          onPressed: () => Navigator.of(context).maybePop(),
          icon: Icon(Icons.arrow_back_rounded, color: colors.foreground),
        ),
        title: Text(
          _isDialpad ? 'Add call' : 'Contacts',
          style: TextStyle(
            color: colors.foreground,
            fontSize: 20,
            fontWeight: FontWeight.w800,
            letterSpacing: -0.3,
          ),
        ),
        actions: [
          IconButton(
            tooltip: _isDialpad ? 'Show contacts' : 'Open dialpad',
            onPressed: _switchMode,
            icon: Icon(
              _isDialpad ? Icons.contacts_rounded : Icons.dialpad_rounded,
              color: colors.primary,
              size: 24,
            ),
          ),
          const SizedBox(width: 4),
        ],
      ),
      body: SafeArea(
        top: false,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(18, 6, 18, 5),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _searchController,
                      focusNode: _searchFocusNode,
                      readOnly: _isDialpad,
                      showCursor: !_isDialpad,
                      keyboardType: _isDialpad
                          ? TextInputType.none
                          : TextInputType.text,
                      textInputAction: TextInputAction.search,
                      onChanged: (_) => setState(() {}),
                      onSubmitted: (_) => _searchFocusNode.unfocus(),
                      textAlign: _isDialpad ? TextAlign.center : TextAlign.start,
                      style: TextStyle(
                        color: colors.foreground,
                        fontSize: _isDialpad ? 28 : 16,
                        letterSpacing: _isDialpad ? 1.4 : 0,
                        fontWeight: _isDialpad
                            ? FontWeight.w400
                            : FontWeight.w500,
                      ),
                      decoration: InputDecoration(
                        hintText: _isDialpad ? 'Enter number' : 'Search contacts',
                        prefixIcon: _isDialpad
                            ? null
                            : Icon(
                                Icons.search_rounded,
                                color: colors.foregroundSecondary,
                              ),
                        filled: true,
                        fillColor: colors.surfaceSecondary,
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 14,
                        ),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(18),
                          borderSide: BorderSide.none,
                        ),
                        enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(18),
                          borderSide: BorderSide(color: colors.borderSubtle),
                        ),
                        focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(18),
                          borderSide: BorderSide(color: colors.primary, width: 1.4),
                        ),
                      ),
                    ),
                  ),
                  if (_isDialpad && query.isNotEmpty)
                    IconButton(
                      tooltip: 'Clear number',
                      onPressed: () {
                        _searchController.clear();
                        setState(() {});
                      },
                      icon: Icon(Icons.close_rounded, color: colors.foregroundSecondary),
                    ),
                ],
              ),
            ),
            if (_isDialpad) ...[
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 2),
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: Text(
                    query.isEmpty
                        ? 'Enter a number to find a Chaty contact'
                        : contacts.isEmpty
                            ? 'No registered contact matches this number'
                            : contacts.length == 1
                                ? '1 matching Chaty contact'
                                : '${contacts.length} matching Chaty contacts',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: contacts.isEmpty && query.isNotEmpty
                          ? colors.foregroundSecondary
                          : colors.foregroundSecondary,
                      fontSize: 12,
                    ),
                  ),
                ),
              ),
              _buildDialpad(),
            ] else
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 4, 20, 8),
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: Text(
                    contactsLabel,
                    style: TextStyle(
                      color: colors.foregroundSecondary,
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),
            Expanded(
              child: contacts.isEmpty
                  ? Center(
                      child: Padding(
                        padding: const EdgeInsets.all(28),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              query.isEmpty
                                  ? Icons.people_outline_rounded
                                  : Icons.person_search_rounded,
                              size: 42,
                              color: colors.foregroundTertiary,
                            ),
                            const SizedBox(height: 12),
                            Text(
                              query.isEmpty
                                  ? 'No contacts available'
                                  : 'No matching contacts',
                              style: TextStyle(
                                color: colors.foreground,
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 5),
                            Text(
                              query.isEmpty
                                  ? 'Your registered Chaty contacts will appear here.'
                                  : 'Calls are available for registered Chaty contacts only.',
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                color: colors.foregroundSecondary,
                                fontSize: 13,
                              ),
                            ),
                          ],
                        ),
                      ),
                    )
                  : ListView.separated(
                      keyboardDismissBehavior:
                          ScrollViewKeyboardDismissBehavior.onDrag,
                      padding: const EdgeInsets.fromLTRB(12, 4, 12, 24),
                      itemCount: contacts.length,
                      separatorBuilder: (_, __) => Divider(
                        height: 1,
                        indent: 72,
                        color: colors.divider,
                      ),
                      itemBuilder: (context, index) {
                        final contact = contacts[index];
                        final name = (contact.displayName as String).trim();
                        final phone = (contact.phone as String).trim();
                        final about = (contact.about as String).trim();
                        final subtitle = phone.isNotEmpty
                            ? phone
                            : (about.isNotEmpty ? about : 'Chaty contact');
                        final isThisCallStarting =
                            _startingContactId == contact.id;

                        return ListTile(
                          minVerticalPadding: 8,
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 4,
                          ),
                          leading: AppAvatar(
                            initials: _initials(name),
                            colorHex: contact.avatarColorHex as String?,
                            size: 46,
                          ),
                          title: Text(
                            name.isEmpty ? 'Chaty contact' : name,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: colors.foreground,
                              fontSize: 15,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          subtitle: Text(
                            subtitle,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: colors.foregroundSecondary,
                              fontSize: 12.5,
                            ),
                          ),
                          trailing: isThisCallStarting
                              ? SizedBox(
                                  width: 40,
                                  height: 40,
                                  child: Padding(
                                    padding: const EdgeInsets.all(10),
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2.2,
                                      color: colors.primary,
                                    ),
                                  ),
                                )
                              : Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    IconButton(
                                      tooltip: 'Voice call',
                                      visualDensity: VisualDensity.compact,
                                      onPressed: isStartingCall
                                          ? null
                                          : () => _startCall(contact, isVideo: false),
                                      icon: Icon(
                                        Icons.call_rounded,
                                        color: colors.primary,
                                        size: 21,
                                      ),
                                    ),
                                    IconButton(
                                      tooltip: 'Video call',
                                      visualDensity: VisualDensity.compact,
                                      onPressed: isStartingCall
                                          ? null
                                          : () => _startCall(contact, isVideo: true),
                                      icon: Icon(
                                        Icons.videocam_rounded,
                                        color: colors.primary,
                                        size: 22,
                                      ),
                                    ),
                                  ],
                                ),
                          onTap: isThisCallStarting
                              ? null
                              : () => _startCall(contact, isVideo: false),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}
