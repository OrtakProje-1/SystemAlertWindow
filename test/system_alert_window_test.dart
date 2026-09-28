import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:system_alert_window/system_alert_window.dart';

void main() {
  const MethodChannel channel =
      MethodChannel('in.jvapps.system_alert_window', JSONMethodCodec());

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        if (methodCall.method == 'getPlatformVersion') {
          return '42';
        }
        return null;
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('getPlatformVersion', () async {
    expect(await SystemAlertWindow.platformVersion, '42');
  });
  test('drag region defaults on show and is optional on update', () async {
    final calls = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return true;
    });

    await SystemAlertWindow.showSystemWindow();
    expect((calls.last.arguments as List)[2]['dragRegionHeightFraction'], 1.0);

    await SystemAlertWindow.updateSystemWindow();
    expect(
        (calls.last.arguments as List)[2]
            .containsKey('dragRegionHeightFraction'),
        isFalse);

    await SystemAlertWindow.updateSystemWindow(dragRegionHeightFraction: .25);
    expect((calls.last.arguments as List)[2]['dragRegionHeightFraction'], .25);
  });

  test('drag region rejects values outside the window', () async {
    expect(
        () =>
            SystemAlertWindow.showSystemWindow(dragRegionHeightFraction: -0.1),
        throwsRangeError);
    expect(
        () =>
            SystemAlertWindow.updateSystemWindow(dragRegionHeightFraction: 1.1),
        throwsRangeError);
  });
}
