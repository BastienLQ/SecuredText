#!/usr/bin/env python3

import argparse
import binascii
import io
import shutil
from pathlib import Path
import xml.etree.ElementTree as ElementTree

from fontTools.ttLib import TTFont

parser = argparse.ArgumentParser(
    prog='emoji-extractor',
    description="""Extract emojis from NotoColorEmoji.ttf. Requires FontTools.""")
parser.add_argument(
    '-i', '--input',
    help='the TTF file to parse',
    default='NotoColorEmoji.ttf',
    required=False)
parser.add_argument(
    '-o', '--output',
    help='the png output folder',
    default='output/', required=False)
args = parser.parse_args()

path = Path(args.output)
shutil.rmtree(path, ignore_errors=True)
path.mkdir()

font_xml = io.StringIO()
TTFont(args.input).saveXML(font_xml)
ttx = ElementTree.fromstring(font_xml.getvalue())


def mass_rename(ligature_set_or_emoji):
    cmap = ttx.find('cmap').find('cmap_format_12')
    code = ''
    for map_element in cmap:
        if map_element.attrib['name'].lower() == ligature_set_or_emoji:
            replace_s = '' if len(map_element.attrib['code']) > 4 else '00'
            code = map_element.attrib['code'].replace('0x', replace_s)
            rename('emoji_{}.png'.format(ligature_set_or_emoji),
                   'emoji_u{}.png'.format(code))


def rename(old_name, new_name):
    print('Renaming {} to {}'.format(old_name, new_name))
    try:
        (path / old_name).rename(path / new_name)
    except:
        print('!! Cannot rename {}'.format(old_name))


for element in ttx.find('CBDT').find('strikedata'):
    data = element.find('rawimagedata').text.split()
    name = element.attrib['name'].lower().replace('uni', 'u')
    image_path = path / 'emoji_{}.png'.format(name)
    print('Extracting {}'.format(image_path.name))
    emoji = open(image_path, "wb")
    for char in data:
        hexChar = binascii.unhexlify(char)
        emoji.write(hexChar)
    emoji.close

for ligature_set_xml in ttx.find('GSUB')\
        .find('LookupList')\
        .find('Lookup')\
        .find('LigatureSubst'):
    ligature_set = ligature_set_xml.attrib['glyph'].lower().replace('uni', 'u')
    if ligature_set.startswith('u'):  # TODO: parse missing emojis
        for ligature_xml in ligature_set_xml:
            component = ligature_xml.attrib['components'].lower()\
                .replace(',', '_')\
                .replace('uni', 'u')
            glyph = ligature_xml.attrib['glyph']
            rename('emoji_{}.png'.format(glyph),
                   'emoji_{}_{}.png'.format(ligature_set, component))
    else:
        mass_rename(ligature_set)

emojis = path.glob('*.png')
for emoji in emojis:
    if not emoji.name.startswith('emoji_u'):
        emoji = emoji.with_name(emoji.name.replace('emoji_', '').replace('.png', ''))
        print('Fixing {}...'.format(emoji.name))
        mass_rename(emoji)

# Some flags aren't correctly sorted
trans_table = {
    'fe4e5': '1f1ef_u1f1f5',
    'fe4e6': '1f1fa_u1f1f8',
    'fe4e7': '1f1eb_u1f1f7',
    'fe4e8': '1f1e9_u1f1ea',
    'fe4e9': '1f1ee_u1f1f9',
    'fe4ea': '1f1ec_u1f1e7',
    'fe4eb': '1f1ea_u1f1f8',
    'fe4ec': '1f1f7_u1f1fa',
    'fe4ed': '1f1e8_u1f1f3',
    'fe4ee': '1f1f0_u1f1f7',
}

for old, new in trans_table.items():
    old_path = path / 'emoji_u{}.png'.format(old)
    new_path = path / 'emoji_u{}.png'.format(new)
    print('Renaming incorrect {} to {}'.format(old_path.name, new_path.name))
    old_path.rename(new_path)
