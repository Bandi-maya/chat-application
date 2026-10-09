/// Canonical labels and compatibility mapping for the Chaty home layout selector.
///
/// Values stored by older Chaty builds are accepted, but every visible option
/// resolves to a layout that the real ChatsHomeScreen can render.
class ChatyHomeStyleCatalog {
  const ChatyHomeStyleCatalog._();

  static const String defaultStyle = 'Chaty Default';
  static const String oneUi = 'ONE UI';
  static const String telegram = 'TELEGRAM STYLE';
  static const String ios = 'IOS STYLE';
  static const String oldUi = 'WHATSAPP OLD UI';

  /// User-facing labels. Keep these human-readable; do not expose storage keys.
  static const List<String> options = <String>[
    defaultStyle,
    'One UI',
    'Telegram',
    'iOS',
    'Classic',
    'Minimal',
    'Compact',
    'Stories First',
    'Expressive',
    'Tablet Split View',
    'Productivity',
    'WhatsApp Old UI',
  ];

  /// Convert stored legacy names into the label shown in the picker.
  static String displayName(String storedValue) {
    switch (storedValue.trim().toLowerCase()) {
      case 'one ui':
        return 'One UI';
      case 'telegram':
      case 'telegram style':
        return 'Telegram';
      case 'ios':
      case 'ios style':
        return 'iOS';
      case 'bubbles tab style':
        return 'Expressive';
      case 'basic tab style':
        return 'Minimal';
      case 'whatsapp old ui':
        return 'WhatsApp Old UI';
      case 'whatsapp ui stock':
        return 'Classic';
      case 'chaty default':
      case '':
        return defaultStyle;
      default:
        return storedValue;
    }
  }

  /// Convert a visible option back to its canonical persisted value.
  static String storedValue(String displayValue) {
    switch (displayValue.trim().toLowerCase()) {
      case 'one ui':
        return oneUi;
      case 'telegram':
      case 'telegram style':
        return telegram;
      case 'ios':
      case 'ios style':
        return ios;
      case 'bubbles tab style':
        return 'BUBBLES TAB STYLE';
      case 'basic tab style':
        return 'BASIC TAB STYLE';
      case 'whatsapp old ui':
        return 'WhatsApp OLD UI';
      case 'whatsapp ui stock':
        return 'WhatsApp UI Stock';
      case 'chaty default':
      case '':
        return defaultStyle;
      default:
        return displayValue;
    }
  }

  /// Resolve legacy names to structural presets used by the live home screen.
  static String structuralStyle(String storedValue) {
    switch (storedValue.trim().toLowerCase()) {
      case 'one ui':
        return oneUi;
      case 'telegram':
      case 'telegram style':
        return telegram;
      case 'ios':
      case 'ios style':
        return ios;
      case 'whatsapp ui stock':
        return 'Classic';
      case 'bubbles tab style':
        return 'Expressive';
      case 'basic tab style':
        return 'Minimal';
      case 'whatsapp old ui':
        return oldUi;
      case '':
      case 'chaty default':
        return defaultStyle;
      default:
        return storedValue;
    }
  }
}
