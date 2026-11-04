# Call bundle UX implementation status

One global Call Experience Bundle controls incoming and ongoing production presentation. Independent incoming/ongoing picker tiles are removed, while compatibility keys/setters remain to avoid data loss. PiP/island/audio/low-data/privacy settings remain separate capabilities.

## State and capabilities

UI observes real CallSessionState; no visual component advances signalling. Only connected state displays elapsed duration. Outgoing, connecting, reconnecting, terminal/failure states retain explicit labels. Available runtime actions come from actual local media/call capability; no placeholder keypad/add-call/hold/record controls are added to production.

Incoming centre badge is phone/video, incoming only, per owner choice. Caller name remains. This is an intentional deviation from avatar-based references and is not asserted to be OEM-exact.

## Distinct production control grammar staged

Google Latest: labelled tonal pills and separate red end action. Apple: circle grid/separate end. Samsung: lower rounded panel/wide end. Classic: airy circles. Vivo: two-column panel/wide end. Azure: three-column circles. Mono: rounded squares. Nextcloud: flat toolbar/end above. Chaty Default controls retained. Voice identity size/type/placement vary by selected bundle. Final font/viewport captures remain required.

## Other surfaces

Production PiP retains real video and guarded state labels, consumes existing bundle color/corner tokens, and retains expand/island/end callbacks. It is not a proof of nine OEM-specific compact geometries. Live island uses DynamicIslandPayload, not standalone ChatyCallIsland. OS notification/lockscreen templates remain platform-owned; do not imitate system UI or invent bundle-dependent system controls.

## Unsupported group boundary

ChatyCallSession has one remote participant and no group runtime model. BundleGroupMeetingView and invite preview are visual fixtures, not production group signalling. Do not fabricate runtime support or count preview as parity.

## Reference evidence

Google article: https://9to5google.com/2025/08/21/google-phone-material-3-expressive-redesign/
Google support: https://support.google.com/phoneapp/answer/2811745?hl=en
The inspected v186 screenshot has a centre puck; Google action mode depends on version/settings. Product-required explicit red-left/green-right buttons are not a universal Google exact-match claim. Remaining OEM/Nextcloud source evidence and visual comparisons remain open.

## Accessibility acceptance

Final verification must cover 48dp targets, semantic labels/focus, TalkBack, RTL, narrow/landscape/multiwindow, font scales 1/1.3/1.5/2, contrast and system animations disabled. Source implementation alone does not pass these gates.
