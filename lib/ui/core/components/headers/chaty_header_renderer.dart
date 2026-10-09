import 'dart:ui';
import 'package:flutter/material.dart';
import '../../design_system/components/app_components.dart';
import '../../theme/app_theme.dart';

class ChatyHeaderData {
  final String title;
  final String? subtitle;
  final Widget? avatar;
  final List<Widget>? actions;
  final Widget? leading;
  final VoidCallback? onBack;
  final VoidCallback? onTitleTap;
  final int selectionCount;
  final bool isSelectionMode;
  final List<Widget>? selectionActions;
  final VoidCallback? onClearSelection;
  final String? callDurationLabel;
  final bool isCallActive;

  const ChatyHeaderData({
    required this.title,
    this.subtitle,
    this.avatar,
    this.actions,
    this.leading,
    this.onBack,
    this.onTitleTap,
    this.selectionCount = 0,
    this.isSelectionMode = false,
    this.selectionActions,
    this.onClearSelection,
    this.callDurationLabel,
    this.isCallActive = false,
  });
}

class ChatyHeaderRenderer extends StatelessWidget implements PreferredSizeWidget {
  final String variantId;
  final ChatyHeaderData data;
  final Color? backgroundColor;
  final Color? foregroundColor;
  final double elevation;

  const ChatyHeaderRenderer({
    super.key,
    required this.variantId,
    required this.data,
    this.backgroundColor,
    this.foregroundColor,
    this.elevation = 0,
  });

  @override
  Size get preferredSize {
    if (variantId == 'large_context') {
      return const Size.fromHeight(74.0);
    }
    if (variantId == 'compact') {
      return const Size.fromHeight(48.0);
    }
    return const Size.fromHeight(kToolbarHeight);
  }

  @override
  Widget build(BuildContext context) {
    if (data.isSelectionMode) {
      return _buildSelectionHeader(context);
    }
    if (data.isCallActive) {
      return _buildCallContextHeader(context);
    }

    switch (variantId) {
      case 'large_context':
        return _buildLargeContextHeader(context);
      case 'compact':
        return _buildCompactHeader(context);
      case 'search_first':
        return _buildSearchFirstHeader(context);
      case 'glass_strip':
        return _buildGlassStripHeader(context);
      case 'profile_led':
        return _buildProfileLedHeader(context);
      case 'standard_identity':
      default:
        return _buildStandardIdentityHeader(context);
    }
  }

  // ---------------------------------------------------------------------------
  // HDR-07: Selection Header
  // ---------------------------------------------------------------------------
  Widget _buildSelectionHeader(BuildContext context) {
    final colors = context.colors;
    final bg = backgroundColor ?? colors.surface;
    final fg = foregroundColor ?? colors.foreground;

    return AppBar(
      backgroundColor: bg,
      foregroundColor: fg,
      elevation: 1,
      leading: IconButton(
        icon: const Icon(Icons.close_rounded),
        tooltip: 'Close selection',
        onPressed: data.onClearSelection,
      ),
      title: Text(
        '${data.selectionCount} selected',
        style: TextStyle(
          fontSize: 17,
          fontWeight: FontWeight.w700,
          color: fg,
        ),
      ),
      actions: data.selectionActions,
    );
  }

