import 'package:flutter/material.dart';
import '../../injection/locator.dart';
import 'auto_reply_rule_engine.dart';

class AutoReplyRulesScreen extends StatefulWidget {
  const AutoReplyRulesScreen({super.key});

  @override
  State<AutoReplyRulesScreen> createState() => _AutoReplyRulesScreenState();
}

class _AutoReplyRulesScreenState extends State<AutoReplyRulesScreen> {
  late final AutoReplyRuleEngine _engine;

  @override
  void initState() {
    super.initState();
    _engine = locator<AutoReplyRuleEngine>();
  }

  Future<void> _openAddRuleDialog() async {
    final keywordCtrl = TextEditingController();
    final responseCtrl = TextEditingController();
    String matchType = 'contains';
    String recipientFilter = 'all';

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setModalState) {
          final theme = Theme.of(context);
          final primary = theme.colorScheme.primary;
          return Container(
            padding: EdgeInsets.only(
              left: 20,
              right: 20,
              top: 24,
              bottom: MediaQuery.of(context).viewInsets.bottom + 24,
            ),
            decoration: BoxDecoration(
              color: theme.scaffoldBackgroundColor,
              borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Icon(Icons.reply_all_rounded, color: primary),
                    const SizedBox(width: 10),
                    Text(
                      'Add Auto-Reply Rule',
                      style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                    ),
                    const Spacer(),
                    IconButton(
                      icon: const Icon(Icons.close_rounded),
                      onPressed: () => Navigator.of(context).pop(),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: keywordCtrl,
                  decoration: InputDecoration(
                    labelText: 'Trigger Keyword / Phrase',
                    hintText: 'e.g., hello, status, help',
                    filled: true,
                    fillColor: theme.cardColor,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: responseCtrl,
                  maxLines: 3,
                  decoration: InputDecoration(
                    labelText: 'Automatic Response Message',
                    hintText: 'e.g., Hello! I am currently away. Will reply shortly.',
                    filled: true,
                    fillColor: theme.cardColor,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: DropdownButtonFormField<String>(
                        value: matchType,
                        decoration: InputDecoration(
                          labelText: 'Match Type',
                          filled: true,
                          fillColor: theme.cardColor,
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide.none,
                          ),
                        ),
                        items: const [
                          DropdownMenuItem(value: 'contains', child: Text('Contains')),
                          DropdownMenuItem(value: 'exact', child: Text('Exact match')),
                          DropdownMenuItem(value: 'starts_with', child: Text('Starts with')),
                        ],
                        onChanged: (val) {
                          if (val != null) {
                            setModalState(() => matchType = val);
                          }
                        },
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: DropdownButtonFormField<String>(
                        value: recipientFilter,
                        decoration: InputDecoration(
                          labelText: 'Apply To',
                          filled: true,
                          fillColor: theme.cardColor,
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide.none,
                          ),
                        ),
                        items: const [
                          DropdownMenuItem(value: 'all', child: Text('All Chats')),
                          DropdownMenuItem(value: 'direct', child: Text('Direct Only')),
                          DropdownMenuItem(value: 'group', child: Text('Groups Only')),
                        ],
                        onChanged: (val) {
                          if (val != null) {
                            setModalState(() => recipientFilter = val);
                          }
                        },
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 20),
                FilledButton.icon(
                  onPressed: () {
                    final kw = keywordCtrl.text.trim();
                    final resp = responseCtrl.text.trim();
                    if (kw.isEmpty || resp.isEmpty) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Keyword and response message cannot be empty.')),
                      );
                      return;
                    }
                    _engine.addRule(
                      keyword: kw,
                      responseMessage: resp,
                      matchType: matchType,
                      recipientFilter: recipientFilter,
                    );
                    Navigator.of(context).pop();
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Auto-reply rule added.')),
                    );
                  },
                  icon: const Icon(Icons.check_rounded),
                  label: const Text('Save Auto-Reply Rule'),
                  style: FilledButton.styleFrom(
                    backgroundColor: primary,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final primary = theme.colorScheme.primary;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Auto-Reply Rules'),
        centerTitle: false,
      ),
      body: ListenableBuilder(
        listenable: _engine,
        builder: (context, _) {
          final isMasterEnabled = _engine.isMasterEnabled;
          final rules = _engine.rules;

          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Card(
                elevation: 0,
                color: theme.cardColor,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                child: SwitchListTile.adaptive(
                  value: isMasterEnabled,
                  onChanged: (val) => _engine.setMasterEnabled(val),
                  activeColor: primary,
                  title: const Text(
                    'Auto-Reply Engine',
                    style: TextStyle(fontWeight: FontWeight.bold),
                  ),
                  subtitle: Text(
                    isMasterEnabled
                        ? 'Active — incoming messages will trigger configured responses'
                        : 'Disabled — incoming messages are ignored by auto-reply',
                  ),
                  secondary: Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: (isMasterEnabled ? primary : Colors.grey)
                          .withValues(alpha: 0.15),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      Icons.reply_all_rounded,
                      color: isMasterEnabled ? primary : Colors.grey,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'CONFIGURED RULES (${rules.length})',
                    style: theme.textTheme.labelMedium?.copyWith(
                      color: theme.colorScheme.onSurface.withValues(alpha: 0.6),
                      letterSpacing: 1.2,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  TextButton.icon(
                    onPressed: _openAddRuleDialog,
                    icon: const Icon(Icons.add_rounded, size: 18),
                    label: const Text('Add Rule'),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              if (rules.isEmpty)
                Card(
                  elevation: 0,
                  color: theme.cardColor.withValues(alpha: 0.5),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 24),
                    child: Column(
                      children: [
                        Icon(
                          Icons.rule_folder_outlined,
                          size: 48,
                          color: theme.colorScheme.onSurface.withValues(alpha: 0.4),
                        ),
                        const SizedBox(height: 12),
                        Text(
                          'No Auto-Reply Rules Configured',
                          style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          'Tap "Add Rule" to define keywords and automated responses for incoming chats.',
                          textAlign: TextAlign.center,
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurface.withValues(alpha: 0.6),
                          ),
                        ),
                      ],
                    ),
                  ),
                )
              else
                ...List.generate(rules.length, (index) {
                  final rule = rules[index];
                  return Card(
                    key: ValueKey(rule.id),
                    margin: const EdgeInsets.only(bottom: 12),
                    elevation: 0,
                    color: theme.cardColor,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                    child: Padding(
                      padding: const EdgeInsets.all(14),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                decoration: BoxDecoration(
                                  color: primary.withValues(alpha: 0.12),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: Text(
                                  rule.matchType.toUpperCase(),
                                  style: TextStyle(
                                    fontSize: 11,
                                    fontWeight: FontWeight.bold,
                                    color: primary,
                                  ),
                                ),
                              ),
                              const SizedBox(width: 8),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                decoration: BoxDecoration(
                                  color: Colors.blueGrey.withValues(alpha: 0.12),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: Text(
                                  rule.recipientFilter.toUpperCase(),
                                  style: const TextStyle(
                                    fontSize: 11,
                                    fontWeight: FontWeight.bold,
                                    color: Colors.blueGrey,
                                  ),
                                ),
                              ),
                              const Spacer(),
                              Switch.adaptive(
                                value: rule.enabled,
                                activeColor: primary,
                                onChanged: (val) => _engine.toggleRule(index, val),
                              ),
                              IconButton(
                                icon: const Icon(Icons.delete_outline_rounded, color: Colors.redAccent),
                                onPressed: () => _engine.deleteRule(index),
                                tooltip: 'Delete Rule',
                              ),
                            ],
                          ),
                          const SizedBox(height: 8),
                          RichText(
                            text: TextSpan(
                              style: theme.textTheme.bodyMedium,
                              children: [
                                TextSpan(
                                  text: 'Trigger: ',
                                  style: TextStyle(
                                    fontWeight: FontWeight.bold,
                                    color: theme.colorScheme.onSurface.withValues(alpha: 0.7),
                                  ),
                                ),
                                TextSpan(
                                  text: '"${rule.keyword}"',
                                  style: const TextStyle(fontWeight: FontWeight.w600),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 6),
                          RichText(
                            text: TextSpan(
                              style: theme.textTheme.bodyMedium,
                              children: [
                                TextSpan(
                                  text: 'Response: ',
                                  style: TextStyle(
                                    fontWeight: FontWeight.bold,
                                    color: theme.colorScheme.onSurface.withValues(alpha: 0.7),
                                  ),
                                ),
                                TextSpan(text: rule.responseMessage),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  );
                }),
            ],
          );
        },
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _openAddRuleDialog,
        icon: const Icon(Icons.add_rounded),
        label: const Text('New Rule'),
        backgroundColor: primary,
        foregroundColor: Colors.white,
      ),
    );
  }
}
