import 'package:flutter/material.dart';

/// Real consumer for the Appearance screen's "Entry animation" and
/// "Exit animation" settings (`AppearanceVariantController.entryAnimation` /
/// `.exitAnimation`).
///
/// [ChatyTransitions.build] produces a [PageTransitionsTheme] that is merged
/// into MaterialApp's theme in `main.dart`, so EVERY MaterialPageRoute in the
/// app opens with the configured entry style, while the covered screen
/// recedes with the configured exit style (driven by the route's
/// secondaryAnimation — exactly how native platform transitions work).
class ChatyTransitions {
  /// Builds the app-wide page-transitions theme from the two setting values.
  static PageTransitionsTheme build({
    required String entry,
    required String exit,
  }) {
    return PageTransitionsTheme(
      builders: <TargetPlatform, PageTransitionsBuilder>{
        for (final TargetPlatform platform in TargetPlatform.values)
          platform: ChatyPageTransitionsBuilder(entry: entry, exit: exit),
      },
    );
  }
}

/// A [PageTransitionsBuilder] driven entirely by the two persisted style
/// names. The entry spec animates the incoming route's primary animation;
/// the exit spec animates the covered route's secondaryAnimation.
class ChatyPageTransitionsBuilder extends PageTransitionsBuilder {
  final String entry;
  final String exit;

  const ChatyPageTransitionsBuilder({
    required this.entry,
    required this.exit,
  });

  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    final Widget entered = _TransitionMotion(
      animation: animation,
      spec: _specFor(entry, isEntry: true),
      asEntry: true,
      child: child,
    );
    // secondaryAnimation runs 0->1 while THIS route is being covered by a
    // new one, which is precisely the "exit" moment for the covered page.
    return _TransitionMotion(
      animation: secondaryAnimation,
      spec: _specFor(exit, isEntry: false),
      asEntry: false,
      child: entered,
    );
  }

  static _TransitionSpec _none() =>
      const _TransitionSpec(shift: Offset.zero, scale: 1.0, fade: false);

  /// Maps each option name from AppearanceVariantController to a concrete
  /// motion. Naming convention for slides: "Slide X" = the page arrives
  /// FROM direction X when entering / retreats TOWARD direction X on exit.
  static _TransitionSpec _specFor(String style, {required bool isEntry}) {
    switch (style) {
      case 'Fade':
        return const _TransitionSpec(
          shift: Offset.zero,
          scale: 1.0,
          fade: true,
        );
      case 'Fade + Slide':
        return _TransitionSpec(
          shift: isEntry ? const Offset(0, 0.05) : const Offset(0, -0.04),
          scale: 1.0,
          fade: true,
        );
      case 'Slide Right':
        return const _TransitionSpec(
          shift: Offset(1, 0),
          scale: 1.0,
          fade: false,
          curve: Curves.easeOutCubic,
        );
      case 'Slide Left':
        return const _TransitionSpec(
          shift: Offset(-1, 0),
          scale: 1.0,
          fade: false,
          curve: Curves.easeOutCubic,
        );
      case 'Slide Up':
        return const _TransitionSpec(
          shift: Offset(0, 1),
          scale: 1.0,
          fade: false,
          curve: Curves.easeOutCubic,
        );
      case 'Slide Down':
        return const _TransitionSpec(
          shift: Offset(0, -1),
          scale: 1.0,
          fade: false,
          curve: Curves.easeOutCubic,
        );
      case 'Scale In':
      case 'Scale Out':
        return const _TransitionSpec(
          shift: Offset.zero,
          scale: 0.88,
          fade: true,
        );
      case 'Soft Zoom':
        return const _TransitionSpec(
          shift: Offset.zero,
          scale: 1.08,
          fade: true,
          curve: Curves.easeOut,
        );
      case 'Shared Axis X':
        return _TransitionSpec(
          shift: Offset(isEntry ? 0.16 : -0.16, 0),
          scale: 1.0,
          fade: true,
        );
      case 'Shared Axis Y':
        return _TransitionSpec(
          shift: Offset(0, isEntry ? 0.16 : -0.16),
          scale: 1.0,
          fade: true,
        );
      case 'Fade Through':
        return const _TransitionSpec(
          shift: Offset.zero,
          scale: 0.985,
          fade: true,
          curve: Curves.fastOutSlowIn,
        );
      case 'Cupertino Push':
      case 'Cupertino Pop':
        return const _TransitionSpec(
          shift: Offset(1, 0),
          scale: 1.0,
          fade: false,
          curve: Curves.linearToEaseOut,
        );
      case 'Spring Push':
      case 'Spring Pop':
        return const _TransitionSpec(
          shift: Offset(0.3, 0),
          scale: 1.0,
          fade: false,
          curve: Curves.easeOutBack,
        );
      case 'Soft Reveal':
      case 'Soft Conceal':
        return const _TransitionSpec(
          shift: Offset.zero,
          scale: 1.05,
          fade: true,
        );
      case 'Card Lift':
      case 'Card Drop':
        return _TransitionSpec(
          shift: const Offset(0, 0.06),
          scale: 0.96,
          fade: true,
          curve: isEntry ? Curves.easeOutCubic : Curves.easeInCubic,
        );
      case 'Blur-free Reveal':
        return const _TransitionSpec(
          shift: Offset.zero,
          scale: 1.10,
          fade: false,
          curve: Curves.easeOutQuad,
        );
      case 'Quick Snap':
        return _TransitionSpec(
          shift: const Offset(0, 0.03),
          scale: 1.02,
          fade: true,
          curve: isEntry ? Curves.easeOut : Curves.easeIn,
        );
      case 'Gentle Drift':
        return _TransitionSpec(
          shift: Offset(isEntry ? 0.05 : -0.05, 0.02),
          scale: 1.0,
          fade: true,
          curve: Curves.slowMiddle,
        );
      case 'Focus In':
      case 'Focus Out':
        return const _TransitionSpec(
          shift: Offset.zero,
          scale: 0.92,
          fade: true,
          curve: Curves.easeInOutCubicEmphasized,
        );
      case 'Cross Fade':
        return const _TransitionSpec(
          shift: Offset.zero,
          scale: 1.0,
          fade: true,
          curve: Curves.easeInOut,
        );
      case 'None':
      default:
        return _none();
    }
  }
}

