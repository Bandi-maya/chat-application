/**
 * Chaty custom-backend WebRTC ICE credential endpoint.
 *
 * Mount this router at the API root. It deliberately uses the existing
 * backend's authenticated req.user identity; never accept a user ID from
 * query/body parameters and never ship TURN_SHARED_SECRET to Android.
 *
 * Required environment:
 *   TURN_URLS=turn:turn.example.com:3478?transport=udp,turns:turn.example.com:5349?transport=tcp
 *   TURN_SHARED_SECRET=<coturn static-auth-secret>
 * Optional:
 *   STUN_URLS=stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302
 *   TURN_CREDENTIAL_TTL_SECONDS=3600
 */
'use strict';

const crypto = require('node:crypto');
const express = require('express');

const MAX_TTL_SECONDS = 6 * 60 * 60;
const DEFAULT_TTL_SECONDS = 60 * 60;
const MAX_URLS = 16;
const WINDOW_MS = 60_000;
const MAX_REQUESTS_PER_WINDOW = 30;
const requestWindows = new Map();

function parseUrls(value, fallback) {
  if (!value) return fallback;
  let values;
  try {
    const parsed = JSON.parse(value);
    values = Array.isArray(parsed) ? parsed : [parsed];
  } catch {
    values = String(value).split(',');
  }
  return values.map((item) => String(item).trim()).filter(Boolean).slice(0, MAX_URLS);
}

function validIceUrl(value) {
  return /^(stun|stuns|turn|turns):[a-z0-9.-]+(?::[0-9]{1,5})?(?:\?[a-z0-9=&._-]+)?$/i.test(value);
}

function allowRequest(userId, now) {
  const current = requestWindows.get(userId);
  if (!current || now - current.startedAt >= WINDOW_MS) {
    requestWindows.set(userId, { startedAt: now, count: 1 });
    return true;
  }
  if (current.count >= MAX_REQUESTS_PER_WINDOW) return false;
  current.count += 1;
  return true;
}

function createWebRtcIceRouter({ requireAuth, env = process.env, now = Date.now }) {
  if (typeof requireAuth !== 'function') {
    throw new TypeError('createWebRtcIceRouter requires the custom backend auth middleware');
  }

  const router = express.Router();

  router.get('/chat/call/ice-servers', requireAuth, (req, res) => {
    res.set('Cache-Control', 'no-store, private');
    res.set('Pragma', 'no-cache');

    const userId = String(req.user?.id || req.auth?.userId || '').trim();
    if (!userId || userId.length > 256) {
      return res.status(401).json({ message: 'Authenticated user identity is required.' });
    }

    const timestamp = now();
    if (!allowRequest(userId, timestamp)) {
      return res.status(429).json({ message: 'ICE credential rate limit exceeded.' });
    }

    const stunUrls = parseUrls(
      env.STUN_URLS,
      ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'],
    );
    const turnUrls = parseUrls(env.TURN_URLS, []);
    const secret = String(env.TURN_SHARED_SECRET || '');
    const iceServers = [];

    const safeStun = stunUrls.filter((url) => validIceUrl(url) && /^(stun|stuns):/i.test(url));
    if (safeStun.length) iceServers.push({ urls: safeStun });

    const safeTurn = turnUrls.filter((url) => validIceUrl(url) && /^(turn|turns):/i.test(url));
    const relayConfigured = safeTurn.length > 0 && secret.length >= 32;

    if (relayConfigured) {
      const requestedTtl = Number.parseInt(env.TURN_CREDENTIAL_TTL_SECONDS || '', 10);
      const ttl = Number.isFinite(requestedTtl)
        ? Math.max(300, Math.min(requestedTtl, MAX_TTL_SECONDS))
        : DEFAULT_TTL_SECONDS;
      const expiresAt = Math.floor(timestamp / 1000) + ttl;
      // Coturn REST API: username = expiry:user, password = Base64(HMAC-SHA1(secret, username)).
      const username = `${expiresAt}:${userId}`;
      const credential = crypto.createHmac('sha1', secret).update(username).digest('base64');
      iceServers.push({ urls: safeTurn, username, credential });
      return res.status(200).json({ ice_servers: iceServers, relay_configured: true, expires_at: expiresAt });
    }

    if (!iceServers.length) {
      return res.status(503).json({ message: 'No ICE servers are configured.' });
    }
    return res.status(200).json({
      ice_servers: iceServers,
      relay_configured: false,
      expires_at: null,
      warning: 'TURN is not configured; only direct STUN connectivity is available.',
    });
  });

  return router;
}

module.exports = { createWebRtcIceRouter };
