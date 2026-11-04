"""Native field-kit sprites and a compact heartwood ward; no external bitmap dependency."""
from pathlib import Path
from PIL import Image, ImageDraw
from stillness_art import item, write, PALE, METAL, COPPER, AMBER


def gen():
    def cell(d):
        d.polygon([(5,0),(10,0),(12,3),(12,12),(10,15),(4,15),(2,12),(2,4)], fill=METAL[0])
        d.rectangle((4,3,10,12), fill=PALE[2]);d.line((4,4,4,11), fill=PALE[4])
        d.line([(6,4),(5,8),(6,11)], fill=PALE[1])
        for y in [2,12]:
            d.rectangle((3,y,11,y+1), fill=COPPER[1]);d.line((4,y,9,y), fill=COPPER[3])
        d.rectangle((7,4,9,10), fill=METAL[0]);d.line((8,5,8,9), fill=AMBER[2]);d.point((8,5), fill=AMBER[4])
        d.rectangle((5,0,9,1), fill=COPPER[2]);d.point((5,0), fill=COPPER[4])
    def lens(d):
        d.line((3,12,0,15), fill=METAL[0], width=3);d.line((3,12,1,14), fill=COPPER[2])
        d.polygon([(7,0),(12,1),(15,5),(15,9),(11,13),(6,13),(2,9),(2,4)], fill=METAL[0])
        d.polygon([(7,1),(12,2),(14,5),(14,9),(11,12),(6,12),(3,9),(3,4)], fill=COPPER[2])
        d.polygon([(7,3),(11,3),(12,5),(12,9),(10,10),(6,10),(5,8),(5,5)], fill=PALE[1])
        d.line([(7,4),(6,5),(6,7)], fill=PALE[5]);d.polygon([(9,4),(11,7),(9,10),(7,7)], fill=AMBER[1]);d.line((9,5,9,8), fill=AMBER[3])
        d.line((5,2,9,1), fill=COPPER[4]);d.point((12,11), fill=COPPER[0])
    item('resonance_cell',cell);item('survey_lens',lens)
    textures={k:'expeditions:block/'+v for k,v in [('wood','machine_bone'),('end','wood_end'),('metal','machine_iron'),('copper','machine_copper'),('heart','machine_knot')]}
    textures['particle']=textures['wood']
    def box(a,b,t):
        return {'from':a,'to':b,'faces':{f:{'texture':'#'+t,'uv':[0,0,16,16]} for f in ['up','down','north','south','east','west']}}
    # A pegged three-foot instrument with space around its amber sample.
    parts=[box([3,0,3],[13,2,13],'metal'),box([6,2,6],[10,4,10],'copper'),
           box([6,4,6],[7,12,7],'wood'),box([9,4,9],[10,12,10],'wood'),
           box([7,5,7],[9,10,9],'heart'),box([4,12,4],[12,14,12],'wood'),
           box([4,12,4],[12,12.5,12],'copper'),box([7,14,7],[9,15,9],'end')]
    for lit in [False,True]:
        tex=dict(textures)
        if lit:tex['heart']='expeditions:block/machine_heart'
        write(Path('models/block/field_ward'+('_lit' if lit else '')+'.json'),{'parent':'minecraft:block/block','textures':tex,'elements':parts,'render_type':'minecraft:cutout'})
    write(Path('blockstates/field_ward.json'),{'variants':{'lit=false':{'model':'expeditions:block/field_ward'},'lit=true':{'model':'expeditions:block/field_ward_lit'}}})
    write(Path('models/item/field_ward.json'),{'parent':'expeditions:block/field_ward_lit'})


if __name__=='__main__':
    gen()
