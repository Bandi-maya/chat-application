# QA TEST MATRIX & VERIFICATION SCENARIOS

## 1. Test Verification Matrix

| Test ID | Subsystem / Feature | Preconditions | Test Steps | Expected Outcome | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **QA-01** | Static Analysis | Code modified | Run `flutter analyze --no-fatal-infos` | 0 errors, 0 warnings | Verified (0 issues) |
| **QA-02** | Unit & Widget Tests | Test environment | Run `flutter test` | All existing unit & widget tests pass | Queued |
| **QA-03** | Dynamic Island Overlay | Active app state | Trigger notification / active call | Overlay appears at `topPadding + 4dp`, expands smoothly, no layout stutter | Verified |
| **QA-04** | Responsive Layouts | Varying viewport | Rotate screen & test compact width (360dp) | No overflow yellow-and-black stripes, no clipped text | Verified |
| **QA-05** | Haptic Feedback | Device/emulator | Tap button with haptics enabled/disabled | Haptics trigger when enabled; silent when user disables | Verified |
| **QA-06** | Contact Picker & SIM Sheet | Contacts loaded | Open call contact picker, tap contact | Dialog displays options, search filters in $\le 16\text{ ms}$ | Verified |
| **QA-07** | Notification Settings | Settings screen | Toggle notification switches & sliders | Settings persist to `SharedPreferences` instantly | Verified |
| **QA-08** | Dark/Light Theming | Appearance screen | Switch between Light, Dark, and OLED modes | Semantic tokens update instantly without restart | Verified |
