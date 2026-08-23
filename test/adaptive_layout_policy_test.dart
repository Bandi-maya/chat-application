import 'package:flutter_test/flutter_test.dart';
import 'package:chat/ui/core/design_system/chaty_adaptive.dart';

void main() {
  test('window classes follow Chaty adaptive breakpoints', () {
    expect(ChatyAdaptive.windowClassForWidth(359), ChatyWindowClass.compact);
    expect(ChatyAdaptive.windowClassForWidth(600), ChatyWindowClass.medium);
    expect(ChatyAdaptive.windowClassForWidth(840), ChatyWindowClass.expanded);
    expect(ChatyAdaptive.windowClassForWidth(1200), ChatyWindowClass.large);
  });

  test('pane policy promotes wide layouts to dual-pane', () {
    expect(ChatyAdaptive.paneModeForWidth(480), ChatyPaneMode.single);
    expect(ChatyAdaptive.paneModeForWidth(700), ChatyPaneMode.supporting);
    expect(ChatyAdaptive.paneModeForWidth(900), ChatyPaneMode.dual);
    expect(ChatyAdaptive.paneModeForWidth(1400), ChatyPaneMode.dual);
  });
}
