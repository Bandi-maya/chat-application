import 'package:flutter/material.dart';
import '../tokens/app_tokens.dart';
import '../../theme/app_theme.dart';

/// ---------------------------------------------------------------------------
/// CHATY SCAFFOLD (Standardized background & safe-area handling)
/// ---------------------------------------------------------------------------
class ChatyScaffold extends StatelessWidget {
  final Widget body;
  final PreferredSizeWidget? appBar;
  final Widget? bottomNavigationBar;
  final Widget? floatingActionButton;
  final Color? backgroundColor;
  final bool extendBody;
  final bool extendBodyBehindAppBar;
  final bool safeAreaTop;
  final bool safeAreaBottom;

  const ChatyScaffold({
    super.key,
    required this.body,
    this.appBar,
    this.bottomNavigationBar,
    this.floatingActionButton,
    this.backgroundColor,
    this.extendBody = false,
    this.extendBodyBehindAppBar = false,
    this.safeAreaTop = true,
    this.safeAreaBottom = true,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      backgroundColor: backgroundColor ?? theme.scaffoldBackgroundColor,
      appBar: appBar,
      body: SafeArea(
        top: safeAreaTop && !extendBodyBehindAppBar,
        bottom: safeAreaBottom,
        child: body,
      ),
      bottomNavigationBar: bottomNavigationBar,
      floatingActionButton: floatingActionButton,
      extendBody: extendBody,
      extendBodyBehindAppBar: extendBodyBehindAppBar,
    );
  }
}

/// ---------------------------------------------------------------------------
/// CHATY BACK BUTTON (Apple HIG styled leading chevron)
/// ---------------------------------------------------------------------------
class ChatyBackButton extends StatefulWidget {
  final VoidCallback? onPressed;
  final Color? color;
  final Color? backgroundColor;
  final double size;

  const ChatyBackButton({
    super.key,
    this.onPressed,
    this.color,
    this.backgroundColor,
    this.size = 34.0,
  });

  @override
  State<ChatyBackButton> createState() => _ChatyBackButtonState();
}

class _ChatyBackButtonState extends State<ChatyBackButton> {
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final fg = widget.color ?? colors.foreground;
    final bg = widget.backgroundColor ?? colors.surfaceSecondary;
    final border = colors.border;

