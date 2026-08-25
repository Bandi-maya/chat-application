class ChatyValidators {
  static const int minPasswordLength = 12;
  static const int maxPasswordLength = 128;

  static const Set<String> reservedUsernames = {
    'admin',
    'administrator',
    'root',
    'support',
    'security',
    'chaty',
    'official',
    'moderator',
    'system',
    'help',
    'bot',
    'service',
    'guest',
    'null',
    'undefined',
    'anonymous',
    'channel',
    'community',
    'group',
    'saved',
    'updates',
  };

  static String normalizeUsername(String username) {
    return username.trim().toLowerCase().replaceAll(RegExp(r'^@+'), '');
  }

  static String? validateUsername(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Username is required';
    }

    final normalized = normalizeUsername(value);
    if (normalized.length < 3) {
      return 'Username must be at least 3 characters';
    }
    if (normalized.length > 24) {
      return 'Username must not exceed 24 characters';
    }
    if (!RegExp(r'^[a-z0-9_]+$').hasMatch(normalized)) {
      return 'Only letters, numbers, and underscores are allowed';
    }
    if (reservedUsernames.contains(normalized)) {
      return 'This username is reserved and cannot be registered';
    }
    return null;
  }

  static String? validateEmail(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Email address is required';
    }

    final email = value.trim();
    final emailPattern = RegExp(
      r'^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)+$',
    );
    if (!emailPattern.hasMatch(email)) {
      return 'Please enter a valid email address';
    }
    return null;
  }

  static String? validatePassword(String? value) {
    if (value == null || value.isEmpty) {
      return 'Password is required';
    }
    if (value.length < minPasswordLength) {
      return 'Password must be at least $minPasswordLength characters';
    }
    if (value.length > maxPasswordLength) {
      return 'Password cannot exceed $maxPasswordLength characters';
    }
    if (!RegExp(r'[a-z]').hasMatch(value)) {
      return 'Password must include a lowercase letter';
    }
    if (!RegExp(r'[A-Z]').hasMatch(value)) {
      return 'Password must include an uppercase letter';
    }
    if (!RegExp(r'[0-9]').hasMatch(value)) {
      return 'Password must include a number';
    }
    if (!RegExp(
      r'''[!@#$%^&*()_+\-=\[\]{};'\\:"|<>?,./`~]''',
    ).hasMatch(value)) {
      return 'Password must include a symbol';
    }
    return null;
  }

  static String? validatePhone(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Phone number is required';
    }
    final cleaned = value.replaceAll(RegExp(r'[\s\-\(\)\.]'), '');
    if (!RegExp(r'^\+?[0-9]{7,15}$').hasMatch(cleaned)) {
      return 'Please enter a valid international phone number';
    }
    return null;
  }

  static String? validateDisplayName(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Display name is required';
    }
    if (value.trim().length > 50) {
      return 'Display name must not exceed 50 characters';
    }
    return null;
  }

  static String? validateGroupTitle(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Title is required';
    }
    if (value.trim().length > 64) {
      return 'Title must not exceed 64 characters';
    }
    return null;
  }

  static String? validateBio(String? value) {
    if (value != null && value.length > 256) {
      return 'Bio must not exceed 256 characters';
    }
    return null;
  }
}
