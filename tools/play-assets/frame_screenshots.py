#!/usr/bin/env python3
"""Frames the Robolectric screenshots (build/play-screenshots) with a caption banner for Play.

Input: build/play-screenshots/<lang>/NN-name.png (1080x1920, from `./gradlew testDebugUnitTest -Pscreenshots`)
Output: docs/play-assets/<lang>/screenshots/NN-name.png (framed, 1080x1920) and .../screenshots/raw/ (as rendered)
Usage: python3 tools/play-assets/frame_screenshots.py [in_dir] [out_dir]
"""
import base64, os, shutil, sys
sys.path.insert(0, os.path.dirname(__file__))
import generate_store_graphics as g  # noqa: E402
from PIL import Image  # noqa: E402

IN = sys.argv[1] if len(sys.argv) > 1 else os.path.join(g.ic.ROOT, "build/play-screenshots")
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(g.ic.ROOT, "docs/play-assets")
CAPTIONS = {
    "en": {
        "01-home": "Pick your unread chats, press Play",
        "02-player": "Every message read aloud in a natural voice",
        "03-up-next": "See what's next and jump ahead",
        "04-home-playing": "Keep browsing while it reads",
        "05-voice-settings": "Offline system voice or natural online voices",
        "06-about": "Unofficial, private, and open about it",
    },
    "he": {
        "01-home": "בוחרים צ׳אטים ולוחצים על השמעה",
        "02-player": "כל הודעה מוקראת בקול טבעי",
        "03-up-next": "רואים מה הבא וקופצים קדימה",
        "04-home-playing": "ממשיכים לגלוש בזמן ההקראה",
        "05-voice-settings": "קול המערכת בלי אינטרנט, או קולות טבעיים ברשת",
        "06-about": "לא רשמי, פרטי ושקוף",
    },
}
W, H, SHOT_H = 1080, 1920, 1500


def framed_html(lang, caption, png):
    data = base64.b64encode(open(png, "rb").read()).decode()
    shot_w = round(SHOT_H * 1080 / 1920)
    return f"""<html dir="{'rtl' if lang == 'he' else 'ltr'}"><head><meta charset="utf-8"><style>
body{{margin:0;width:{W}px;height:{H}px;overflow:hidden;font-family:'Rubik','Noto Sans',sans-serif;color:#fff;
 background:linear-gradient(160deg,{g.ic.BLUE_TOP} 0%,#2496D6 50%,{g.ic.BLUE_BOTTOM} 100%)}}
.cap{{position:absolute;top:0;left:60px;right:60px;height:{H - SHOT_H - 60}px;display:flex;align-items:center;
 justify-content:center;text-align:center;font-size:64px;font-weight:600;line-height:1.15}}
img{{position:absolute;bottom:0;left:{(W - shot_w) // 2}px;width:{shot_w}px;height:{SHOT_H}px;
 border-radius:44px 44px 0 0;box-shadow:0 -10px 50px rgba(0,30,60,.35);border:10px solid #10334a;border-bottom:none}}
</style></head><body><div class="cap">{caption}</div><img src="data:image/png;base64,{data}"></body></html>"""


def main():
    for lang, captions in CAPTIONS.items():
        out = os.path.join(OUT, lang, "screenshots")
        raw = os.path.join(out, "raw")
        os.makedirs(raw, exist_ok=True)
        for name, caption in captions.items():
            src = os.path.join(IN, lang, name + ".png")
            if not os.path.exists(src):
                raise SystemExit(f"missing {src}: run ./gradlew testDebugUnitTest -Pscreenshots first")
            Image.open(src).convert("RGB").save(os.path.join(raw, name + ".png"))
            dst = os.path.join(out, name + ".png")
            g.shot(framed_html(lang, caption, src), W, H, dst)
            Image.open(dst).convert("RGB").save(dst)
    print("ok", OUT)


if __name__ == "__main__":
    main()
