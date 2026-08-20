import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';

import '../../../data/services/chaty_backend_service.dart';
import '../validators/chaty_validators.dart';

class UsernameAvailabilityField extends StatefulWidget {
  final TextEditingController controller;
  final ChatyBackendService backend;
  final String? currentUsername;
  final bool enabled;
  final ValueChanged<bool?>? onAvailabilityChanged;
  final TextStyle? style;
  final InputDecoration? decoration;

  const UsernameAvailabilityField({
    super.key,
    required this.controller,
    required this.backend,
    this.currentUsername,
    this.enabled = true,
    this.onAvailabilityChanged,
    this.style,
    this.decoration,
  });

  @override
  State<UsernameAvailabilityField> createState() => _UsernameAvailabilityFieldState();
}

class _UsernameAvailabilityFieldState extends State<UsernameAvailabilityField> {
  Timer? _debounce;
  int _generation = 0;
  bool _checking = false;
  bool? _available;
  String? _localError;
  List<String> _suggestions = const <String>[];

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onChanged);
    _onChanged();
  }

  @override
  void didUpdateWidget(covariant UsernameAvailabilityField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onChanged);
      widget.controller.addListener(_onChanged);
      _onChanged();
    }
  }

  @override
  void dispose() {
    _debounce?.cancel();
    widget.controller.removeListener(_onChanged);
    super.dispose();
  }

  void _onChanged() {
    _debounce?.cancel();
    final normalized = ChatyValidators.normalizeUsername(widget.controller.text);
    final error = ChatyValidators.validateUsername(normalized);
    final current = ChatyValidators.normalizeUsername(widget.currentUsername ?? '');
    final generation = ++_generation;

    if (error != null) {
      setState(() {
        _localError = error;
        _available = null;
        _checking = false;
        _suggestions = const <String>[];
      });
      widget.onAvailabilityChanged?.call(null);
      return;
    }
    if (current.isNotEmpty && normalized == current) {
      setState(() {
        _localError = null;
        _available = true;
        _checking = false;
        _suggestions = const <String>[];
      });
      widget.onAvailabilityChanged?.call(true);
      return;
    }

    setState(() {
      _localError = null;
      _available = null;
      _checking = true;
      _suggestions = const <String>[];
    });
    widget.onAvailabilityChanged?.call(null);
    _debounce = Timer(const Duration(milliseconds: 420), () => _check(normalized, generation));
  }

  Future<void> _check(String normalized, int generation) async {
    try {
      final available = await widget.backend.isUsernameAvailable(normalized);
      if (!mounted || generation != _generation) return;
      var suggestions = const <String>[];
      if (!available) suggestions = await _buildSuggestions(normalized, generation);
      if (!mounted || generation != _generation) return;
      setState(() {
        _checking = false;
        _available = available;
        _suggestions = suggestions;
      });
      widget.onAvailabilityChanged?.call(available);
    } catch (_) {
      if (!mounted || generation != _generation) return;
      setState(() {
        _checking = false;
        _available = null;
        _suggestions = const <String>[];
      });
      widget.onAvailabilityChanged?.call(null);
    }
  }

  Future<List<String>> _buildSuggestions(String base, int generation) async {
    final cleaned = base.replaceAll(RegExp(r'[^a-z0-9_]'), '');
    final stem = (cleaned.isEmpty ? 'chatyuser' : cleaned).substring(0, min(cleaned.isEmpty ? 9 : cleaned.length, 17));
    final random = Random.secure();
    final result = <String>[];
    for (var attempt = 0; attempt < 12 && result.length < 3; attempt++) {
      if (generation != _generation) break;
      final suffix = 10 + random.nextInt(9990);
      final candidate = '${stem}_$suffix';
      if (ChatyValidators.validateUsername(candidate) != null) continue;
      if (await widget.backend.isUsernameAvailable(candidate)) result.add(candidate);
    }
    return result;
  }

  String? _validator(String? value) {
    final local = ChatyValidators.validateUsername(value);
    if (local != null) return local;
    final normalized = ChatyValidators.normalizeUsername(value ?? '');
    final current = ChatyValidators.normalizeUsername(widget.currentUsername ?? '');
    if (current.isNotEmpty && normalized == current) return null;
    if (_available == false) return 'That username is already taken';
    if (_available != true) return 'Wait for username availability to finish checking';
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final statusColor = _available == true
        ? Colors.green
        : _available == false
            ? scheme.error
            : scheme.onSurfaceVariant;
    final statusText = _checking
        ? 'Checking availability…'
        : _available == true
            ? 'Username is available'
            : _available == false
                ? 'Username is already in use'
                : _localError ?? '3–24 letters, numbers or underscores';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextFormField(
          controller: widget.controller,
          enabled: widget.enabled,
          autocorrect: false,
          textCapitalization: TextCapitalization.none,
          keyboardType: TextInputType.text,
          textInputAction: TextInputAction.next,
          style: widget.style,
          validator: _validator,
          decoration: (widget.decoration ?? const InputDecoration(labelText: 'Username')).copyWith(
            prefixText: '@',
            suffixIcon: _checking
                ? const Padding(
                    padding: EdgeInsets.all(13),
                    child: SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)),
                  )
                : _available == true
                    ? const Icon(Icons.check_circle_rounded, color: Colors.green)
                    : _available == false
                        ? Icon(Icons.cancel_rounded, color: scheme.error)
                        : null,
          ),
        ),
        const SizedBox(height: 6),
        Row(
          children: [
            Icon(
              _available == true
                  ? Icons.check_circle_outline_rounded
                  : _available == false
                      ? Icons.info_outline_rounded
                      : Icons.alternate_email_rounded,
              size: 15,
              color: statusColor,
            ),
            const SizedBox(width: 6),
            Expanded(
              child: Text(statusText, style: Theme.of(context).textTheme.bodySmall?.copyWith(color: statusColor)),
            ),
          ],
        ),
        if (_suggestions.isNotEmpty) ...[
          const SizedBox(height: 8),
          Text('Available suggestions', style: Theme.of(context).textTheme.labelMedium),
          const SizedBox(height: 6),
          Wrap(
            spacing: 8,
            runSpacing: 6,
            children: _suggestions
                .map(
                  (suggestion) => ActionChip(
                    avatar: const Icon(Icons.add_rounded, size: 16),
                    label: Text('@$suggestion'),
                    onPressed: widget.enabled
                        ? () {
                            widget.controller.value = TextEditingValue(
                              text: suggestion,
                              selection: TextSelection.collapsed(offset: suggestion.length),
                            );
                          }
                        : null,
                  ),
                )
                .toList(growable: false),
          ),
        ],
      ],
    );
  }
}
