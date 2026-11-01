# Chaty custom-backend WebRTC ICE endpoint

This route belongs to the **custom Node/Express API**, not Supabase. Mount it on the API root:

```js
const { createWebRtcIceRouter } = require('./routes/chaty-webrtc-ice');

// Reuse the same JWT/auth middleware that protects /chat/call/signal.
// It must set req.user.id (or req.auth.userId).
app.use(createWebRtcIceRouter({ requireAuth }));
```

The Android app calls:

- `GET /chat/call/ice-servers`
- fallback: `GET /api/v1/chat/call/ice-servers` when the first path returns 404

Headers: `Authorization: Bearer <Chaty API token>`, `x-tenant-id: <active tenant>`.

## Coturn configuration

Set these on the custom backend only:

- `TURN_URLS`: comma-separated or JSON array of `turn:` / `turns:` URLs
- `TURN_SHARED_SECRET`: at least 32 characters; same static-auth-secret configured on coturn
- `TURN_CREDENTIAL_TTL_SECONDS`: optional, defaults to 3600, clamped to 300–21600
- `STUN_URLS`: optional list; Google STUN defaults are used otherwise

The route derives a short-lived Coturn REST username (`expiry:userId`) and HMAC-SHA1 credential. The shared secret never leaves the backend. Requests are authenticated, scoped to the authenticated user, marked no-store, and limited to 30 requests/minute/user per process. Put a shared rate limiter in front when running multiple API instances.

The response shape is:

```json
{
  "ice_servers": [
    { "urls": ["stun:stun.example.net:3478"] },
    {
      "urls": ["turn:turn.example.net:3478?transport=udp", "turns:turn.example.net:5349?transport=tcp"],
      "username": "unix-expiry:user-id",
      "credential": "ephemeral-hmac"
    }
  ],
  "relay_configured": true,
  "expires_at": 1790000000
}
```

If TURN is not configured, the route returns STUN-only with `relay_configured: false`. That is not a relay fallback; configure coturn to make restrictive-NAT transfers/calls reliable.

The existing custom WebSocket `call_signal` event continues to carry WebRTC offer/answer/ICE and file-transfer control messages. The actual file bytes remain end-to-end encrypted over the DataChannel; this endpoint never receives file content or transfer secrets.

## Voice-note recording indicator over the custom WebSocket

The Android client publishes recording state on the existing authenticated socket:

```json
{ "type": "recording", "room_id": "conversation-id", "is_recording": true }
```

The custom WebSocket handler should validate that the authenticated socket user belongs to `room_id`, then broadcast to the other room participants:

```json
{
  "type": "recording",
  "data": {
    "room_id": "conversation-id",
    "user_id": "authenticated-sender-id",
    "is_recording": true
  }
}
```

Do not accept `user_id` from the client payload. Derive it from the authenticated socket session. Broadcast `is_recording: false` on stop, cancel, socket close and session expiry. Apply the user's recording-indicator privacy preference server-side.