  // ---------------------------------------------------------------------------
  // HDR-08: Call Context Header
  // ---------------------------------------------------------------------------
  Widget _buildCallContextHeader(BuildContext context) {
    final colors = context.colors;
    final bg = colors.success;

    return AppBar(
      backgroundColor: bg,
      foregroundColor: Colors.white,
      elevation: 2,
      leading: _buildLeading(context, Colors.white),
      title: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            data.title,
            style: const TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w700,
              color: Colors.white,
            ),
          ),
          Text(
            data.callDurationLabel ?? 'Call in progress',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: Colors.white.withValues(alpha: 0.85),
            ),
          ),
        ],
      ),
      actions: data.actions,
    );
  }

  // ---------------------------------------------------------------------------
  // HDR-01: Standard Identity (Default)
  // ---------------------------------------------------------------------------
  Widget _buildStandardIdentityHeader(BuildContext context) {
    final colors = context.colors;
    final bg = backgroundColor ?? colors.surface;
    final fg = foregroundColor ?? colors.foreground;

    return AppBar(
      backgroundColor: bg,
      foregroundColor: fg,
      elevation: elevation,
      leading: _buildLeading(context, fg),
      leadingWidth: 54,
      title: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: data.onTitleTap,
        child: Row(
          children: [
            if (data.avatar != null) ...[
              data.avatar!,
              const SizedBox(width: 10),
            ],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    data.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: fg,
                      letterSpacing: -0.2,
                    ),
                  ),
                  if (data.subtitle != null) ...[
                    Text(
                      data.subtitle!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w500,
                        color: colors.foregroundSecondary,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
      actions: data.actions,
    );
  }

  // ---------------------------------------------------------------------------
  // HDR-02: Large Context
  // ---------------------------------------------------------------------------
  Widget _buildLargeContextHeader(BuildContext context) {
    final colors = context.colors;
    final bg = backgroundColor ?? colors.surface;
    final fg = foregroundColor ?? colors.foreground;

    return AppBar(
      toolbarHeight: 74,
      backgroundColor: bg,
      foregroundColor: fg,
      elevation: elevation,
      leading: _buildLeading(context, fg),
      title: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: data.onTitleTap,
        child: Row(
          children: [
            if (data.avatar != null) ...[
              SizedBox.square(dimension: 48, child: data.avatar!),
              const SizedBox(width: 12),
            ],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    data.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w800,
                      color: fg,
                    ),
                  ),
                  if (data.subtitle != null)
                    Text(
                      data.subtitle!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 12.5,
                        fontWeight: FontWeight.w500,
                        color: colors.foregroundSecondary,
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
      actions: data.actions,
    );
  }

  // ---------------------------------------------------------------------------
  // HDR-03: Compact Header
  // ---------------------------------------------------------------------------
  Widget _buildCompactHeader(BuildContext context) {
    final colors = context.colors;
    final bg = backgroundColor ?? colors.surface;
    final fg = foregroundColor ?? colors.foreground;

    return AppBar(
      toolbarHeight: 48,
      backgroundColor: bg,
      foregroundColor: fg,
      elevation: elevation,
      leading: _buildLeading(context, fg),
      title: Text(
        data.title,
        style: TextStyle(
          fontSize: 14.5,
          fontWeight: FontWeight.w700,
          color: fg,
        ),
      ),
      actions: data.actions,
    );
  }

  // ---------------------------------------------------------------------------
  // HDR-04: Search First Header
  // ---------------------------------------------------------------------------
  Widget _buildSearchFirstHeader(BuildContext context) {
    final colors = context.colors;
    final bg = backgroundColor ?? colors.surface;
    final fg = foregroundColor ?? colors.foreground;

    return AppBar(
      backgroundColor: bg,
      foregroundColor: fg,
      elevation: elevation,
      leading: _buildLeading(context, fg),
      title: Row(
        children: [
          Expanded(
            child: Text(
              data.title,
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w700,
                color: fg,
              ),
            ),
          ),
        ],
      ),
      actions: [
        if (data.actions != null) ...data.actions!,
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // HDR-05: Glass Strip Header
  // ---------------------------------------------------------------------------
  Widget _buildGlassStripHeader(BuildContext context) {
    final colors = context.colors;
    final fg = foregroundColor ?? colors.foreground;

    return ClipRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
        child: AppBar(
          backgroundColor:
              (backgroundColor ?? colors.surface).withValues(alpha: 0.75),
          foregroundColor: fg,
          elevation: 0,
          leading: _buildLeading(context, fg),
          title: Text(
            data.title,
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w700,
              color: fg,
            ),
          ),
          actions: data.actions,
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // HDR-06: Profile Led Header
  // ---------------------------------------------------------------------------
  Widget _buildProfileLedHeader(BuildContext context) {
    return _buildLargeContextHeader(context);
  }

  Widget? _buildLeading(BuildContext context, Color color) {
    if (data.leading != null) return data.leading;
    final canPop = Navigator.of(context).canPop();
    if (!canPop) return null;
    return ChatyBackButton(
      color: color,
      onPressed: data.onBack,
    );
  }
}
