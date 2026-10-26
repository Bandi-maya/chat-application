import { build } from 'esbuild';
import { mkdirSync, copyFileSync } from 'node:fs';
const out = '../../app/src/main/assets/location-map';
mkdirSync(out, {recursive:true});
await build({entryPoints:['map.js'],bundle:true,minify:true,format:'iife',outfile:out+'/map.js',metafile:true});
copyFileSync('node_modules/maplibre-gl/dist/maplibre-gl-worker.mjs',out+'/map.worker.js');
copyFileSync('index.html',out+'/index.html');
copyFileSync('THIRD_PARTY_NOTICES.txt',out+'/THIRD_PARTY_NOTICES.txt');
