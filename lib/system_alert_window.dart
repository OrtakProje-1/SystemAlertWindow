import 'dart:async';
import 'package:flutter/services.dart';
import 'package:system_alert_window/utils/commons.dart';
import 'package:system_alert_window/utils/constants.dart';

enum SystemWindowGravity { TOP, BOTTOM, CENTER }

enum SystemWindowPrefMode { DEFAULT, OVERLAY, BUBBLE }

class SystemAlertWindow {
  static const MethodChannel _channel =
      const MethodChannel(Constants.CHANNEL, JSONMethodCodec());

  static Future<int?> get platformVersionInt async {
    final int? version =
        await _channel.invokeMethod<int>('getPlatformVersionInt');
    return version;
  }

  static Future<String?> get platformVersion async {
    final String? version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<bool?> checkPermissions(
      {SystemWindowPrefMode prefMode = SystemWindowPrefMode.DEFAULT}) async {
    return await _channel.invokeMethod(
        'checkPermissions', [Commons.getSystemWindowPrefMode(prefMode)]);
  }

  static Future<bool?> requestPermissions(
      {SystemWindowPrefMode prefMode = SystemWindowPrefMode.DEFAULT}) async {
    return await _channel.invokeMethod(
        'requestPermissions', [Commons.getSystemWindowPrefMode(prefMode)]);
  }

  static Future<bool?> showSystemWindow({
    required String imagePath,
    SystemWindowGravity gravity = SystemWindowGravity.CENTER,
    int? width,
    int? height,
    int? offsetX,
    int? offsetY,
    String notificationTitle = "Title",
    String notificationBody = "Body",
    SystemWindowPrefMode prefMode = SystemWindowPrefMode.DEFAULT,
  }) async {
    final Map<String, dynamic> params = <String, dynamic>{
      'gravity': Commons.getWindowGravity(gravity),
      'width': width ?? Constants.MATCH_PARENT,
      'height': height ?? Constants.WRAP_CONTENT,
      'imagePath': imagePath,
      'offsetX': offsetX,
      'offsetY': offsetY
    };
    return await _channel.invokeMethod('showSystemWindow', [
      notificationTitle,
      notificationBody,
      params,
      Commons.getSystemWindowPrefMode(prefMode)
    ]);
  }

  static Future<bool?> updateSystemWindow({
    required String imagePath,
    SystemWindowGravity gravity = SystemWindowGravity.CENTER,
    int? width,
    int? height,
    int? offsetX,
    int? offsetY,
    String notificationTitle = "Title",
    String notificationBody = "Body",
    SystemWindowPrefMode prefMode = SystemWindowPrefMode.DEFAULT,
  }) async {
    final Map<String, dynamic> params = <String, dynamic>{
      'gravity': Commons.getWindowGravity(gravity),
      'width': width ?? Constants.MATCH_PARENT,
      'height': height ?? Constants.WRAP_CONTENT,
      'imagePath': imagePath,
      'offsetX': offsetX,
      'offsetY': offsetY
    };
    return await _channel.invokeMethod('updateSystemWindow', [
      notificationTitle,
      notificationBody,
      params,
      Commons.getSystemWindowPrefMode(prefMode)
    ]);
  }

  static Future<bool?> closeSystemWindow(
      {SystemWindowPrefMode prefMode = SystemWindowPrefMode.DEFAULT}) async {
    return await _channel.invokeMethod(
        'closeSystemWindow', [Commons.getSystemWindowPrefMode(prefMode)]);
  }
}
