# News custom emoji: frontend and backend boundary

Preview branch: `test/news-emoji-sections-nov7`
Base: `kotlin`, commit `3bc0215836470bfe2023a0af8147067b001bd50a`.

## This frontend preview

- Adds a News section under EMOJI, not STICKER.
- Includes 100 existing approximate v1 News assets. Redraw improvements remain separate and held.
- Uses compact adaptive cells, a section title and a horizontal pack strip.
- Reuses animated WebP rendering with foreground lifecycle and reduced-motion fallback.
- A tap selects an item locally. It does not insert a fake Unicode character, send a sticker, change a message or call the backend.
- Shows a visible sending-unavailable notice. Regular Unicode emoji and existing actual stickers remain separate.

This is source-only work. No APK has been generated for this change. Layout images are design previews, not emulator screenshots or runtime proof.

## Required identity and text model

Use a stable pack ID (`chaty-news-clean-v1`) and item IDs (`001` through `100`). Do not infer Unicode equivalents from the artwork. Each message needs ordered text and custom-emoji content that retains those IDs.

A suggested contract, subject to backend review, is an ordered content-segment array:

```json
{
  "content_version": 1,
  "segments": [
    {"type": "text", "text": "Hello "},
    {"type": "custom_emoji", "pack_id": "chaty-news-clean-v1", "item_id": "001", "fallback_text": "[News 001]"}
  ]
}
```

This contract is proposed only. The existing API has not been changed to accept it.

## Backend work before sending can be enabled

- Validate segment type, order, allowed IDs and limits. Treat the catalog and asset version as controlled data, never accept arbitrary file paths from a message.
- Store and return custom-emoji segments without flattening them to plain text.
- Preserve segments through sync, message history, offline delivery, edit, reply, forward and export paths. Define what quoting and copying return.
- Define old-client and notification fallbacks. Do not silently display a different Unicode emoji or an unrelated sticker.
- Define catalog/asset distribution, immutable versions and integrity checks so sender and receiver resolve the same item.
- Review current message encryption/serialization and storage boundaries before adding fields. The transport must retain its existing security properties.
- Define whether reactions can use custom emoji. The preview does not add custom-emoji reactions.

## Frontend work after the contract is agreed

- Structured composer draft model with insertion, caret movement, selection and deletion around custom-emoji items.
- Outgoing serialization and incoming parsing of the agreed schema.
- Inline rendering in mixed text and custom-emoji messages, with accessible labels and fallbacks.
- Persisted draft/history behavior and supported reply/forward/edit flows.
- Tests for round-trip identity, mixed text, invalid IDs, old clients, offline delivery, accessibility, animation lifecycle and reduced motion.

## Verification before release

Generate only after owner approval. Check the actual composer path, scroll all 100 items, test light/dark and narrow widths, and verify animation with normal animator scale. A debug catalog image or a decoder-only pass is not send/receive proof.
