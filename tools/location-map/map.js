import * as maplibregl from 'maplibre-gl';
import { createLayerSync } from '@geolibre/map';
import { DEFAULT_LAYER_STYLE } from '@geolibre/core';
import 'maplibre-gl/dist/maplibre-gl.css';

// Local bundled assets only. Android host supplies a fresh per-view session token.
// This module has no GPS, messaging, geocoder, routing, analytics or generic native bridge.
maplibregl.setWorkerUrl(new URL('./map.worker.js', location.href).href);
// Android WebView can expose a nonzero viewport while percentage document height is zero.
// Pin the local document and map to that viewport before constructing the renderer.
function sizeMapViewport() {
  const width = Math.max(1, Math.round(window.innerWidth));
  const height = Math.max(1, Math.round(window.innerHeight));
  for (const element of [document.documentElement, document.body, document.getElementById('map')]) {
    if (element.style.width !== width + 'px') element.style.width = width + 'px';
    if (element.style.height !== height + 'px') element.style.height = height + 'px';
  }
}
sizeMapViewport();
const map = new maplibregl.Map({
  container: 'map',
  style: 'https://tiles.openfreemap.org/styles/' + (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'positron'),
  center: [0, 20], zoom: 2, attributionControl: {compact: false, customAttribution: []}, maplibreLogo: false,
  // Bound backing-buffer cost on dense phone screens without changing CSS/control size.
  pixelRatio: Math.min(window.devicePixelRatio || 1, 2),
});
const sync = createLayerSync(map);
const mapContainer = document.getElementById('map');
const updateViewport = () => { if (!disposed) { sizeMapViewport(); map.resize(); } };
window.addEventListener('resize', updateViewport);
window.addEventListener('orientationchange', updateViewport);
const resizeObserver = new ResizeObserver(updateViewport);
// Observe after current script initializes the disposed flag.
queueMicrotask(() => { if (!disposed) resizeObserver.observe(mapContainer); });
function clearRenderedEvidence() { mapContainer.dataset.rendered = 'false'; }
clearRenderedEvidence();
map.on('idle', () => {
  if (disposed) return;
  recoverMapStatus();
  const box = mapContainer.getBoundingClientRect();
  const canvas = map.getCanvas().getBoundingClientRect();
  // Evidence for focused runtime tests, never a substitute for inspecting pixels.
  const rendered = !mapHadLoadError && selected && box.width > 100 && box.height > 100 && canvas.width > 100 && canvas.height > 100 && map.isStyleLoaded() && map.areTilesLoaded() && map.queryRenderedFeatures().length > 0;
  mapContainer.dataset.rendered = rendered ? 'true' : 'false';
});
let selected = null;
let session = null;
let ready = false;
let disposed = false;
let buildings3d = false;
let currentStyle = window.matchMedia('(prefers-color-scheme: dark)').matches ? "dark" : "positron";
let mapHadLoadError = false;
let successfulDataSinceError = false;
function normalMapStatus() {
  return buildings3d ? '3D buildings at this zoom where height data exists. Not satellite.' : 'Pan, zoom or tap a pin. No location sending.';
}
function recoverMapStatus() {
  // idle follows completion of the current style/visible tiles. Do not clear on a button click.
  if (disposed || !mapHadLoadError || !successfulDataSinceError || !map.isStyleLoaded() || !map.areTilesLoaded()) return;
  mapHadLoadError = false;
  document.getElementById('status').textContent = normalMapStatus();
}
function valid(lat, lon) { return Number.isFinite(lat) && Number.isFinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180; }
function draw() {
  if (disposed || !ready || !selected) return;
  sync.sync([{
    id: 'chaty-location-pin', name: 'Selected location', type: 'geojson',
    source: {}, visible: true, opacity: 1,
    style: { ...DEFAULT_LAYER_STYLE, fillColor: '#24a892', strokeColor: '#ffffff', strokeWidth: 3, circleRadius: 10 },
    metadata: {}, geojson: { type: 'FeatureCollection', features: [{
      type: 'Feature', id: 'pin', properties: {},
      geometry: { type: 'Point', coordinates: [selected.longitude, selected.latitude] },
    }] },
  }]);
}
function applyBuildings() {
  if (!map.isStyleLoaded()) return;
  for (const layer of map.getStyle().layers ?? []) {
    if (layer.type === 'fill-extrusion') map.setLayoutProperty(layer.id, 'visibility', buildings3d ? 'visible' : 'none');
  }
}
map.on('style.load', () => { applyBuildings(); ready = true; draw(); });
function emit(type, extra = {}) {
  if (disposed || !session) return;
  const value = JSON.stringify({ version: 1, type, session, ...extra });
  // Android installs an origin-restricted WebMessageListener named ChatyMap.
  // No JavascriptInterface and no arbitrary methods are exposed.
  window.ChatyMap?.postMessage(value);
}
window.ChatyGeoLibre = Object.freeze({
  initialize(token, nativeLayers = false, dark = window.matchMedia('(prefers-color-scheme: dark)').matches) {
    if (disposed || session || typeof token !== 'string' || !/^[a-f0-9]{32}$/.test(token)) return false;
    session = token;
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    if (nativeLayers === true) document.querySelector('.map-controls').hidden = true;
    if (ready) emit('ready');
    return true;
  },
  setTheme(dark, token) {
    if (disposed || !session || token !== session || typeof dark !== 'boolean') return false;
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    return true;
  },
  control(action, token) {
    if (disposed || !session || token !== session || !['zoomIn','zoomOut','toggleBuildings'].includes(action)) return false;
    if (action === 'zoomIn') map.zoomIn();
    else if (action === 'zoomOut') map.zoomOut();
    else document.getElementById('view-mode').click();
    return true;
  },
  selectStyle(value, token) {
    if (disposed || !session || token !== session || !['liberty','positron','dark','bright','fiord'].includes(value)) return false;
    selectMapStyle(value); updateLayerSelection(currentStyle); return true;
  },
  dispose() {
    if (disposed) return;
    disposed = true; ready = false; session = null; selected = null;
    resizeObserver.disconnect(); window.removeEventListener('resize', updateViewport); window.removeEventListener('orientationchange', updateViewport); sync.dispose(); map.remove();
  },
  setPin(lat, lon, token) {
    if (disposed || !session || token !== session || !valid(lat, lon)) return false;
    clearRenderedEvidence();
    selected = { latitude: lat, longitude: lon };
    draw(); map.jumpTo({ center: [lon, lat], zoom: 15 });
    return true;
  },
});
map.on('load', () => { ready = true; draw(); emit('ready'); });
map.on('click', event => {
  // Only a trusted physical click can select. Programmatic camera changes never send.
  if (disposed || !session || !event.originalEvent?.isTrusted || !valid(event.lngLat.lat, event.lngLat.lng)) return;
  selected = { latitude: event.lngLat.lat, longitude: event.lngLat.lng };
  draw(); emit('pinSelected', selected);
});
document.getElementById('zoom-in').onclick = () => map.zoomIn();
document.getElementById('zoom-out').onclick = () => map.zoomOut();
document.getElementById('view-mode').onclick = () => {
  buildings3d = !buildings3d;
  if (buildings3d && currentStyle !== 'liberty') { currentStyle = 'liberty'; updateLayerSelection('liberty'); map.setStyle('https://tiles.openfreemap.org/styles/liberty'); }
  // Liberty extrusions start at building zooms. Pitch alone does not reveal buildings.
  map.easeTo({zoom: buildings3d ? Math.max(map.getZoom(), 16) : map.getZoom(), pitch: buildings3d ? 55 : 0, bearing: 0, duration: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 300});
  applyBuildings();
  document.getElementById('view-mode').textContent = buildings3d ? '2D' : '3D';
  document.getElementById('view-mode').setAttribute('aria-pressed', String(buildings3d));
  emit('viewChanged', {buildings: buildings3d});
  if (!mapHadLoadError) document.getElementById('status').textContent = buildings3d ? '3D buildings at this zoom where height data exists. Not satellite.' : '2D map. Nothing is sent.';
};
function selectMapStyle(value) {
  if (!['liberty','positron','dark','bright','fiord'].includes(value)) return;
  if (buildings3d && value !== 'liberty') { buildings3d = false; map.easeTo({pitch:0,bearing:0,duration:0}); document.getElementById('view-mode').textContent='3D'; document.getElementById('view-mode').setAttribute('aria-pressed', 'false'); emit('viewChanged', {buildings: false}); if (!mapHadLoadError) document.getElementById('status').textContent='2D map. Choose Streets for 3D buildings.'; }
  currentStyle = value;
  clearRenderedEvidence();
  map.setStyle('https://tiles.openfreemap.org/styles/' + value);
}
const layerTrigger = document.getElementById('layers-trigger');
const layerMenu = document.getElementById('layers-menu');
const layerOptions = [...layerMenu.querySelectorAll('[data-style]')];
function updateLayerSelection(value) {
  emit('styleChanged', {style: value});
  for (const option of document.querySelectorAll('#layers-menu [data-style]')) option.setAttribute('aria-checked', String(option.dataset.style === value));
}
function closeLayers(returnFocus = false) {
  layerMenu.hidden = true; layerTrigger.setAttribute('aria-expanded', 'false');
  if (returnFocus) layerTrigger.focus();
}
layerTrigger.onclick = () => {
  if (!layerMenu.hidden) { closeLayers(true); return; }
  layerMenu.hidden = false; layerTrigger.setAttribute('aria-expanded', 'true');
  // Prefer below the icon. On very short screens, shift upward only enough to stay visible.
  layerMenu.style.top = '56px';
  const bounds = layerMenu.getBoundingClientRect();
  if (bounds.bottom > innerHeight - 8) layerMenu.style.top = (56 - (bounds.bottom - innerHeight + 8)) + 'px';
  layerOptions.find(option => option.getAttribute('aria-checked') === 'true')?.focus();
};
for (const option of layerOptions) option.onclick = () => {
  selectMapStyle(option.dataset.style); updateLayerSelection(currentStyle); closeLayers(true);
};
layerMenu.onkeydown = event => {
  if (event.key === 'Escape') { event.preventDefault(); closeLayers(true); }
  if (['ArrowDown','ArrowUp','Home','End'].includes(event.key)) {
    event.preventDefault(); const current = layerOptions.indexOf(document.activeElement);
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? layerOptions.length - 1 : (current + (event.key === 'ArrowDown' ? 1 : -1) + layerOptions.length) % layerOptions.length;
    layerOptions[next].focus();
  }
  if (event.key === 'Tab') closeLayers();
};
document.addEventListener('pointerdown', event => { if (!event.target.closest('.layer-picker')) closeLayers(); });
map.on('error', () => { clearRenderedEvidence(); successfulDataSinceError = false; mapHadLoadError = true; document.getElementById('status').textContent = 'Map unavailable. Check your internet connection.'; });
map.on('style.load', () => { if (mapHadLoadError) successfulDataSinceError = true; });
map.on('sourcedata', event => { if (mapHadLoadError && event.sourceDataType === 'content' && event.isSourceLoaded) successfulDataSinceError = true; });
