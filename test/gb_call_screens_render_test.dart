// Render + persistence harness for Chaty call screen templates
// (Settings > Calls > Call Screen Style). Pumps the real IncomingCallScreen
// and OngoingCallScreen against a demo call session, one render per
// template, proving selection -> persistence -> presentation.
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:chat/data/repositories/chaty_data_store.dart';
import 'package:chat/data/services/backend_service.dart';
import 'package:chat/data/services/call_signaling_service.dart';
import 'package:chat/domain/models/call_state.dart';
import 'package:chat/data/services/connection_health_service.dart';
import 'package:chat/data/services/notification_service.dart';
import 'package:chat/data/services/rich_chat_realtime_service.dart';
import 'package:chat/features/calls/call_screen_template.dart';
import 'package:chat/features/calls/incoming_call_screen.dart';
import 'package:chat/features/calls/ongoing_call_screen.dart';
import 'package:chat/features/settings/calls/call_presentation_preferences.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/theme/theme_config.dart';
import 'package:chat/ui/core/theme/theme_controller.dart';

Future<void> _loadFont(String family, String file) async {
  final root = Platform.environment['FLUTTER_ROOT'] ?? '';
  final f = File('$root/bin/cache/artifacts/material_fonts/$file');
  if (!f.existsSync()) return;
  final bytes = await f.readAsBytes();
  final loader = FontLoader(family)
    ..addFont(Future<ByteData>.value(ByteData.sublistView(Uint8List.fromList(bytes))));
  await loader.load();
}

ButtonStyle? _withRobotoButtonStyle(ButtonStyle? style) {
  if (style == null) return null;
  final textStyle = style.textStyle;
  if (textStyle == null) return style;
  return style.copyWith(
    textStyle: WidgetStatePropertyAll<TextStyle?>(
      textStyle.resolve(const <WidgetState>{})?.copyWith(fontFamily: 'Roboto'),
    ),
  );
}

