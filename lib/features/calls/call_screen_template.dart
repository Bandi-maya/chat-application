/// Chaty-designed call screen layouts for incoming and ongoing calls.
///
/// These are original Chaty designs. GB WhatsApp customizes call screen
/// colors (ModCallsBackground / ModCallsTextColor / ModCallsIconColors,
/// already applied on top of every template) but ships no selectable screen
/// templates, so no GB parity is claimed for these layouts.
enum CallScreenTemplate {
  /// Chaty's original layout: centered identity, gradient action panels.
  chatyClassic('Chaty Classic', 'Centered avatar with floating action buttons'),

  /// Bottom-sheet layout: identity and actions grouped in a rounded sheet.
  sheet('Sheet', 'Rounded bottom sheet carrying the call actions'),

  /// Floating card layout: the whole call UI sits in one centered card.
  card('Card', 'A single elevated card with all call controls'),

  /// Edge layout: full-bleed background, controls pinned to screen edges.
  edge('Edge', 'Minimal chrome, controls pinned to the screen edges');

  const CallScreenTemplate(this.displayName, this.description);

  final String displayName;
  final String description;

  static const List<CallScreenTemplate> all = CallScreenTemplate.values;

  static CallScreenTemplate fromName(String? name) {
    for (final template in CallScreenTemplate.values) {
      if (template.name == name) return template;
    }
    return CallScreenTemplate.chatyClassic;
  }
}