/// Motion parameters shared by entry and exit styles.
///
/// [scale] is the "absent" scale: entering pages grow from it to 1.0 and
/// exiting pages shrink toward it. [shift] is a fractional offset applied
/// the same way. [fade] toggles opacity animation.
class _TransitionSpec {
  final Offset shift;
  final double scale;
  final bool fade;
  final Curve curve;

  const _TransitionSpec({
    this.shift = Offset.zero,
    this.scale = 1.0,
    this.fade = true,
    this.curve = Curves.easeOutCubic,
  });
}

/// Applies a [_TransitionSpec] to an [animation].
///
/// For entries (`asEntry == true`) progress p goes 0->1 as the route opens.
/// For exits p goes 1->0 as the covering route takes over, producing the
/// mirror-image motion with the same code path.
class _TransitionMotion extends StatelessWidget {
  final Animation<double> animation;
  final _TransitionSpec spec;
  final bool asEntry;
  final Widget child;

  const _TransitionMotion({
    required this.animation,
    required this.spec,
    required this.asEntry,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    if (spec.shift == Offset.zero && spec.scale == 1.0 && !spec.fade) {
      // Pure no-op styles ('None') must not wrap the child at all so
      // hit-testing and layout stay untouched.
      return child;
    }
    final curved = CurvedAnimation(parent: animation, curve: spec.curve);
    return AnimatedBuilder(
      animation: curved,
      builder: (context, _) {
        final double presence =
            (asEntry ? curved.value : 1 - curved.value).clamp(0.0, 1.0);
        final double absence = 1 - presence;
        Widget result = FractionalTranslation(
          translation: Offset(spec.shift.dx * absence, spec.shift.dy * absence),
          child: child,
        );
        if (spec.scale != 1.0) {
          result = Transform.scale(scale: 1 + (spec.scale - 1) * absence, child: result);
        }
        if (spec.fade) {
          result = Opacity(opacity: presence, child: result);
        }
        return result;
      },
    );
  }
}
