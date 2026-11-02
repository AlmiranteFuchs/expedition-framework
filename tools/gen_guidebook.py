"""Build the Patchouli guide and its server discovery gates from our chapter source."""
import json
import re
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
BOOK = ROOT / 'assets/expeditions/patchouli_books/field_guide'
CHAPTERS = json.loads((ROOT / 'assets/expeditions/journal/chapters.json').read_text(encoding='utf-8'))


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


groups = [
    ('beginnings', 'First roots', 'expeditions:expedition_field_journal',
     'Begin with a sample of the Bloom. Harvest dormant traces for flakes, then craft the instruments that let you return home.',
     ['first_steps', 'essence', 'terminal', 'wayfinding']),
    ('expeditions', 'Beyond the threshold', 'expeditions:expedition_terminal',
     'Deploy, follow the compass, secure each checkpoint and extract. These pages answer as you put the instruments to use.',
     ['checkpoints', 'cargo', 'defense', 'extraction', 'preparation']),
    ('bloom', 'The living country', 'expeditions:assimilation_heart',
     'Approach a heart to observe the Bloom. Exposure or a blood sample opens the assay; awareness reveals what the land has learned.',
     ['bloom', 'blood', 'pressure']),
    ('thicket', 'Under the bark', 'expeditions:attunement_sigil',
     'File surveys, recover the sealed cargo, then follow the Charter into the thicket. Some truths require more than a sample.',
     ['guardian', 'liberation']),
]
icons = {'first_steps':'expedition_field_journal', 'bloom':'assimilation_heart',
         'essence':'assimilation_essence', 'terminal':'expedition_terminal',
         'wayfinding':'expedition_compass', 'checkpoints':'expedition_checkpoint',
         'cargo':'expedition_artifact', 'defense':'expedition_checkpoint',
         'blood':'field_assay_kit', 'pressure':'assimilation_heart',
         'extraction':'expedition_exit', 'guardian':'assimilation_heart', 'liberation':'attunement_sigil', 'preparation':'field_ward'}

write(ROOT / 'data/expeditions/patchouli_books/field_guide/book.json', {
    'name':'The Charter', 'landing_text':'The frontier remembers.$(br2)'
    'Explore to unlock entries.$(br2)Browse for crafting and field notes.',
    'subtitle':'Field notes', 'version':'0', 'use_resource_pack':True,
    'nameplate_color':'dfcf91',
    'dont_generate_book':True, 'custom_book_item':'expeditions:expedition_field_journal',
    'book_texture':'patchouli:textures/gui/book_green.png', 'model':'expeditions:expedition_field_journal',
    'use_blocky_font':True, 'show_progress':True, 'show_toasts':False,
    'text_color':'393126', 'header_color':'483821', 'link_color':'915627',
    'link_hover_color':'bd7a32', 'progress_bar_color':'ac783b', 'pause_game':False,
})


def chunks(body, limit=230):
    # Deliberately short leaves leave room for item spotlights and readable type.
    for paragraph in body.split('\n\n'):
        yield from textwrap.wrap(paragraph.replace('\n', '$(br)'), limit,
                                 break_long_words=False, break_on_hyphens=False)


for order, (group, name, icon, description, chapters) in enumerate(groups):
    write(BOOK / f'en_us/categories/{group}.json', {
        'name':name, 'description':description, 'icon':icon, 'sortnum':order,
    })
    for index, key in enumerate(chapters):
        chapter = CHAPTERS[key]
        name = re.sub(r'^[IVX]+\. ', '', chapter['title'])
        pages = []
        for source in chapter['pages']:
            for piece in chunks(source['body']):
                pages.append({'type':'patchouli:text', 'title':source['heading'], 'text':piece})
        pages[0] = {'type':'patchouli:spotlight', 'item':'expeditions:' + icons[key],
                    'text':pages[0]['text'], 'title':name}
        if key in ('first_steps', 'essence'):
            pages.append({'type':'patchouli:crafting', 'recipe':'expeditions:expedition_field_journal',
                          'text':'Your discoveries belong to you. A replacement book remembers them.'})
        if key in ('essence', 'terminal'):
            pages.append({'type':'patchouli:crafting', 'recipe':'expeditions:expedition_terminal',
                          'text':'Place it facing a safe space. Its portal opens above it.'})
        if key == 'essence':
            for machine in ['dormant_knot','stillness_dynamo','essence_condenser','living_heart_engine','severance_core','severance_generator']:
                pages.append({'type':'patchouli:crafting','recipe':'expeditions:'+machine})
            pages.append({'type':'patchouli:crafting', 'recipe':'expeditions:field_assay_kit',
                          'text':'Inspect a sealed sample with use. Sneak-use to draw a fresh, nonlethal sample.'})
            pages.append({'type':'patchouli:crafting', 'recipe':'expeditions:attunement_sigil',
                          'text':'Shapeless. Use the sigil against a terminal; proof is not consumed.'})
        if key == 'preparation':
            for equipment in ['resonance_cell','field_ward','survey_lens']:
                pages.append({'type':'patchouli:crafting','recipe':'expeditions:'+equipment})
        if key == 'guardian':
            pages.append({'type':'patchouli:entity', 'entity':'expeditions:rootbound_guardian',
                          'scale':0.55, 'text':'Read the raised arms. Leave the marked ground before it strikes.'})
        if key == 'blood':
            pages.append({'type':'patchouli:crafting', 'recipe':'expeditions:field_assay_kit',
                          'text':'The journal teaches the assay; the kit holds the blood and measured result.'})
        record = {'first_steps':0, 'pressure':1}.get(key)
        if record is not None:
            pages.append({'type':f'expeditions:record_{record}'})
            write(BOOK / f'en_us/templates/record_{record}.json', {'components':[
                {'type':'patchouli:custom', 'class':'com.expeditions.client.JournalRecordComponent',
                 'x':0, 'y':0, 'record':record}]})
        data = {'name':name, 'icon':'expeditions:' + icons[key], 'category':'expeditions:' + group,
                'sortnum':index, 'pages':pages}
        if key != 'first_steps':
            data['advancement'] = 'expeditions:journal/' + key
        write(BOOK / f'en_us/entries/{key}.json', data)
        write(ROOT / f'data/expeditions/advancement/journal/{key}.json', {
            'criteria':{'discovered':{'trigger':'minecraft:impossible'}},
            'requirements':[['discovered']],
        })

# An always-readable trail of hints, without exposing locked chapter bodies.
write(BOOK / 'en_us/entries/discovery_trail.json', {
    'name':'Unwritten pages', 'icon':'minecraft:feather', 'category':'expeditions:beginnings',
    'sortnum':10, 'pages':[
        {'type':'patchouli:text', 'title':re.sub(r'^[IVX]+\. ', '', chapter['title']),
         'text':chapter['unlock_hint']}
        for chapter in CHAPTERS.values() if 'unlock_hint' in chapter],
})
print(f'Generated {len(groups)} categories, {len(CHAPTERS)+1} entries, live record templates and {len(CHAPTERS)} discovery gates.')
