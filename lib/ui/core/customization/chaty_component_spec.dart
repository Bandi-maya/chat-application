import 'component_capabilities.dart';
import 'performance_profile.dart';

/// All canonical customizable component families in Chaty.
enum ComponentId {
  navigation,
  header,
  composer,
  bubble,
  tick,
  chatList,
  reactionRail,
  emojiPicker,
  attachmentTray,
  modal,
  sheet,
  actionSheet,
  contextMenu,
  toast,
  emptyState,
  button,
  textField,
  avatar,
  wallpaper,
  callCapsule,
  pip,
  cameraControls,
}

/// Metadata contract describing a specific visual variant in a component family.
class ComponentSpec {
  final ComponentId componentId;
  final String variantId;
  final String displayName;
  final String description;
  final ComponentCapabilities capabilities;
  final PerformanceCost performanceCost;
  final String defaultMotionProfile;

  const ComponentSpec({
    required this.componentId,
    required this.variantId,
    required this.displayName,
    this.description = '',
    this.capabilities = ComponentCapabilities.universal,
    this.performanceCost = PerformanceCost.low,
    this.defaultMotionProfile = 'standard',
  });

  bool supports(VariantContext context) {
    if (context.isCompact && !capabilities.compactWidth) return false;
    if (context.isExpanded && !capabilities.expandedWidth) return false;
    if (context.isReducedMotion && !capabilities.reducedMotion) return false;
    if (context.isHighContrast && !capabilities.highContrast) return false;
    if (context.isRtl && !capabilities.rtl) return false;
    return true;
  }
}