ThemeData _renderTheme(ThemeData td) {
  return td.copyWith(
    textTheme: td.textTheme.apply(fontFamily: 'Roboto'),
    primaryTextTheme: td.primaryTextTheme.apply(fontFamily: 'Roboto'),
    chipTheme: td.chipTheme.copyWith(
      labelStyle: (td.chipTheme.labelStyle ?? const TextStyle())
          .copyWith(fontFamily: 'Roboto'),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: _withRobotoButtonStyle(td.elevatedButtonTheme.style),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: _withRobotoButtonStyle(td.outlinedButtonTheme.style),
    ),
    textButtonTheme: TextButtonThemeData(
      style: _withRobotoButtonStyle(td.textButtonTheme.style),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: _withRobotoButtonStyle(td.filledButtonTheme.style),
    ),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    for (final name in ['com.llfbandit.app_links/messages', 'com.llfbandit.app_links/events']) {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(MethodChannel(name), (call) async => null);
    }
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('flutter.baseflow.com/permissions/methods'),
      (call) async => 1,
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('chaty/call_pip'),
      (call) async => false,
    );
    // flutter_webrtc: let RTCVideoRenderer initialize/dispose without a
    // platform. Voice-call renders never show a video texture.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('FlutterWebRTC.Method'),
      (call) async {
        if (call.method == 'createVideoRenderer') {
          return <String, dynamic>{'textureId': 0};
        }
        return null;
      },
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('FlutterWebRTC/Texture0'),
      (call) async => null,
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('FlutterWebRTC.Event'),
      (call) async => null,
    );
    SharedPreferences.setMockInitialValues({});
    try {
      await Supabase.initialize(url: 'http://localhost:54321', anonKey: 'x');
    } catch (_) {}
    for (final fam in ['Roboto', 'sans-serif', 'sans-serif-medium']) {
      await _loadFont(fam, 'Roboto-Regular.ttf');
      await _loadFont(fam, 'Roboto-Medium.ttf');
    }
    await _loadFont('MaterialIcons', 'MaterialIcons-Regular.otf');
    setupLocator();
  });

  test('call screen templates persist across store instances', () async {
    SharedPreferences.setMockInitialValues({});
    final a = CallPresentationPreferencesStore();
    await a.initialize();
    await a.setIncomingCallTemplate('card');
    await a.setOutgoingCallTemplate('edge');
    final b = CallPresentationPreferencesStore();
    await b.initialize();
    expect(b.value.incomingCallTemplate, 'card');
    expect(b.value.ongoingCallTemplate, 'edge');
    expect(
      CallScreenTemplate.fromName(b.value.incomingCallTemplate),
      CallScreenTemplate.card,
    );
    expect(
      CallScreenTemplate.fromName(b.value.ongoingCallTemplate),
      CallScreenTemplate.edge,
    );
    // Unknown persisted values resolve to the default Chaty Classic layout.
    expect(
      CallScreenTemplate.fromName('not-a-template'),
      CallScreenTemplate.chatyClassic,
    );
  });

  Future<void> teardownServices(WidgetTester tester) async {
    // Resolve first: locator<T> instantiates lazy singletons, and a fresh
    // ConnectionHealthService starts probe-chain timers in its constructor.
    final services = [
      locator<ChatyDataStore>(),
      locator<ConnectionHealthService>(),
      locator<RichChatRealtimeService>(),
    ];
    // Let in-flight health-probe chain timers (2s interface timeout ->
    // 2.5s DNS timeout) finish while the services are still alive; they
    // notify listeners on completion and would fire after dispose.
    await tester.pump(const Duration(seconds: 6));
    // Cancel periodic service timers (typing expiry, connection health)
    // so the pending-timer invariant passes at test end.
    for (final t in services) {
      t.dispose();
    }
    await locator.unregister<ChatyDataStore>();
    await locator.unregister<ConnectionHealthService>();
    await locator.unregister<RichChatRealtimeService>();
    locator.registerLazySingleton<ChatyDataStore>(() => ChatyDataStore());
    locator.registerLazySingleton<ConnectionHealthService>(
      () => ConnectionHealthService(),
    );
    locator.registerLazySingleton<RichChatRealtimeService>(
      () => RichChatRealtimeService(
        preferencesController: locator<ChatyPreferencesController>(),
        notificationService: locator<ChatyNotificationService>(),
        backendService: locator<ChatyBackendService>(),
      ),
    );
    await tester.pump(const Duration(milliseconds: 200));
  }

  Future<void> snap(
    WidgetTester tester,
    GlobalKey key,
    String name,
    ThemeController theme,
    Widget screen,
  ) async {
    await tester.pumpWidget(
      RepaintBoundary(
        key: key,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: _renderTheme(theme.globalTheme.toThemeData()),
          home: screen,
        ),
      ),
    );
    await tester.pump(const Duration(milliseconds: 400));
    expect(tester.takeException(), isNull, reason: '$name must not throw');
    await tester.runAsync(() async {
      final boundary =
          key.currentContext!.findRenderObject() as RenderRepaintBoundary;
      final image = await boundary.toImage(pixelRatio: 1);
      final data = await image.toByteData(format: ui.ImageByteFormat.png);
      File('build/ui_renders/$name.png')
          .writeAsBytesSync(data!.buffer.asUint8List());
    });
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(milliseconds: 100));
  }

  testWidgets('call screen templates render', (tester) async {
    tester.view.physicalSize = const Size(800, 1700);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.reset);
    final theme = locator<ThemeController>();
    final store = CallPresentationPreferencesStore.instance;
    await store.initialize();
    final calls = locator<CallSignalingService>();
    // Initialize before starting the demo session: the service's auth
    // listener resets active calls when no user is signed in (the test
    // backend has none), and that reset fires once on first initialize.
    await calls.initialize();
    await tester.pump(const Duration(milliseconds: 200));
    await calls.startDemoCall(
      remoteUserId: 'u1',
      remoteDisplayName: 'Alex Rivera',
      remoteAvatarInitials: 'AR',
      remoteAvatarColorHex: '#6C5CE7',
    );

    // Incoming: 4 templates x light/dark. The incoming screen follows the
    // app theme; the demo session is reused across pumps.
    for (final b in [Brightness.light, Brightness.dark]) {
      theme.syncWithSystem(b);
      for (final t in CallScreenTemplate.all) {
        await store.setIncomingCallTemplate(t.name);
        await snap(
          tester,
          GlobalKey(),
          'gb_call_incoming_${t.name}_${b.name}',
          theme,
          IncomingCallScreen(theme: theme.globalTheme),
        );
      }
    }

    // Ongoing: 4 templates. The ongoing call surface is dark-first by
    // design (GB color mods apply on top), so one context is enough.
    theme.syncWithSystem(Brightness.dark);
    await calls.startDemoCall(
      remoteUserId: 'u1',
      remoteDisplayName: 'Alex Rivera',
      remoteAvatarInitials: 'AR',
      remoteAvatarColorHex: '#6C5CE7',
    );
    for (final t in CallScreenTemplate.all) {
      await store.setOutgoingCallTemplate(t.name);
      await snap(
        tester,
        GlobalKey(),
        'gb_call_ongoing_${t.name}',
        theme,
        OngoingCallScreen(theme: theme.globalTheme),
      );
    }

    // ---- Action wiring regression checks (same test to avoid the
    // cross-test service re-registration stall) ----
    Widget wrap(Widget screen) => MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: _renderTheme(theme.globalTheme.toThemeData()),
      home: screen,
    );

    Future<void> startIncomingDemo() => calls.startDemoCall(
      remoteUserId: 'u1',
      remoteDisplayName: 'Alex Rivera',
      remoteAvatarInitials: 'AR',
      remoteAvatarColorHex: '#6C5CE7',
      isOutgoing: false,
    );

    // Answer: tapping Answer connects the incoming demo call and advances
    // to the ongoing screen.
    await startIncomingDemo();
    await tester.pumpWidget(wrap(IncomingCallScreen(theme: theme.globalTheme)));
    await tester.pump(const Duration(milliseconds: 300));
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('Answer'));
    await tester.pump(const Duration(milliseconds: 300));
    expect(calls.currentSession?.state, CallSessionState.connected);
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byType(OngoingCallScreen), findsOneWidget);

    // Mute toggle.
    await tester.tap(find.bySemanticsLabel('Mute'));
    await tester.pump(const Duration(milliseconds: 200));
    expect(calls.currentSession?.isMuted, isTrue);
    await tester.tap(find.bySemanticsLabel('Unmute'));
    await tester.pump(const Duration(milliseconds: 200));
    expect(calls.currentSession?.isMuted, isFalse);

    // Speaker/earpiece routing (voice demo starts on earpiece).
    await tester.tap(find.bySemanticsLabel('Speaker'));
    await tester.pump(const Duration(milliseconds: 200));
    expect(calls.currentSession?.audioRoute, AudioRouteType.speaker);
    await tester.tap(find.bySemanticsLabel('Earpiece'));
    await tester.pump(const Duration(milliseconds: 200));
    expect(calls.currentSession?.audioRoute, AudioRouteType.earpiece);

    // Hang up.
    await tester.tap(find.bySemanticsLabel('End call'));
    await tester.pump(const Duration(milliseconds: 200));
    expect(calls.currentSession?.state, CallSessionState.ended);
    // Let the 350ms session-clear timer fire.
    await tester.pump(const Duration(milliseconds: 500));

    // Decline: a fresh incoming demo ends as declined. Remount cleanly:
    // the ongoing screen popped itself on hang-up.
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(milliseconds: 200));
    await startIncomingDemo();
    await tester.pumpWidget(wrap(IncomingCallScreen(theme: theme.globalTheme)));
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump();
    await tester.tap(find.text('Decline'));
    await tester.pump(const Duration(milliseconds: 300));
    expect(calls.currentSession?.state, CallSessionState.declined);


    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(milliseconds: 600));
    await calls.endCall();
    // endCall schedules a 350ms post-call cleanup timer; let it fire.
    await tester.pump(const Duration(milliseconds: 600));
    await teardownServices(tester);
  });
}
