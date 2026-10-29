#!/usr/bin/env python3
"""Play Store graphics from the launcher-icon artwork (tools/icon/generate_icon.py).

- icon-512.png: 512x512 32-bit PNG, full-bleed square (Play applies its own mask)
- feature-graphic.png: 1024x500 24-bit PNG, icon + app name + tagline, en and he
No Telegram logo or trademark artwork. Needs google-chrome (headless), Pillow and the Rubik font.
Usage: python3 tools/play-assets/generate_store_graphics.py [out_dir]   (default docs/play-assets)
"""
import os, subprocess, sys, tempfile
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "icon"))
import generate_icon as ic  # noqa: E402
from PIL import Image  # noqa: E402

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ic.ROOT, "docs/play-assets")
TAGLINES = {
    "en": ("Your Telegram channels, read aloud", "Chat by chat, in each chat's own language"),
    "he": ("הערוצים והצ׳אטים שלך, בהקראה", "צ׳אט אחרי צ׳אט, כל אחד בשפה שלו"),
}


def icon_svg(px, rounded=0):
    """The launcher icon (background + foreground) as a full-bleed square; [rounded] = corner radius in px."""
    glyph = (f'<path transform="translate(0,{ic.GLYPH_DY}) translate(54,54) scale({ic.GLYPH_SCALE}) translate(-54,-54)" '
             f'fill="#fff" fill-rule="evenodd" d="{ic.GLYPH}"/>'
             f'<path fill="#fff" fill-rule="evenodd" d="{ic.RIBBON} {ic.TEXT}"/>')
    r = rounded * 72 / px
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{px}" height="{px}" viewBox="18 18 72 72">'
            f'<defs><linearGradient id="ig" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{ic.BLUE_TOP}"/>'
            f'<stop offset="1" stop-color="{ic.BLUE_BOTTOM}"/></linearGradient>'
            f'<clipPath id="ic"><rect x="18" y="18" width="72" height="72" rx="{r}"/></clipPath></defs>'
            f'<g clip-path="url(#ic)"><rect width="108" height="108" fill="url(#ig)"/>{glyph}</g></svg>')


def shot(html, w, h, path):
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False) as f:
        f.write(html)
    try:
        subprocess.run(["google-chrome", "--headless=new", "--no-sandbox", "--disable-gpu", "--hide-scrollbars",
                        "--force-device-scale-factor=1", "--default-background-color=00000000",
                        f"--window-size={w},{h}", f"--screenshot={path}", "file://" + f.name],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    finally:
        os.remove(f.name)


def feature_html(lang):
    rtl = lang == "he"
    tag, sub = TAGLINES[lang]
    waves = "".join(
        f'<circle cx="{"170" if rtl else "854"}" cy="250" r="{r}" fill="none" stroke="#fff" stroke-opacity="{o}" stroke-width="14"/>'
        for r, o in [(150, .07), (230, .05), (310, .035), (390, .025)])
    return f"""<html dir="{'rtl' if rtl else 'ltr'}"><head><meta charset="utf-8"><style>
body{{margin:0;width:1024px;height:500px;overflow:hidden;font-family:'Rubik','Noto Sans',sans-serif;
 background:linear-gradient(135deg,{ic.BLUE_TOP} 0%,#2496D6 45%,{ic.BLUE_BOTTOM} 100%);color:#fff;position:relative}}
svg.bg{{position:absolute;left:0;top:0}}
.row{{position:absolute;inset:0;display:flex;align-items:center;gap:56px;padding:0 72px}}
.icon{{flex:none;filter:drop-shadow(0 18px 30px rgba(0,40,80,.35));border-radius:64px;line-height:0}}
h1{{margin:0;font-size:84px;font-weight:700;line-height:1.08;letter-spacing:-.5px}}
.kicker{{margin-top:8px;font-size:28px;font-weight:500;opacity:.85}}
.tag{{margin-top:22px;white-space:nowrap;font-size:{38 if rtl else 32}px;font-weight:500;opacity:.97}}
.sub{{margin-top:10px;font-size:26px;font-weight:400;opacity:.8}}
</style></head><body><svg class="bg" width="1024" height="500">{waves}</svg>
<div class="row"><div class="icon">{icon_svg(300, rounded=64)}</div>
<div><h1 dir="ltr" style="text-align:{'right' if rtl else 'left'}">Narri</h1>
<div class="kicker" dir="ltr" style="text-align:{'right' if rtl else 'left'}">Unofficial Telegram TTS</div>
<div class="tag">{tag}</div><div class="sub">{sub}</div></div></div></body></html>"""


def main():
    for lang in ("en", "he"):
        d = os.path.join(OUT, lang)
        os.makedirs(d, exist_ok=True)
        icon = os.path.join(d, "icon-512.png")
        shot(f"<html><body style='margin:0'>{icon_svg(512)}</body></html>", 512, 512, icon)
        Image.open(icon).convert("RGBA").save(icon)  # 32-bit PNG
        fg = os.path.join(d, "feature-graphic.png")
        shot(feature_html(lang), 1024, 500, fg)
        Image.open(fg).convert("RGB").save(fg)  # no alpha, as Play requires
    print("ok", OUT)


if __name__ == "__main__":
    main()
