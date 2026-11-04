"""Native 16px copper/heartwood ward and its effect icon; leaves exported art untouched."""
from pathlib import Path
from PIL import Image, ImageDraw

def generate(root):
    root=Path(root)
    im=Image.new('RGBA',(16,16)); d=ImageDraw.Draw(im)
    d.line([(4,1),(3,4),(5,7),(8,9),(11,7),(13,4),(12,1)],fill='#625b4f')
    d.line([(4,2),(4,4),(6,6)],fill='#c5bba2')
    d.polygon([(7,6),(10,7),(12,10),(10,14),(7,15),(4,12),(5,8)],fill='#433b30')
    d.polygon([(7,7),(10,8),(11,10),(9,13),(7,14),(5,12),(6,9)],fill='#b0a895')
    d.line([(6,9),(6,11),(8,13)],fill='#ece1c7')
    d.line([(8,9),(9,10),(8,12)],fill='#895629',width=2)
    d.point((8,9),fill='#e6af56'); d.point((9,12),fill='#bd7c32')
    im.save(root/'item/disruption_charm.png')
    icon=Image.new('RGBA',(18,18)); d=ImageDraw.Draw(icon)
    d.line([(4,4),(8,2),(13,4),(14,9),(10,14),(7,15),(3,10),(4,4)],fill='#d9cfb8',width=2)
    d.line([(5,12),(12,5)],fill='#dd9c41',width=2)
    d.line([(6,4),(8,6),(7,8),(10,10),(9,13)],fill='#a89e88')
    (root/'mob_effect').mkdir(exist_ok=True)
    icon.save(root/'mob_effect/assimilation_disruption.png')

if __name__=='__main__':
    generate(Path(__file__).resolve().parents[1]/'src/main/resources/assets/expeditions/textures')
