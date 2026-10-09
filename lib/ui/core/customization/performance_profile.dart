/// Declares the estimated rendering complexity of a visual variant.
enum PerformanceCost {
  low,
  medium,
  high,
}

/// Profile specifying rendering constraints and expensive effect limits.
class PerformanceProfile {
  final PerformanceCost cost;
  final bool allowsBackdropFilter;
  final bool allowsBoxShadows;
  final bool allowsParticles;
  final bool allowsContinuousAnimation;

  const PerformanceProfile({
    this.cost = PerformanceCost.low,
    this.allowsBackdropFilter = true,
    this.allowsBoxShadows = true,
    this.allowsParticles = true,
    this.allowsContinuousAnimation = true,
  });

  static const PerformanceProfile lowCost = PerformanceProfile(
    cost: PerformanceCost.low,
    allowsBackdropFilter: false,
    allowsBoxShadows: true,
    allowsParticles: false,
    allowsContinuousAnimation: false,
  );

  static const PerformanceProfile standard = PerformanceProfile(
    cost: PerformanceCost.medium,
    allowsBackdropFilter: true,
    allowsBoxShadows: true,
    allowsParticles: true,
    allowsContinuousAnimation: false,
  );

  static const PerformanceProfile highFidelity = PerformanceProfile(
    cost: PerformanceCost.high,
    allowsBackdropFilter: true,
    allowsBoxShadows: true,
    allowsParticles: true,
    allowsContinuousAnimation: true,
  );
}
