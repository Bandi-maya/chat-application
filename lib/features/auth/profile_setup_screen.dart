import 'package:flutter/material.dart';
import '../../../ui/core/theme/theme_controller.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../features/chats/main_navigation_shell.dart';
import '../../injection/locator.dart';

class ProfileSetupScreen extends StatefulWidget {
  const ProfileSetupScreen({super.key});

  @override
  State<ProfileSetupScreen> createState() => _ProfileSetupScreenState();
}

class _ProfileSetupScreenState extends State<ProfileSetupScreen> {
  late final ThemeController themeController;
  late final MockDataStore dataStore;
  late final TextEditingController _nameCtrl;
  late final TextEditingController _usernameCtrl;
  late final TextEditingController _aboutCtrl;

  @override
  void initState() {
    super.initState();
    themeController = locator<ThemeController>();
    dataStore = locator<MockDataStore>();
    final user = dataStore.currentUser;
    _nameCtrl = TextEditingController(text: user.displayName);
    _usernameCtrl = TextEditingController(text: user.username);
    _aboutCtrl = TextEditingController(text: user.about);
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _usernameCtrl.dispose();
    _aboutCtrl.dispose();
    super.dispose();
  }

  void _saveAndEnterHome() async {
    final updatedUser = dataStore.currentUser.copyWith(
      displayName: _nameCtrl.text,
      username: _usernameCtrl.text,
      about: _aboutCtrl.text,
    );
    await dataStore.updateUser(updatedUser);
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (_) => const MainNavigationShell()),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = themeController.globalTheme;
    final user = dataStore.currentUser;

    return ChatyScaffold(
      appBar: const ChatyAppBar(
        title: 'Complete Profile',
        leading: ChatyBackButton(),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(ChatySpacing.base),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: ChatySpacing.md),
              Center(
                child: Stack(
                  children: [
                    AppAvatar(
                      initials: user.avatarInitials,
                      colorHex: user.avatarColorHex,
                      size: 96,
                    ),
                    Positioned(
                      bottom: 0,
                      right: 0,
                      child: Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: theme.accentColor,
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: theme.backgroundColor,
                            width: 2.5,
                          ),
                        ),
                        child: Icon(
                          Icons.camera_alt_rounded,
                          color: theme.onAccentColor,
                          size: 16,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: ChatySpacing.xl),
              ChatyGroupedSection(
                title: 'Public Identity',
                children: [
                  Padding(
                    padding: const EdgeInsets.all(ChatySpacing.md),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        ChatyInput(
                          label: 'Display Name',
                          hintText: 'Your public name',
                          controller: _nameCtrl,
                          prefixIcon: const Icon(
                            Icons.person_outline_rounded,
                            size: 20,
                          ),
                        ),
                        const SizedBox(height: ChatySpacing.md),
                        ChatyInput(
                          label: 'Username',
                          hintText: 'e.g. @bandi_maya',
                          controller: _usernameCtrl,
                          prefixIcon: const Icon(
                            Icons.alternate_email_rounded,
                            size: 20,
                          ),
                        ),
                        const SizedBox(height: ChatySpacing.md),
                        ChatyInput(
                          label: 'About / Status',
                          hintText: 'Write a short bio or status...',
                          controller: _aboutCtrl,
                          maxLines: 3,
                          prefixIcon: const Icon(
                            Icons.info_outline_rounded,
                            size: 20,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: ChatySpacing.xl),
              ChatyPrimaryButton(
                text: 'Enter Chaty',
                onPressed: _saveAndEnterHome,
              ),
              const SizedBox(height: ChatySpacing.lg),
            ],
          ),
        ),
      ),
    );
  }
}

