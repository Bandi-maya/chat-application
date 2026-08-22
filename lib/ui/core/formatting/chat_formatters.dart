/// Shared conversation timestamp / presence formatting.
///
/// One implementation serves the chat list, the chat header, profile and
/// presence surfaces so date rules never diverge between widgets.
/// Timestamps are converted to LOCAL time only at this display boundary —
/// the storage contract stays UTC (see backend `_date()` parsing).
library;

/// Weekday names are produced through Dart's built-in `DateFormat`-free
/// path so no intl dependency is required here; English short weekday names
/// match the app's existing UI copy.
const List<String> _weekdays = [
  'Monday',
  'Tuesday',
  'Wednesday',
  'Thursday',
  'Friday',
  'Saturday',
  'Sunday',
];

String _two(int v) => v.toString().padLeft(2, '0');

/// Locale-aware clock time ("9:52 AM" or "21:52") following the system
/// 12/24-hour preference via [DateTime] hour + dayPeriod convention used
/// across Chaty (always en-US labels today; swap to MaterialLocalizations
/// when full l10n lands).
String formatClockTime(DateTime value) {
  final local = value.toLocal();
  final hour12 = local.hour % 12 == 0 ? 12 : local.hour % 12;
  final amPm = local.hour < 12 ? 'AM' : 'PM';
  return '$hour12:${_two(local.minute)} $amPm';
}

bool _sameCalendarDay(DateTime a, DateTime b) =>
    a.year == b.year && a.month == b.month && a.day == b.day;

/// Chat-list / metadata timestamp:
/// today → "9:52 AM" · yesterday → "Yesterday"
/// within the last 7 days → "Wednesday" · older → "19/08/2026".
String formatConversationTimestamp(DateTime timestamp, {DateTime? now}) {
  final local = timestamp.toLocal();
  final ref = (now ?? DateTime.now()).toLocal();
  if (_sameCalendarDay(local, ref)) return formatClockTime(local);

  final yesterday = ref.subtract(const Duration(days: 1));
  if (_sameCalendarDay(local, yesterday)) return 'Yesterday';

  final diffDays = DateTime(ref.year, ref.month, ref.day)
      .difference(DateTime(local.year, local.month, local.day))
      .inDays;
  if (diffDays > 0 && diffDays < 7) {
    // DateTime.weekday: Mon=1 … Sun=7.
    return _weekdays[local.weekday - 1];
  }
  return '${_two(local.day)}/${_two(local.month)}/${local.year}';
}

/// Presence line for a contact's last-seen instant.
///
/// online handled by callers that have live presence; null/hidden input
/// yields an empty string so privacy rules stay in the caller's control.
/// today → "last seen today at 9:52 AM"
/// yesterday → "last seen yesterday at 8:16 PM"
/// older → "last seen 19/08/2026 at 6:45 PM"
String formatLastSeen(DateTime? lastSeen, {DateTime? now}) {
  if (lastSeen == null) return '';
  final local = lastSeen.toLocal();
  final ref = (now ?? DateTime.now()).toLocal();
  if (_sameCalendarDay(local, ref)) {
    return 'last seen today at ${formatClockTime(local)}';
  }
  final yesterday = ref.subtract(const Duration(days: 1));
  if (_sameCalendarDay(local, yesterday)) {
    return 'last seen yesterday at ${formatClockTime(local)}';
  }
  return 'last seen '
      '${_two(local.day)}/${_two(local.month)}/${local.year} '
      'at ${formatClockTime(local)}';
}
