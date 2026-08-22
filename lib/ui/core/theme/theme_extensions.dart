import 'package:flutter/material.dart';
import 'semantic_colors.dart';
import 'chat_theme_tokens.dart';

/// Extension methods on BuildContext for quick and clean theme color access.
///
/// Example usage:
/// `context.colors.primary`
/// `context.colors.surface`
/// `context.chatColors.outgoingBubble`
extension ThemeExtensions on BuildContext {
  ThemeData get theme => Theme.of(this);

  AppColors get colors =>
      Theme.of(this).extension<AppColors>() ?? _fallbackAppColors;

  ChatColors get chatColors =>
      Theme.of(this).extension<ChatColors>() ?? _fallbackChatColors;

  bool get isDarkMode => Theme.of(this).brightness == Brightness.dark;
}

/// Fallback instance in case context is accessed without theme extension mounted
final AppColors _fallbackAppColors = AppColors(
  brightness: Brightness.dark,
  primary: const Color(0xFF6366F1),
  onPrimary: Colors.white,
  secondary: const Color(0xFF818CF8),
  onSecondary: Colors.white,
  accent: const Color(0xFF6366F1),
  onAccent: Colors.white,
  background: const Color(0xFF0B0F19),
  onBackground: const Color(0xFFF9FAFB),
  surface: const Color(0xFF111827),
  onSurface: const Color(0xFFF9FAFB),
  surfaceSecondary: const Color(0xFF1E293B),
  onSurfaceSecondary: const Color(0xFF94A3B8),
  surfaceElevated: const Color(0xFF1E293B),
  onSurfaceElevated: const Color(0xFFF9FAFB),
  foreground: const Color(0xFFF9FAFB),
  foregroundSecondary: const Color(0xFF94A3B8),
  foregroundTertiary: const Color(0xFF64748B),
  border: const Color(0xFF1E293B),
  borderSubtle: const Color(0xFF0F172A),
  divider: const Color(0xFF1E293B),
  input: const Color(0xFF111827),
  inputBorder: const Color(0xFF1E293B),
  inputFill: const Color(0xFF111827),
  disabled: const Color(0xFF334155),
  disabledForeground: const Color(0xFF64748B),
  selected: const Color(0xFF6366F1),
  onSelected: Colors.white,
  hover: const Color(0x1A6366F1),
  pressed: const Color(0x336366F1),
  success: const Color(0xFF10B981),
  onSuccess: Colors.white,
  warning: const Color(0xFFF59E0B),
  onWarning: Colors.black,
  error: const Color(0xFFEF4444),
  onError: Colors.white,
  info: const Color(0xFF38BDF8),
  onInfo: Colors.black,
  link: const Color(0xFF38BDF8),
  icon: const Color(0xFFF9FAFB),
  iconSecondary: const Color(0xFF94A3B8),
  shadow: const Color(0x66000000),
);

final ChatColors _fallbackChatColors = ChatColors(
  incomingBubble: const Color(0xFF1E293B),
  incomingText: const Color(0xFFF1F5F9),
  outgoingBubble: const Color(0xFF4F46E5),
  outgoingText: Colors.white,
  replySurface: const Color(0xFF0F172A),
  replyBorder: const Color(0xFF6366F1),
  composerSurface: const Color(0xFF111827),
  composerBorder: const Color(0xFF1E293B),
  messageMetadata: const Color(0x99F1F5F9),
  deliveryTick: const Color(0x99F1F5F9),
  readTick: const Color(0xFF38BDF8),
  reactionSurface: const Color(0xFF1E293B),
  reactionBorder: const Color(0x406366F1),
  reactionCount: const Color(0xFFF9FAFB),
  mentionBackground: const Color(0x336366F1),
  selectionBackground: const Color(0x406366F1),
  chatWallpaperBackground: const Color(0xFF0B0F19),
  voiceNoteWaveform: const Color(0xFF818CF8),
  voiceNoteProgress: const Color(0xFF6366F1),
  voiceNoteButton: const Color(0xFF6366F1),
  systemMessageBackground: const Color(0xCC111827),
  systemMessageText: const Color(0xFF94A3B8),
  taskCardSurface: const Color(0xFF1E293B),
  taskCardBorder: const Color(0x406366F1),
);