    return ConstrainedBox(
      constraints: const BoxConstraints(minWidth: 44.0, minHeight: 44.0),
      child: Center(
        child: AnimatedScale(
          scale: _isPressed ? ChatyMotion.activeIconScale : 1.0,
          duration: ChatyMotion.instant,
          curve: ChatyMotion.enter,
          child: Material(
            color: Colors.transparent,
            child: InkWell(
              onTap: () {
                ChatyMotion.selection();
                if (widget.onPressed != null) {
                  widget.onPressed!();
                } else {
                  Navigator.of(context).maybePop();
                }
              },
              onTapDown: (_) => setState(() => _isPressed = true),
              onTapUp: (_) => setState(() => _isPressed = false),
              onTapCancel: () => setState(() => _isPressed = false),
              borderRadius: BorderRadius.circular(ChatyRadius.full),
              child: Container(
                width: widget.size,
                height: widget.size,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: bg,
                  border: Border.all(color: border, width: 0.8),
                ),
                alignment: Alignment.center,
                child: Icon(Icons.chevron_left_rounded, size: 24, color: fg),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// ---------------------------------------------------------------------------
/// CHATY APP BAR (Restrained, clear typography & uniform height)
/// ---------------------------------------------------------------------------
class ChatyAppBar extends StatelessWidget implements PreferredSizeWidget {
  final String? title;
  final Widget? titleWidget;
  final String? subtitle;
  final List<Widget>? actions;
  final Widget? leading;
  final bool automaticallyImplyLeading;
  final bool centerTitle;
  final Color? backgroundColor;
  final Color? foregroundColor;
  final double elevation;
  final PreferredSizeWidget? bottom;

  const ChatyAppBar({
    super.key,
    this.title,
    this.titleWidget,
    this.subtitle,
    this.actions,
    this.leading,
    this.automaticallyImplyLeading = true,
    this.centerTitle = false,
    this.backgroundColor,
    this.foregroundColor,
    this.elevation = 0,
    this.bottom,
  });

  @override
  Size get preferredSize =>
      Size.fromHeight(kToolbarHeight + (bottom?.preferredSize.height ?? 0.0));

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final canPop = Navigator.of(context).canPop();

    Widget? leadingWidget = leading;
    if (leadingWidget == null && automaticallyImplyLeading && canPop) {
      leadingWidget = const Center(
        child: Padding(
          padding: EdgeInsets.only(left: ChatySpacing.sm),
          child: ChatyBackButton(),
        ),
      );
    }

    Widget? titleContent = titleWidget;
    if (titleContent == null && title != null) {
      titleContent = Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: centerTitle
            ? CrossAxisAlignment.center
            : CrossAxisAlignment.start,
        children: [
          Text(
            title!,
            style: ChatyTypography.title(
              foregroundColor ?? theme.colorScheme.onSurface,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          if (subtitle != null) ...[
            const SizedBox(height: 1),
            Text(
              subtitle!,
              style: ChatyTypography.caption(
                (foregroundColor ?? theme.colorScheme.onSurface).withValues(
                  alpha: 0.65,
                ),
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ],
      );
    }

    return AppBar(
      title: titleContent,
      leading: leadingWidget,
      leadingWidth: (leadingWidget != null) ? 56.0 : null,
      automaticallyImplyLeading: false,
      actions: actions != null
          ? [...actions!, const SizedBox(width: ChatySpacing.sm)]
          : null,
      centerTitle: centerTitle,
      backgroundColor: backgroundColor ?? theme.scaffoldBackgroundColor,
      foregroundColor: foregroundColor ?? theme.colorScheme.onSurface,
      elevation: elevation,
      scrolledUnderElevation: 0.5,
      surfaceTintColor: Colors.transparent,
      bottom: bottom,
    );
  }
}

/// ---------------------------------------------------------------------------
/// CHATY BUTTONS (Primary, Secondary, Text, Icon with tactile scale)
/// ---------------------------------------------------------------------------
class ChatyPrimaryButton extends StatefulWidget {
  final String text;
  final IconData? icon;
  final VoidCallback? onPressed;
  final bool isLoading;
  final Color? backgroundColor;
  final Color? foregroundColor;
  final double height;
  final double? width;
  final double borderRadius;

  final EdgeInsetsGeometry? padding;

  const ChatyPrimaryButton({
    super.key,
    required this.text,
    this.icon,
    this.onPressed,
    this.isLoading = false,
    this.backgroundColor,
    this.foregroundColor,
    this.height = ChatyTouchTargets.buttonHeight,
    this.width = double.infinity,
    this.borderRadius = ChatyRadius.lg,
    this.padding,
  });

  @override
  State<ChatyPrimaryButton> createState() => _ChatyPrimaryButtonState();
}

class _ChatyPrimaryButtonState extends State<ChatyPrimaryButton> {
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isEnabled = widget.onPressed != null && !widget.isLoading;
    final bg = widget.backgroundColor ?? theme.colorScheme.primary;
    final fg = widget.foregroundColor ?? Colors.white;
    final defaultHorizontalPadding =
        (widget.height < 40 || (widget.width != null && widget.width! < 100))
        ? ChatySpacing.sm
        : ChatySpacing.lg;

    return AnimatedScale(
      scale: _isPressed && isEnabled ? ChatyMotion.activeScale : 1.0,
      duration: ChatyMotion.instant,
      curve: ChatyMotion.enter,
      child: SizedBox(
        height: widget.height,
        width: widget.width,
        child: ElevatedButton(
          onPressed: isEnabled
              ? () {
                  ChatyMotion.selection();
                  widget.onPressed?.call();
                }
              : null,
          style: ElevatedButton.styleFrom(
            backgroundColor: bg,
            foregroundColor: fg,
            disabledBackgroundColor: bg.withValues(alpha: 0.45),
            disabledForegroundColor: fg.withValues(alpha: 0.6),
            elevation: 0,
            minimumSize: Size.zero,
            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
            padding:
                widget.padding ??
                EdgeInsets.symmetric(horizontal: defaultHorizontalPadding),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(widget.borderRadius),
            ),
          ),
          child: Listener(
            onPointerDown: (_) {
              if (isEnabled) setState(() => _isPressed = true);
            },
            onPointerUp: (_) {
              if (_isPressed) setState(() => _isPressed = false);
            },
            onPointerCancel: (_) {
              if (_isPressed) setState(() => _isPressed = false);
            },
            child: AnimatedSwitcher(
              duration: ChatyMotion.fast,
              child: widget.isLoading
                  ? SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                        strokeWidth: 2.2,
                        valueColor: AlwaysStoppedAnimation<Color>(fg),
                      ),
                    )
                  : Row(
                      mainAxisSize: MainAxisSize.min,
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        if (widget.icon != null) ...[
                          Icon(widget.icon, size: ChatyIconSize.md, color: fg),
                          const SizedBox(width: ChatySpacing.xs),
                        ],
                        Flexible(
                          child: Text(
                            widget.text,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontSize: widget.height < 40 ? 13 : 15,
                              fontWeight: FontWeight.w600,
                              letterSpacing: -0.2,
                              color: fg,
                            ),
                          ),
                        ),
                      ],
                    ),
            ),
          ),
        ),
      ),
    );
  }
}

class ChatySecondaryButton extends StatefulWidget {
  final String text;
  final IconData? icon;
  final VoidCallback? onPressed;
  final Color? borderColor;
  final Color? textColor;
  final double height;
  final double? width;
  final double borderRadius;

  final EdgeInsetsGeometry? padding;

  const ChatySecondaryButton({
    super.key,
    required this.text,
    this.icon,
    this.onPressed,
    this.borderColor,
    this.textColor,
    this.height = ChatyTouchTargets.buttonHeight,
    this.width = double.infinity,
    this.borderRadius = ChatyRadius.lg,
    this.padding,
  });

  @override
  State<ChatySecondaryButton> createState() => _ChatySecondaryButtonState();
}

class _ChatySecondaryButtonState extends State<ChatySecondaryButton> {
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final isEnabled = widget.onPressed != null;
    final border = widget.borderColor ?? colors.border;
    final fg = widget.textColor ?? colors.foreground;
    final defaultHorizontalPadding =
        (widget.height < 40 || (widget.width != null && widget.width! < 100))
        ? ChatySpacing.sm
        : ChatySpacing.lg;

    return AnimatedScale(
      scale: _isPressed && isEnabled ? ChatyMotion.activeScale : 1.0,
      duration: ChatyMotion.instant,
      curve: ChatyMotion.enter,
      child: SizedBox(
        height: widget.height,
        width: widget.width,
        child: OutlinedButton(
          onPressed: isEnabled
              ? () {
                  ChatyMotion.selection();
                  widget.onPressed?.call();
                }
              : null,
          style: OutlinedButton.styleFrom(
            foregroundColor: fg,
            side: BorderSide(color: border, width: 1.2),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(widget.borderRadius),
            ),
            minimumSize: Size.zero,
            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
            padding:
                widget.padding ??
                EdgeInsets.symmetric(horizontal: defaultHorizontalPadding),
          ),
          child: Listener(
            onPointerDown: (_) {
              if (isEnabled) setState(() => _isPressed = true);
            },
            onPointerUp: (_) {
              if (_isPressed) setState(() => _isPressed = false);
            },
            onPointerCancel: (_) {
              if (_isPressed) setState(() => _isPressed = false);
            },
            child: Row(
              mainAxisSize: MainAxisSize.min,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                if (widget.icon != null) ...[
                  Icon(widget.icon, size: ChatyIconSize.md, color: fg),
                  const SizedBox(width: ChatySpacing.xs),
                ],
                Flexible(
                  child: Text(
                    widget.text,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: widget.height < 40 ? 13 : 15,
                      fontWeight: FontWeight.w600,
                      letterSpacing: -0.2,
                      color: fg,
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
}

class ChatyIconButton extends StatefulWidget {
  final IconData icon;
  final VoidCallback? onPressed;
  final String? tooltip;
  final Color? color;
  final Color? backgroundColor;
  final double size;
  final double iconSize;

  const ChatyIconButton({
    super.key,
    required this.icon,
    required this.onPressed,
    this.tooltip,
    this.color,
    this.backgroundColor,
    this.size = ChatyTouchTargets.minTouchTarget,
    this.iconSize = ChatyIconSize.md,
  });

  @override
  State<ChatyIconButton> createState() => _ChatyIconButtonState();
}

class _ChatyIconButtonState extends State<ChatyIconButton> {
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final iconColor = widget.color ?? colors.foreground;

    Widget button = Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: widget.onPressed != null
            ? () {
                ChatyMotion.selection();
                widget.onPressed!();
              }
            : null,
        onTapDown: (_) => setState(() => _isPressed = true),
        onTapUp: (_) => setState(() => _isPressed = false),
        onTapCancel: () => setState(() => _isPressed = false),
        borderRadius: BorderRadius.circular(ChatyRadius.full),
        child: Container(
          width: widget.size,
          height: widget.size,
          decoration: BoxDecoration(
            color: widget.backgroundColor ?? Colors.transparent,
            shape: BoxShape.circle,
          ),
          alignment: Alignment.center,
          child: AnimatedScale(
            scale: _isPressed && widget.onPressed != null
                ? ChatyMotion.activeIconScale
                : 1.0,
            duration: ChatyMotion.instant,
            curve: ChatyMotion.enter,
            child: Icon(
              widget.icon,
              size: widget.iconSize,
              color: widget.onPressed != null
                  ? iconColor
                  : iconColor.withValues(alpha: 0.35),
            ),
          ),
        ),
      ),
    );

    if (widget.tooltip != null) {
      button = Tooltip(message: widget.tooltip!, child: button);
    }

    return button;
  }
}

/// ---------------------------------------------------------------------------
/// CHATY CARDS & GROUPED SECTIONS (Reduced noise, balanced geometry)
/// ---------------------------------------------------------------------------
class ChatyCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry margin;
  final Color? backgroundColor;
  final Color? borderColor;
  final double borderRadius;
  final VoidCallback? onTap;

  const ChatyCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(ChatySpacing.base),
    this.margin = EdgeInsets.zero,
    this.backgroundColor,
    this.borderColor,
    this.borderRadius = ChatyRadius.card,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final bg = backgroundColor ?? colors.surface;
    final border = borderColor ?? colors.border;

    Widget card = Container(
      margin: margin,
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(borderRadius),
        border: Border.all(color: border, width: 1.0),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(borderRadius),
        child: Padding(padding: padding, child: child),
      ),
    );

    if (onTap != null) {
      card = InkWell(
        onTap: () {
          ChatyMotion.selection();
          onTap!();
        },
        borderRadius: BorderRadius.circular(borderRadius),
        child: card,
      );
    }

    return card;
  }
}

class ChatyGroupedSection extends StatelessWidget {
  final String? title;
  final String? description;
  final List<Widget> children;
  final EdgeInsetsGeometry margin;

  const ChatyGroupedSection({
    super.key,
    this.title,
    this.description,
    required this.children,
    this.margin = const EdgeInsets.only(bottom: ChatySpacing.base),
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final border = colors.border;
    final bg = colors.surface;

    return Padding(
      padding: margin,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (title != null) ...[
            Padding(
              padding: const EdgeInsets.only(
                left: ChatySpacing.md,
                bottom: ChatySpacing.sm,
              ),
              child: Text(
                title!.toUpperCase(),
                style: TextStyle(
                  fontSize: 11.5,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.0,
                  color: colors.primary,
                ),
              ),
            ),
          ],
          Container(
            decoration: BoxDecoration(
              color: bg,
              borderRadius: BorderRadius.circular(ChatyRadius.card),
              border: Border.all(color: border, width: 1.0),
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(ChatyRadius.card),
              child: Column(
                children: [
                  for (int i = 0; i < children.length; i++) ...[
                    children[i],
                    if (i < children.length - 1)
                      Divider(
                        height: 1,
                        thickness: 1,
                        indent: ChatySpacing.base,
                        endIndent: ChatySpacing.base,
                        color: border.withValues(alpha: 0.6),
                      ),
                  ],
                ],
              ),
            ),
          ),
          if (description != null) ...[
            Padding(
              padding: const EdgeInsets.only(
                left: ChatySpacing.md,
                right: ChatySpacing.md,
                top: ChatySpacing.xs,
              ),
              child: Text(
                description!,
                style: ChatyTypography.caption(colors.foregroundSecondary),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// ---------------------------------------------------------------------------
/// CHATY LIST TILE (Clean, uniform row architecture)
/// ---------------------------------------------------------------------------
class ChatyListTile extends StatelessWidget {
  final Widget? leading;
  final Widget title;
  final Widget? subtitle;
  final Widget? trailing;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final EdgeInsetsGeometry contentPadding;

  const ChatyListTile({
    super.key,
    this.leading,
    required this.title,
    this.subtitle,
    this.trailing,
    this.onTap,
    this.onLongPress,
    this.contentPadding = const EdgeInsets.symmetric(
      horizontal: ChatySpacing.base,
      vertical: ChatySpacing.md,
    ),
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap != null
          ? () {
              ChatyMotion.selection();
              onTap!();
            }
          : null,
      onLongPress: onLongPress != null
          ? () {
              ChatyMotion.medium();
              onLongPress!();
            }
          : null,
      child: Padding(
        padding: contentPadding,
        child: Row(
          children: [
            if (leading != null) ...[
              leading!,
              const SizedBox(width: ChatySpacing.base),
            ],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  title,
                  if (subtitle != null) ...[
                    const SizedBox(height: 2),
                    subtitle!,
                  ],
                ],
              ),
            ),
            if (trailing != null) ...[
              const SizedBox(width: ChatySpacing.sm),
              trailing!,
            ],
          ],
        ),
      ),
    );
  }
}

/// ---------------------------------------------------------------------------
/// CHATY TEXT INPUT (Standardized form fields & search)
/// ---------------------------------------------------------------------------
class ChatyInput extends StatelessWidget {
  final String? label;
  final String? hintText;
  final TextEditingController? controller;
  final bool obscureText;
  final Widget? prefixIcon;
  final Widget? suffixIcon;
  final TextInputType keyboardType;
  final TextInputAction textInputAction;
  final String? Function(String?)? validator;
  final ValueChanged<String>? onChanged;
  final FocusNode? focusNode;
  final bool autofocus;
  final bool enabled;
  final int maxLines;

  const ChatyInput({
    super.key,
    this.label,
    this.hintText,
    this.controller,
    this.obscureText = false,
    this.prefixIcon,
    this.suffixIcon,
    this.keyboardType = TextInputType.text,
    this.textInputAction = TextInputAction.next,
    this.validator,
    this.onChanged,
    this.focusNode,
    this.autofocus = false,
    this.enabled = true,
    this.maxLines = 1,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final fill = colors.inputFill;
    final border = colors.inputBorder;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (label != null) ...[
          Text(
            label!,
            style: TextStyle(
              color: colors.foreground,
              fontSize: 13.5,
              fontWeight: FontWeight.w600,
              letterSpacing: -0.1,
            ),
          ),
          const SizedBox(height: ChatySpacing.xs),
        ],
        TextFormField(
          controller: controller,
          enabled: enabled,
          obscureText: obscureText,
          keyboardType: keyboardType,
          textInputAction: textInputAction,
          validator: validator,
          onChanged: onChanged,
          focusNode: focusNode,
          autofocus: autofocus,
          maxLines: maxLines,
          style: TextStyle(
            color: colors.foreground,
            fontSize: 15,
            fontWeight: FontWeight.w500,
          ),
          decoration: InputDecoration(
            hintText: hintText,
            hintStyle: TextStyle(
              color: colors.foregroundSecondary,
              fontSize: 14.5,
              fontWeight: FontWeight.w400,
            ),
            filled: true,
            fillColor: fill,
            contentPadding: const EdgeInsets.symmetric(
              horizontal: ChatySpacing.base,
              vertical: 14,
            ),
            prefixIcon: prefixIcon,
            suffixIcon: suffixIcon,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(ChatyRadius.lg),
              borderSide: BorderSide(color: border, width: 1.0),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(ChatyRadius.lg),
              borderSide: BorderSide(color: border, width: 1.0),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(ChatyRadius.lg),
              borderSide: BorderSide(color: colors.primary, width: 1.6),
            ),
            errorBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(ChatyRadius.lg),
              borderSide: BorderSide(color: colors.error, width: 1.2),
            ),
          ),
        ),
      ],
    );
  }
}

/// ---------------------------------------------------------------------------
/// CHATY SKELETON (Reduced-motion-aware loading placeholder)
/// ---------------------------------------------------------------------------
/// Structural placeholder shown while content loads, so screens read as
/// "instant" instead of "stuck". Pulses gently; renders as static tinted
/// blocks when the OS reports reduced animations.
class ChatySkeleton extends StatefulWidget {
  final double width;
  final double height;
  final BorderRadius borderRadius;

  const ChatySkeleton({
    super.key,
    this.width = double.infinity,
    required this.height,
    this.borderRadius = const BorderRadius.all(Radius.circular(8)),
  });

  @override
  State<ChatySkeleton> createState() => _ChatySkeletonState();
}

class _ChatySkeletonState extends State<ChatySkeleton>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pulse;

  @override
  void initState() {
    super.initState();
    _pulse = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1100),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final base = colors.surfaceElevated;
    final highlight = colors.borderSubtle;
    final reduceMotion = MediaQuery.disableAnimationsOf(context);

    Widget block() => Container(
      width: widget.width,
      height: widget.height,
      decoration: BoxDecoration(
        color: base,
        borderRadius: widget.borderRadius,
        border: Border.all(color: highlight, width: 0.5),
      ),
    );

    if (reduceMotion) return block();

    return AnimatedBuilder(
      animation: _pulse,
      builder: (context, _) {
        final t = Curves.easeInOut.transform(_pulse.value);
        return Container(
          width: widget.width,
          height: widget.height,
          decoration: BoxDecoration(
            color: Color.lerp(base, highlight, t * .6),
            borderRadius: widget.borderRadius,
          ),
        );
      },
    );
  }
}

/// Ready-made skeleton row matching conversation/list-tile geometry.
class ChatySkeletonTile extends StatelessWidget {
  final bool showTrailing;

  const ChatySkeletonTile({super.key, this.showTrailing = true});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Row(
        children: [
          const ChatySkeleton(width: 48, height: 48),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ChatySkeleton(
                  width: MediaQuery.sizeOf(context).width * .42,
                  height: 13,
                ),
                const SizedBox(height: 8),
                const ChatySkeleton(width: 180, height: 11),
              ],
            ),
          ),
          if (showTrailing) ...[
            const SizedBox(width: 10),
            const ChatySkeleton(width: 34, height: 10),
          ],
        ],
      ),
    );
  }
}
