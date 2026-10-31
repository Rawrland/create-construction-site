import sys
from PIL import Image, ImageDraw, ImageFont
D='out/'; SC=4
def t(n): return Image.open(D+n+'.png').convert('RGBA')
def surface(name, w=3, h=2):
    """Like ToolPainter: fill everywhere, the framed texture's 2-px border only around the outside."""
    fill, framed = t(name+'_fill'), t(name)
    img = Image.new('RGBA', (16*w, 16*h))
    for i in range(w):
        for j in range(h):
            img.paste(fill, (16*i, 16*j))
    W, H = 16*w, 16*h
    for x in range(W):
        for y in range(H):
            fx = x if x < 2 else (x-W+16 if x >= W-2 else None)
            fy = y if y < 2 else (y-H+16 if y >= H-2 else None)
            if fx is not None or fy is not None:
                sx = fx if fx is not None else 2 + x % 12
                sy = fy if fy is not None else 2 + y % 12
                img.putpixel((x, y), framed.getpixel((sx, sy)))
    return img
def tiled(name, w=3, h=2, bg=None):
    img = Image.new('RGBA', (16*w, 16*h), bg or (0,0,0,0))
    for i in range(w):
        for j in range(h):
            img.alpha_composite(t(name), (16*i, 16*j))
    return img
cells=[]
dyes=['white','light_gray','gray','black','brown','red','orange','yellow','lime','green','cyan','light_blue','blue','purple','magenta','pink']
cells.append(('bare (brass)', surface('brass')))
for d in dyes: cells.append((d.replace('_',' '), surface('painted_steel_'+d)))
cells.append(('polished iron (bare edges)', surface('polished_iron')))
cells.append(('dark steel (pins, hinges)', surface('dark_steel')))
cells.append(('tread plate', tiled('tread_plate')))
cells.append(('hazard yellow-black', tiled('hazard_stripes')))
cells.append(('hazard red-white', tiled('hazard_stripes_red_white')))
cells.append(('corrugated sheet (brass)', tiled('corrugated_brass')))
for d in dyes: cells.append(('corrugated '+d.replace('_',' '), tiled('corrugated_'+d)))
cells.append(('grating (over grass)', tiled('grating', bg=(96,140,60,255))))
cols=6; cw, ch = 48*SC+20, 32*SC+34
rows=(len(cells)+cols-1)//cols
sheet=Image.new('RGBA',(cols*cw+20, rows*ch+20),(38,38,42,255))
dr=ImageDraw.Draw(sheet)
try: font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',16)
except: font=ImageFont.load_default()
for k,(label,img) in enumerate(cells):
    x=20+(k%cols)*cw; y=20+(k//cols)*ch
    sheet.alpha_composite(img.resize((img.width*SC,img.height*SC),Image.NEAREST),(x,y))
    dr.text((x,y+img.height*SC+4),label,fill=(230,230,230,255),font=font)
sheet.convert('RGB').save(sys.argv[1])
