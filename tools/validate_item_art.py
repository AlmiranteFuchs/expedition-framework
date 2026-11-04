"""Check the shipped inventory family without loading Minecraft."""
import json
import re
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/expeditions'


def validate():
    items=re.findall(r'(?:registerSimpleItem|registerSimpleBlockItem|register)\(\s*"([a-z_]+)"',
                     (ROOT/'src/main/java/com/expeditions/registry/ModItems.java').read_text(encoding='utf-8'))
    for name in items:
        assert (ASSETS/f'models/item/{name}.json').is_file(),f'Missing inventory model: {name}'
    for path in (ASSETS/'textures/item').glob('*.png'):
        with Image.open(path) as im:
            assert im.size==(16,16),f'Non-native sprite: {path.name}'
            if path.stem!='compass_ember':
                assert set(im.convert('RGBA').getchannel('A').tobytes())<={0,255},f'Soft alpha: {path.name}'
            assert im.getbbox(),f'Empty sprite: {path.name}'
    for path in (ASSETS/'models/item').glob('*.json'):
        data=json.loads(path.read_text(encoding='utf-8'))
        for value in data.get('textures',{}).values():
            if value.startswith('expeditions:'):
                assert (ASSETS/'textures'/(value.split(':',1)[1]+'.png')).is_file(),f'Missing texture in {path.name}: {value}'
    for i in range(32):
        model=json.loads((ASSETS/f'models/item/expedition_compass_{i:02d}.json').read_text(encoding='utf-8'))
        assert model['parent']=='expeditions:item/expedition_compass_housing','Compass frame lost its hand pose'
    model=json.loads((ASSETS/'models/item/expedition_compass.json').read_text(encoding='utf-8'))
    assert len(model['overrides'])==33,'Compass angle mapping changed'
    print(f'Validated {len(items)} registered item models, all native item textures and 32 compass frames.')


if __name__=='__main__':validate()
