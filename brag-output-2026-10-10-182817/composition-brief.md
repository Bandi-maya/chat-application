# Hyperframes Composition Brief: Chaty

## Objective
Create a short launch-style brag video for Chaty, a private, customizable, production-ready communication application with military-grade RFC 9420 MLS End-to-End Encryption.

## Output
- Composition directory: `brag-output-2026-10-10-182817/composition/`
- Rendered video: `brag-output-2026-10-10-182817/brag.mp4`
- Format: landscape — 1920x1080
- Duration: 18 seconds

## Source Material
- Project root: `C:\Users\Bandi\Desktop\desktop\chat-application`
- Primary files read: `web/index.html`, `README.md`, `lib/ui/core/design_system/tokens/app_tokens.dart`, `lib/ui/core/theme/theme_presets.dart`
- Product name: Chaty
- Tagline / strongest claim: "Private, customizable, production-ready communication application engineered with Flutter, Supabase, RFC 9420 MLS End-to-End Encryption, and WebRTC real-time media."
- Key UI or visual moment to recreate: The clean WhatsApp-inspired chat interface with message sending and calling features
- Copy that must appear verbatim:
  - "Hey, this is actually private"
  - "Private by design. Beautiful by choice."

## Creative Direction
- Tone preset: polished
- Creative direction: quiet premium product film
- Interpretation: Fewer scenes, longer holds. Confidence through restraint - letting the quality of the product speak for itself rather than relying on flashy effects.
- Angle: Showcase how Chaty combines enterprise security with beautiful, intuitive design - proving that private communication doesn't have to look or feel clinical or complicated.
- Hook: A lock icon animates open to reveal the Chaty app interface, with the text "Your conversations. Actually private." appearing as the lock fully opens.
- Outro / punchline: The Chaty logo appears with the tagline: "Private by design. Beautiful by choice."
- Avoid:
  - Generic SaaS language like "streamline your workflow"
  - Abstract filler visuals
  - Unrelated visual redesign

## Visual Identity
- Background: #FFFFFF (clean white from WhatsApp Light theme)
- Text: #111B21 (WhatsApp dark charcoal)
- Accent: #25D366 (WhatsApp green)
- Display font: System sans-serif (clean, readable)
- Body font: System sans-serif
- Visual references from the project: Chat bubbles, call interface, lock/unlock animations, messaging UI

## Storyboard
Use the storyboard in `brag-output-2026-10-10-182817/brag-plan.md` as the creative contract.

Scene summary:
1. Lock Reveal — 3s — Lock opening to reveal app
2. App Reveal — 2s — Chaty chat interface fading in
3. Messaging Flow — 6s — Typing, sending message, delivery receipts
4. Calling Flow — 5s — Initiating and connecting video call
5. Outro — 2s — Logo and tagline appearance

## Audio
- Audio role: warm professional bed with subtle UI motion-matched accents
- Audio arc: Starts subtle, swells slightly on key moments (message send, call connect, logo reveal), fades out on ending
- Music: happy-beats-business-moves-vol-12-by-ende-dot-app.mp3 (steady and clean - best for polished tone)
- Music treatment: Start at -3s (pre-roll), fade in over first 0.5s, maintain -18dB volume (0.35 linear), subtle swell on key moments, fade-out starting at 17s
- Music cue guidance: Using bundled track vol-12 - preset cues available at composition time for message send (~4s), call initiation (~10s), and logo reveal (~16s)
- Audio-reactive treatment: subtle; use music RMS/bass to make UI elements gently pulse with key actions
- Audio-coupled moments:
  - Scene 3 — Typing text: keyboard keypress sounds
  - Scene 3 — Message send: soft impact or interface accent
  - Scene 4 — Call connect: gentle chime or bell tone
  - Scene 5 — Logo appearance: soft musical chime
- SFX selection guidance: For polished tone, use minimal but present SFX - 2-3 very subtle cues. Use interface/drop_* for gentle reveals, interface/click_* for button presses, and avoid anything aggressive.
- SFX analysis guidance: Use lower high-frequency-risk sounds for repeated or polished moments
- Exact SFX choice: Hyperframes should choose filenames, timestamps, density, and volume based on the implemented animation.
- Audio files: copy the chosen music and any Hyperframes-selected SFX into `brag-output-2026-10-10-182817/composition/assets/`

## Hyperframes Instructions
Load the composition-building Hyperframes domain skills — `hyperframes-core` (composition contract + `data-*` timing), `hyperframes-animation` (motion), `hyperframes-creative` (design spec, beats, audio-reactive), `hyperframes-keyframes` (seek-safe keyframes), and `hyperframes-cli` (lint/check/render). /brag is its own workflow: do not enter the `hyperframes` entry-point intent interview and do not route into its generic promo / launch-video workflow. Prefer native Hyperframes conventions over anything in `/brag`.

Requirements:
- Show at least one real UI, copy, or visual element from the source project.
- Keep all text readable in the final render.
- Keep the video within 15-25 seconds.
- Include the planned music/SFX layer unless audio was explicitly disabled or documented as intentionally silent.
- Treat `/brag` audio notes as guidance, not a fixed cue sheet. Choose SFX after the visual animation exists.
- Treat music cue metadata as optional timing hints. Hyperframes decides exact animation timing and should ignore cues that hurt readability, scene pacing, or the product story.
- Major reveals may move toward nearby strong cues within about 0.15s. Smaller entrances may align to nearby beat points within about 0.10s. Use only 1-3 strong cue locks in a 15-25s video unless the edit clearly benefits from more.
- Use SFX to support motion and interaction: card sounds for card-like reveals, short announcement cues for major payoffs, key/click sounds for text or user actions, and restraint when the edit is already busy.
- Honor planned music treatment such as fade-outs, ducking, beat-aligned reveals, or letting a final SFX ring over the music, using the best Hyperframes-supported implementation.
- When music is present and the treatment is not `none`, consider Hyperframes audio-reactive workflow: extract audio data and use RMS/frequency bands for subtle, brand-specific motion. Good targets are glow, depth, background warmth, card presence, title emphasis, or other existing visual elements. Avoid waveform/equalizer visuals, musical-note graphics, generic particle systems, strobing, or heavy pulsing.
- Use local assets for audio and any required runtime/media dependencies when possible.
- Run `hyperframes check` before render — it is brag's single gate.
- Keep creation and rendering local. Remote or publishing workflows require a separate explicit user request.