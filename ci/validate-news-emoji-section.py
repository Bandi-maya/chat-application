from pathlib import Path
import hashlib,json
r=Path('.');src=Path('app/src/main/assets/emoji/news');old=json.loads(Path('ci/news-emoji-assets.json').read_text())
for name,h in old.items():
 p=src/name
 assert p.is_file() and hashlib.sha256(p.read_bytes()).hexdigest()==h,name
assert len(list((src/'animated').glob('*.webp')))==100
assert len(list((src/'posters').glob('*.png')))==100
s=Path('app/src/main/java/com/example/chat/core/stickers/BundledStickerPacks.kt').read_text();assert 'NewsStickerPack' not in s
s=Path('app/src/main/kotlin/com/example/chat/ui/auth/LoginScreen.kt').read_text();assert 'capsule = true' in s
s=Path('app/src/main/kotlin/com/example/chat/ui/chats/detail/ChatyEmojiPicker.kt').read_text();assert 'NewsCustomEmojiSection' in s and 'CustomEmojiPackStrip' in s
print('NEWS_EMOJI_100_ASSETS_OK; NEWS_NOT_STICKER; KOTLIN_CAPSULE_LOGIN_PRESENT')
