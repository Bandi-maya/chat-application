#!/usr/bin/env python3
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / 'lib/features/profile/profile_screen.dart'
text = path.read_text(encoding='utf-8')

if "../../ui/core/widgets/cached_remote_image.dart" not in text:
    text = text.replace(
        "import '../../ui/core/design_system/settings_primitives.dart';",
        "import '../../ui/core/design_system/settings_primitives.dart';\n"
        "import '../../ui/core/widgets/app_avatar.dart';\n"
        "import '../../ui/core/widgets/cached_remote_image.dart';",
        1,
    )
elif "../../ui/core/widgets/app_avatar.dart" not in text:
    text = text.replace(
        "import '../../ui/core/widgets/cached_remote_image.dart';",
        "import '../../ui/core/widgets/app_avatar.dart';\n"
        "import '../../ui/core/widgets/cached_remote_image.dart';",
        1,
    )

old_banner = '''                          ? Image.network(
                              bannerUrl!,
                              fit: BoxFit.cover,
                              errorBuilder: (_, __, ___) => _BannerFallback(
                                colors: colors,
                              ),
                            )'''
new_banner = '''                          ? ChatyCachedRemoteImage(
                              url: bannerUrl!,
                              fit: BoxFit.cover,
                              fallback: _BannerFallback(colors: colors),
                            )'''
text = text.replace(old_banner, new_banner, 1)

old_avatar = '''                  child: ChatyNetworkAvatar(
                    initials: initials,
                    colorHex: colorHex,
                    url: avatarUrl,
                    size: 92,
                  ),'''
new_avatar = '''                  child: _CachedProfileAvatar(
                    initials: initials,
                    colorHex: colorHex,
                    avatarUrl: avatarUrl,
                    size: 92,
                  ),'''
text = text.replace(old_avatar, new_avatar, 1)

old_error = '''      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not update banner: $message')),
      );'''
new_error = '''      debugPrint('Chaty banner update failed: $message');
      ChatyActivityIsland.show(
        context,
        icon: Icons.image_not_supported_outlined,
        title: 'Banner wasn’t updated',
        subtitle: 'Your previous banner is still available. Check the connection and retry.',
      );'''
text = text.replace(old_error, new_error, 1)

if 'class _CachedProfileAvatar extends StatelessWidget' not in text:
    marker = '/// Small bordered "Edit profile" pill'
    widget = r'''class _CachedProfileAvatar extends StatelessWidget {
  const _CachedProfileAvatar({
    required this.initials,
    required this.colorHex,
    required this.avatarUrl,
    required this.size,
  });

  final String initials;
  final String colorHex;
  final String? avatarUrl;
  final double size;

  @override
  Widget build(BuildContext context) {
    final url = avatarUrl?.trim() ?? '';
    final fallback = AppAvatar(
      initials: initials,
      colorHex: colorHex,
      size: size,
    );
    if (url.isEmpty) return fallback;
    if (!(url.startsWith('http://') || url.startsWith('https://'))) {
      return ClipOval(
        child: Image.file(
          File(url.replaceFirst('file://', '')),
          width: size,
          height: size,
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => fallback,
        ),
      );
    }
    return ClipOval(
      child: SizedBox(
        width: size,
        height: size,
        child: ChatyCachedRemoteImage(
          url: url,
          fit: BoxFit.cover,
          fallback: fallback,
        ),
      ),
    );
  }
}

'''
    if marker not in text:
        raise SystemExit('profile avatar insertion marker missing')
    text = text.replace(marker, widget + marker, 1)

for needle in (
    "import '../../ui/core/widgets/app_avatar.dart';",
    'ChatyCachedRemoteImage(',
    'class _CachedProfileAvatar',
):
    if needle not in text:
        raise SystemExit(f'profile-cache invariant missing: {needle}')

path.write_text(text, encoding='utf-8')
print('Profile media cache UX applied.')

# Canonical frontend execution order. Legacy/device compatibility runs before
# the design foundation. Grouped implementation phases then layer onto one
# deterministic final tree that analyzer/tests/build all verify.
for tool in (
    'prepare_frontend_master_input.py',
    'apply_frontend_master_plan.py',
    'restore_frontend_design_exports.py',
    'apply_frontend_namespace_cleanup.py',
    'apply_frontend_p4_p7.py',
    'apply_frontend_p8_p10.py',
    'apply_frontend_p11_p14.py',
):
    script = ROOT / 'tools' / tool
    if script.exists():
        runpy.run_path(str(script), run_name='__main__')
