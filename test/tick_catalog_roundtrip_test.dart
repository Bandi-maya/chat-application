import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/ticks/delivery_icon_style.dart';

void main() {
  test('every tick display name round-trips and names are unique', () {
    final names = <String>{};
    for (final s in DeliveryIconStyle.values) {
      expect(DeliveryIconStyleExtension.fromString(s.displayName), s,
          reason: s.displayName);
      names.add(s.displayName);
    }
    expect(names.length, DeliveryIconStyle.values.length);
  });

  test('Stock is the default for unset values; saved choices are kept', () {
    expect(DeliveryIconStyleExtension.fromString(null), DeliveryIconStyle.stock);
    expect(DeliveryIconStyleExtension.fromString('Stock'), DeliveryIconStyle.stock);
    expect(DeliveryIconStyleExtension.fromString('RC iOS 11'), DeliveryIconStyle.rcIos11);
    expect(DeliveryIconStyleExtension.fromString('iOS Ticks'), DeliveryIconStyle.ios2);
  });
}
