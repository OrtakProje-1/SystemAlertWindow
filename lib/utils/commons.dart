import 'package:system_alert_window/system_alert_window.dart';

class Commons {
  static String getWindowGravity(SystemWindowGravity gravity) {
    switch (gravity) {
      case SystemWindowGravity.CENTER:
        return "center";
      case SystemWindowGravity.BOTTOM:
        return "bottom";
      case SystemWindowGravity.TOP:
      default:
        return "top";
    }
  }

  static String getSystemWindowPrefMode(SystemWindowPrefMode prefMode) {
    switch (prefMode) {
      case SystemWindowPrefMode.OVERLAY:
        return "overlay";
      case SystemWindowPrefMode.BUBBLE:
        return "bubble";
      case SystemWindowPrefMode.DEFAULT:
      default:
        return "default";
    }
  }
}
