"""Read installed mods' original biome spawn tables for live-test assertions."""
import json
import sys
import zipfile

biomes = {}
for jar in sys.argv[1:]:
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            parts = name.split('/')
            if len(parts) >= 5 and parts[0] == 'data' and parts[2:4] == ['worldgen', 'biome'] and name.endswith('.json'):
                biome_id = parts[1] + ':' + '/'.join(parts[4:])[:-5]
                biomes[biome_id] = json.loads(archive.read(name)).get('spawners', {})
print(json.dumps(biomes))
