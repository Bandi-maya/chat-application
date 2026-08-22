import 'package:flutter/material.dart';

/// Chat-specific semantic color token contract.
/// Available everywhere via `context.chatColors` or `Theme.of(context).extension<ChatColors>()`.
@immutable
class ChatColors extends ThemeExtension<ChatColors> {
  // Bubbles
  final Color incomingBubble;
  final Color incomingText;
  final Color outgoingBubble;
  final Color outgoingText;

  // Surfaces & Details
  final Color replySurface;
  final Color replyBorder;
  final Color composerSurface;
  final Color composerBorder;
  final Color messageMetadata;
  final Color deliveryTick;
  final Color readTick;

  // Interactions & Reactions
  final Color reactionSurface;
  final Color reactionBorder;
  final Color reactionCount;
  final Color mentionBackground;
  final Color selectionBackground;
  final Color chatWallpaperBackground;

  // Media & Components
  final Color voiceNoteWaveform;
  final Color voiceNoteProgress;
  final Color voiceNoteButton;
  final Color systemMessageBackground;
  final Color systemMessageText;
  final Color taskCardSurface;
  final Color taskCardBorder;

  const ChatColors({
    required this.incomingBubble,
    required this.incomingText,
    required this.outgoingBubble,
    required this.outgoingText,
    required this.replySurface,
    required this.replyBorder,
    required this.composerSurface,
    required this.composerBorder,
    required this.messageMetadata,
    required this.deliveryTick,
    required this.readTick,
    required this.reactionSurface,
    required this.reactionBorder,
    required this.reactionCount,
    required this.mentionBackground,
    required this.selectionBackground,
    required this.chatWallpaperBackground,
    required this.voiceNoteWaveform,
    required this.voiceNoteProgress,
    required this.voiceNoteButton,
    required this.systemMessageBackground,
    required this.systemMessageText,
    required this.taskCardSurface,
    required this.taskCardBorder,
  });

  @override
  ChatColors copyWith({
    Color? incomingBubble,
    Color? incomingText,
    Color? outgoingBubble,
    Color? outgoingText,
    Color? replySurface,
    Color? replyBorder,
    Color? composerSurface,
    Color? composerBorder,
    Color? messageMetadata,
    Color? deliveryTick,
    Color? readTick,
    Color? reactionSurface,
    Color? reactionBorder,
    Color? reactionCount,
    Color? mentionBackground,
    Color? selectionBackground,
    Color? chatWallpaperBackground,
    Color? voiceNoteWaveform,
    Color? voiceNoteProgress,
    Color? voiceNoteButton,
    Color? systemMessageBackground,
    Color? systemMessageText,
    Color? taskCardSurface,
    Color? taskCardBorder,
  }) {
    return ChatColors(
      incomingBubble: incomingBubble ?? this.incomingBubble,
      incomingText: incomingText ?? this.incomingText,
      outgoingBubble: outgoingBubble ?? this.outgoingBubble,
      outgoingText: outgoingText ?? this.outgoingText,
      replySurface: replySurface ?? this.replySurface,
      replyBorder: replyBorder ?? this.replyBorder,
      composerSurface: composerSurface ?? this.composerSurface,
      composerBorder: composerBorder ?? this.composerBorder,
      messageMetadata: messageMetadata ?? this.messageMetadata,
      deliveryTick: deliveryTick ?? this.deliveryTick,
      readTick: readTick ?? this.readTick,
      reactionSurface: reactionSurface ?? this.reactionSurface,
      reactionBorder: reactionBorder ?? this.reactionBorder,
      reactionCount: reactionCount ?? this.reactionCount,
      mentionBackground: mentionBackground ?? this.mentionBackground,
      selectionBackground: selectionBackground ?? this.selectionBackground,
      chatWallpaperBackground: chatWallpaperBackground ?? this.chatWallpaperBackground,
      voiceNoteWaveform: voiceNoteWaveform ?? this.voiceNoteWaveform,
      voiceNoteProgress: voiceNoteProgress ?? this.voiceNoteProgress,
      voiceNoteButton: voiceNoteButton ?? this.voiceNoteButton,
      systemMessageBackground: systemMessageBackground ?? this.systemMessageBackground,
      systemMessageText: systemMessageText ?? this.systemMessageText,
      taskCardSurface: taskCardSurface ?? this.taskCardSurface,
      taskCardBorder: taskCardBorder ?? this.taskCardBorder,
    );
  }

  @override
  ChatColors lerp(ThemeExtension<ChatColors>? other, double t) {
    if (other is! ChatColors) return this;
    return ChatColors(
      incomingBubble: Color.lerp(incomingBubble, other.incomingBubble, t) ?? incomingBubble,
      incomingText: Color.lerp(incomingText, other.incomingText, t) ?? incomingText,
      outgoingBubble: Color.lerp(outgoingBubble, other.outgoingBubble, t) ?? outgoingBubble,
      outgoingText: Color.lerp(outgoingText, other.outgoingText, t) ?? outgoingText,
      replySurface: Color.lerp(replySurface, other.replySurface, t) ?? replySurface,
      replyBorder: Color.lerp(replyBorder, other.replyBorder, t) ?? replyBorder,
      composerSurface: Color.lerp(composerSurface, other.composerSurface, t) ?? composerSurface,
      composerBorder: Color.lerp(composerBorder, other.composerBorder, t) ?? composerBorder,
      messageMetadata: Color.lerp(messageMetadata, other.messageMetadata, t) ?? messageMetadata,
      deliveryTick: Color.lerp(deliveryTick, other.deliveryTick, t) ?? deliveryTick,
      readTick: Color.lerp(readTick, other.readTick, t) ?? readTick,
      reactionSurface: Color.lerp(reactionSurface, other.reactionSurface, t) ?? reactionSurface,
      reactionBorder: Color.lerp(reactionBorder, other.reactionBorder, t) ?? reactionBorder,
      reactionCount: Color.lerp(reactionCount, other.reactionCount, t) ?? reactionCount,
      mentionBackground: Color.lerp(mentionBackground, other.mentionBackground, t) ?? mentionBackground,
      selectionBackground: Color.lerp(selectionBackground, other.selectionBackground, t) ?? selectionBackground,
      chatWallpaperBackground: Color.lerp(chatWallpaperBackground, other.chatWallpaperBackground, t) ?? chatWallpaperBackground,
      voiceNoteWaveform: Color.lerp(voiceNoteWaveform, other.voiceNoteWaveform, t) ?? voiceNoteWaveform,
      voiceNoteProgress: Color.lerp(voiceNoteProgress, other.voiceNoteProgress, t) ?? voiceNoteProgress,
      voiceNoteButton: Color.lerp(voiceNoteButton, other.voiceNoteButton, t) ?? voiceNoteButton,
      systemMessageBackground: Color.lerp(systemMessageBackground, other.systemMessageBackground, t) ?? systemMessageBackground,
      systemMessageText: Color.lerp(systemMessageText, other.systemMessageText, t) ?? systemMessageText,
      taskCardSurface: Color.lerp(taskCardSurface, other.taskCardSurface, t) ?? taskCardSurface,
      taskCardBorder: Color.lerp(taskCardBorder, other.taskCardBorder, t) ?? taskCardBorder,
    );
  }
}
