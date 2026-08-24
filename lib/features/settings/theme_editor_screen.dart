import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import '../../ui/core/design_system/design_system.dart';
import '../messages/message_bubble.dart';
import '../../domain/models/chat_message.dart';
import '../../ui/core/theme/chaty_theme_manager.dart';
import '../../ui/core/theme/image_theme_generator.dart';
import '../../ui/core/theme/theme_preview_card.dart';

class ThemeEditorScreen extends StatefulWidget {
  final ThemeController themeController;

  const ThemeEditorScreen({super.key, required this.themeController});

  @override
  State<ThemeEditorScreen> createState() => _ThemeEditorScreenState();
}

class _ThemeEditorScreenState extends State<ThemeEditorScreen> {
  late ThemeConfig _current;

  @override
  void initState() {
    super.initState();
    _current = widget.themeController.globalTheme;
  }

  void _applyAndSave() {
    widget.themeController.updateThemeConfig(_current);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Text('Theme customization saved!'),
        backgroundColor: context.colors.success,
      ),
    );
  }

  Future<void> _exportTheme() async {
    try {
      final file = await ChatyThemeManager.saveThemeToFile(_current);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Theme exported to ${file.path.split("/").last}'),
          backgroundColor: context.colors.success,
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Export failed: $e')),
      );
    }
  }

  Future<void> _importTheme() async {
    try {
      final result = await FilePicker.pickFile(
        type: FileType.custom,
        allowedExtensions: ['json'],
      );
      if (result == null || result.path == null) return;
      final file = File(result.path!);
      final content = await file.readAsString();
      final theme = ChatyThemeManager.validateAndImportTheme(content);
      setState(() => _current = theme);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Imported " successfully!'),
 backgroundColor: context.colors.success,
 ),
 );
 } catch (e) {
 if (!mounted) return;
 ScaffoldMessenger.of(context).showSnackBar(
 SnackBar(content: Text('Import failed: ')),
 );
 }
 }

 Future<void> _generateFromImage() async {
 try {
 final result = await FilePicker.pickFile(type: FileType.image);
 if (result == null || result.path == null) return;
 final file = File(result.path!);
 final isDark = _current.brightness == Brightness.dark;
 final generated = await ImageThemeGenerator.generateFromImageFile(file, isDark: isDark);
 setState(() => _current = generated);
 if (!mounted) return;
 ScaffoldMessenger.of(context).showSnackBar(
 SnackBar(
 content: const Text('Palette extracted from image!'),
 backgroundColor: context.colors.success,
 ),
 );
 } catch (e) {
 if (!mounted) return;
 ScaffoldMessenger.of(context).showSnackBar(
 SnackBar(content: Text('Could not generate theme from image: ')),
 );
 }
 }

 @override
 Widget build(BuildContext context) {
 return ChatyScaffold(
 appBar: ChatyAppBar(
 title: 'Theme & Design Studio',
 leading: const ChatyBackButton(),
 actions: [
 IconButton(
 icon: const Icon(Icons.palette_outlined),
 tooltip: 'Extract from photo',
 onPressed: _generateFromImage,
 ),
 IconButton(
 icon: const Icon(Icons.file_download_outlined),
 tooltip: 'Import Theme JSON',
 onPressed: _importTheme,
 ),
 IconButton(
 icon: const Icon(Icons.file_upload_outlined),
 tooltip: 'Export Theme JSON',
 onPressed: _exportTheme,
 ),
 IconButton(
 icon: const Icon(Icons.check_rounded),
 tooltip: 'Save Theme',
 onPressed: _applyAndSave,
 ),
 ],
 ),
 body: SingleChildScrollView(
 padding: const EdgeInsets.symmetric(
 horizontal: ChatySpacing.base,
 vertical: ChatySpacing.md,
 ),
 physics: const BouncingScrollPhysics(),
 child: Column(
 crossAxisAlignment: CrossAxisAlignment.stretch,
 children: [
 // Preview message bubble live
 Padding(
 padding: const EdgeInsets.only(bottom: ChatySpacing.md),
 child: ChatyCard(
 child: Column(
 children: [
 MessageBubble(
 message: ChatMessage(
 id: 'prev_1',
 conversationId: 'c1',
 senderId: 'contact_1',
 text: 'Live theme palette preview',
 createdAt: DateTime.now().subtract(const Duration(minutes: 2)),
 deliveryState: DeliveryState.read,
 ),
 isMe: false,
 theme: _current,
 onLongPress: () {},
 ),
 const SizedBox(height: 8),
 MessageBubble(
 message: ChatMessage(
 id: 'prev_2',
 conversationId: 'c1',
 senderId: 'user_me',
 text: 'Warm neutral & high-contrast ready!',
 createdAt: DateTime.now(),
 deliveryState: DeliveryState.read,
 ),
 isMe: true,
 theme: _current,
 onLongPress: () {},
 ),
 ],
 ),
 ),
 ),

 // Presets Header & Grid
 ChatyGroupedSection(
 title: 'Theme Presets ()',
 children: [
 Padding(
 padding: const EdgeInsets.all(ChatySpacing.md),
 child: LayoutBuilder(
 builder: (context, constraints) {
 final isWide = constraints.maxWidth >= 600;
 final crossAxisCount = isWide ? 3 : (constraints.maxWidth >= 380 ? 2 : 1);

 return GridView.builder(
 shrinkWrap: true,
 physics: const NeverScrollableScrollPhysics(),
 itemCount: ThemePresets.all.length,
 gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
 crossAxisCount: crossAxisCount,
 mainAxisSpacing: 12,
 crossAxisSpacing: 12,
 mainAxisExtent: 220,
 ),
 itemBuilder: (context, index) {
 final preset = ThemePresets.all[index];
 final isSelected = preset.id == _current.id;

 return ThemePreviewCard(
 themeConfig: preset,
 isSelected: isSelected,
 onTap: () {
 setState(() => _current = preset);
 widget.themeController.updateThemeConfig(preset);
 },
 );
 },
 );
 },
 ),
 ),
 ],
 ),

 const SizedBox(height: ChatySpacing.xxl),
 ],
 ),
 ),
 );
 }
}
