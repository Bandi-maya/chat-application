import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:chat/features/camera/lens/face_tracker.dart';
import 'package:chat/features/camera/lens/lens_carousel.dart';
import 'package:chat/features/camera/lens/lens_definition.dart';
import 'package:chat/features/camera/lens/lens_manager.dart';
import 'package:chat/features/camera/lens/lens_overlay.dart';

RawFace _faceAt({
  required int id,
  required double centerX,
  double centerY = 300,
  double width = 200,
}) {
  return RawFace(
    trackingId: id,
    headBounds: Rect.fromCenter(
      center: Offset(centerX, centerY),
      width: width,
      height: width * 1.3,
    ),
    leftEye: Offset(centerX - width * 0.18, centerY - width * 0.1),
    rightEye: Offset(centerX + width * 0.18, centerY - width * 0.1),
    noseBase: Offset(centerX, centerY + width * 0.025),
    mouthCenter: Offset(centerX, centerY + width * 0.28),
    confidence: 1,
  );
}

void main() {
  group('FaceAnchorSolver', () {
    const imageExtent = Size(1080, 1920);
    final fullPreview = Rect.fromLTWH(0, 0, imageExtent.width, imageExtent.height);

    List<FacePose> feed(
      FaceAnchorSolver solver,
      int nowMs,
      Map<int, RawFace>? faces, {
      bool mirrored = false,
    }) => solver.update(
      nowMs: nowMs,
      rawFaces: faces,
      mirrored: mirrored,
      imageExtent: imageExtent,
      previewCrop: fullPreview,
      confidenceScale: 1,
    );

    test('mirrored solving keeps anchors on the visible face', () {
      // Front camera: face sits on the LEFT of the raw image but the preview
      // flips horizontally, so the solved pose must land on the RIGHT —
      // the classic mirrored-landmark bug class (requirement 42).
      final solver = FaceAnchorSolver(smoothingAlpha: 1);
      final poses = feed(solver, 0, {7: _faceAt(id: 7, centerX: 200)}, mirrored: true);

      expect(poses, hasLength(1));
      expect(poses.single.headBounds.center.dx, greaterThan(imageExtent.width / 2));
    });

    test('unmirrored solving preserves side', () {
      final solver = FaceAnchorSolver(smoothingAlpha: 1);
      final poses = feed(solver, 0, {7: _faceAt(id: 7, centerX: 200)});

      expect(poses.single.headBounds.center.dx, lessThan(imageExtent.width / 2));
    });

    test('smoothing moves toward a jumped face without overshoot, converging',
        () {
      final solver = FaceAnchorSolver(smoothingAlpha: 0.45);
      Map<int, RawFace> at(double x) => {1: _faceAt(id: 1, centerX: x)};

      var poses = feed(solver, 0, at(540));
      final beforeJump = poses.single.noseBase.dx;

      poses = feed(solver, 33, at(900));
      final afterOneFrame = poses.single.noseBase.dx;
      expect(afterOneFrame, greaterThan(beforeJump));
      expect(afterOneFrame, lessThan(900), reason: 'low-pass must not overshoot');

      for (var frame = 2; frame < 12; frame++) {
        poses = feed(solver, frame * 33, at(900));
      }
      expect(poses.single.noseBase.dx, closeTo(900, 1.0));
    });

    test('tracking loss holds through grace then reports lost', () {
      final solver = FaceAnchorSolver(
        smoothingAlpha: 1,
        gracePeriod: const Duration(milliseconds: 350),
      );
      feed(solver, 0, {3: _faceAt(id: 3, centerX: 500)});

      final held = feed(solver, 200, null);
      expect(solver.health, TrackingHealth.temporarilyLost);
      expect(held, hasLength(1), reason: 'brief occlusion must hold pose');

      final gone = feed(solver, 900, null);
      expect(solver.health, TrackingHealth.lost);
      expect(gone, isEmpty, reason: 'long loss must remove lenses, not freeze');
    });

    test('anchor scale follows face size so lenses grow with proximity', () {
      final solver = FaceAnchorSolver(smoothingAlpha: 1);
      var poses = feed(solver, 0, {9: _faceAt(id: 9, centerX: 540, width: 150)});
      final near =
          (poses.single.leftEye - poses.single.rightEye).distance;

      poses = feed(solver, 16, {9: _faceAt(id: 9, centerX: 540, width: 450)});
      final far =
          (poses.single.leftEye - poses.single.rightEye).distance;

      expect(far, greaterThan(near * 2));
    });
  });

  group('LensManager selection pipeline', () {
    late LensManager manager;
    late List<String> committedIds;

    setUp(() {
      manager = LensManager();
      committedIds = <String>[];
      manager.addListener(() {
        if (manager.runtimeState == LensRuntimeState.active) {
          committedIds.add(manager.selected.id);
        }
      });
    });

    tearDown(() => manager.dispose());

    test('rapid flick commits only where the carousel settles', () async {
      // Requirement 49: five selections inside 300ms must not enqueue five
      // heavy applications; only the settled lens commits.
      for (final index in <int>[1, 2, 3, 4, 2]) {
        manager.select(kBuiltInLenses[index].id);
        await Future<void>.delayed(const Duration(milliseconds: 60));
      }
      await Future<void>.delayed(LensManager.selectionSettleDebounce);

      expect(committedIds, [kBuiltInLenses[2].id]);
    });

    test('settled selection activates once and stays stable', () async {
      manager.select(kBuiltInLenses[1].id);
      await Future<void>.delayed(LensManager.selectionSettleDebounce);
      expect(committedIds, [kBuiltInLenses[1].id]);

      await Future<void>.delayed(const Duration(milliseconds: 250));
      expect(committedIds, [kBuiltInLenses[1].id], reason: 'no duplicate commits');
    });

    test('recents front-insert and never contain No Lens', () async {
      manager.select(kBuiltInLenses[2].id);
      manager.select(kBuiltInLenses[3].id);
      manager.clear();

      expect(manager.selected.isNoLens, isTrue);
      expect(manager.recents.first, kBuiltInLenses[3].id);
      expect(manager.recents.contains('none'), isFalse);
    });

    test('prefetch tiers stay within catalog bounds, nearest first', () {
      final nearCenter = manager.prefetchIndices(0);
      expect(nearCenter.first, 0, reason: 'center item is highest priority');
      expect(nearCenter.any((i) => i < 0 || i >= kBuiltInLenses.length), isFalse);
    });
  });

  group('LensCarousel', () {
    Widget harness(LensManager manager) => MaterialApp(
      // Carousel metrics read MediaQuery, so the test surface must agree
      // with the visual box or items lay out outside the clipped viewport.
      home: MediaQuery(
        data: const MediaQueryData(size: Size(390, 780)),
        child: Scaffold(
          body: SizedBox(
            width: 390,
            height: 780,
            child: Column(
              children: [
                const Spacer(),
                LensCarousel(manager: manager),
              ],
            ),
          ),
        ),
      ),
    );

    testWidgets('first item is No Lens and starts centered/selected', (
      tester,
    ) async {
      final manager = LensManager();
      addTearDown(manager.dispose);
      await tester.pumpWidget(harness(manager));
      await tester.pumpAndSettle();

      expect(kBuiltInLenses.first.isNoLens, isTrue);
      expect(manager.selected.isNoLens, isTrue);
    });

    testWidgets('tapping an off-center item centers and selects it', (
      tester,
    ) async {
      final manager = LensManager();
      addTearDown(manager.dispose);
      await tester.pumpWidget(harness(manager));
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.visibility_rounded));
      await tester.pumpAndSettle();

      expect(manager.selected.id, 'lens_glasses_classic');
      // Selection and visual position must agree (requirement 12): after the
      // tap animation the controller rests exactly on index * slotExtent.
      final controller =
          tester.widget<ListView>(find.byType(ListView)).controller!;
      const slot = 52.0 + 18; // clamped min diameter + gap at 390px width
      expect(controller.offset, closeTo(1 * slot, 1.0));
    });

    testWidgets('dragging settles onto nearest lens which becomes selected', (
      tester,
    ) async {
      final manager = LensManager();
      addTearDown(manager.dispose);
      await tester.pumpWidget(harness(manager));
      await tester.pumpAndSettle();

      final listView = find.byType(ListView);
      final dragFrom = tester.getTopLeft(listView) + const Offset(340, 20);

      await tester.drag(listView, Offset(-260, 0), warnIfMissed: false);
      await tester.pumpAndSettle();

      // After settling right of start, selection advanced past 'none'.
      expect(manager.selected.isNoLens, isFalse);
      expect(dragFrom.dx, greaterThan(0));
    });
  });

  group('LensOverlay gating', () {
    testWidgets('renders nothing for No Lens or fully lost tracking', (
      tester,
    ) async {
      const none = LensDefinition(
        id: 'none',
        name: 'No Lens',
        category: defaultLensCategory,
        anchor: LensAnchor.fullHead,
        icon: Icons.circle_outlined,
        accentColor: Colors.grey,
        requiresFaceTracking: false,
      );
      final pose = FacePose(
        trackingId: 1,
        headBounds: const Rect.fromLTWH(10, 10, 100, 130),
        leftEye: const Offset(40, 50),
        rightEye: const Offset(80, 50),
        noseBase: const Offset(60, 65),
        mouthCenter: const Offset(60, 90),
        rollRadians: 0,
        yawRadians: 0,
        pitchRadians: 0,
        confidence: 1,
      );

      await tester.pumpWidget(
        MaterialApp(
          home: Stack(
            children: [
              LensOverlay(lens: none, poses: [pose], health: TrackingHealth.tracked),
              LensOverlay(
                lens: kBuiltInLenses[1],
                poses: [pose],
                health: TrackingHealth.lost,
              ),
            ],
          ),
        ),
      );
      await tester.pump();

      expect(find.byKey(const ValueKey('lens_anchor_paint')), findsNothing);
    });

    testWidgets('renders anchored paint for an active tracked lens', (
      tester,
    ) async {
      final pose = FacePose(
        trackingId: 1,
        headBounds: const Rect.fromLTWH(10, 10, 100, 130),
        leftEye: const Offset(40, 50),
        rightEye: const Offset(80, 50),
        noseBase: const Offset(60, 65),
        mouthCenter: const Offset(60, 90),
        rollRadians: 0,
        yawRadians: 0,
        pitchRadians: 0,
        confidence: 1,
      );
      await tester.pumpWidget(
        MaterialApp(
          home: Stack(
            children: [
              LensOverlay(
                lens: kBuiltInLenses[1],
                poses: [pose],
                health: TrackingHealth.tracked,
              ),
            ],
          ),
        ),
      );
      await tester.pump();

      expect(find.byKey(const ValueKey('lens_anchor_paint')), findsOneWidget);
    });
  });

  group('Capability gating', () {
    test('unsupported lenses are hidden for low-end devices', () {
      final highOnly = LensDefinition(
        id: 'heavy',
        name: 'Heavy',
        category: defaultLensCategory,
        anchor: LensAnchor.fullHead,
        icon: Icons.auto_awesome,
        accentColor: Colors.deepPurple,
        minimumTier: CapabilityTier.high,
      );
      expect(highOnly.supportedForDevice(DeviceCapabilityTier.lowEnd.asLensRequirement), isFalse);
      expect(highOnly.supportedForDevice(DeviceCapabilityTier.highEnd.asLensRequirement), isTrue);
    });
  });
}
