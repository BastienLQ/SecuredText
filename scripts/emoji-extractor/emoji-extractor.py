#!/usr/bin/env python3

import argparse
import binascii
from time import time
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

# Clean working directory
shutil.rmtree(path, ignore_errors=True)
path.mkdir()

# Load font data
print("Loading font data...")

t0 = time()
font = TTFont(args.input)
font.saveXML('.NotoColorEmoji.ttx')
ttx = ElementTree.parse('.NotoColorEmoji.ttx').getroot()
print("Time:", time() - t0)


def rename_to_resolved_unicode(glyph_name):
    """Use TTF cmap (name <-> Unicode code point) mapping to rename an extracted glyph"""
    glyph_name = str(glyph_name)  # In case it's a path
    cmap = ttx.find('cmap').find('cmap_format_12')
    for map_element in cmap:
        if map_element.attrib['name'].lower() == glyph_name:
            replace_s = '' if len(map_element.attrib['code']) > 4 else '00'
            code = map_element.attrib['code'].replace('0x', replace_s)
            rename_to('emoji_{}.png'.format(glyph_name),
                   'emoji_u{}.png'.format(code))
            break  # Accelerate processing
    else:
        print('Ignoring ' + glyph_name + '...')


def rename_to(old_name, new_name):
    """Rename an extracted glyph"""
    print('Renaming {} to {}'.format(old_name, new_name))
    try:
        (path / old_name).rename(path / new_name)
    except FileNotFoundError as ex:
        print(ex.strerror)


# Extract all emojis
for element in ttx.find('CBDT').find('strikedata'):
    data = element.find('rawimagedata').text.split()
    name = element.attrib['name'].lower().replace('uni', 'u')
    image_path = path / 'emoji_{}.png'.format(name)
    print('Extracting {}'.format(image_path.name))
    emoji = open(str(image_path), "wb")
    for char in data:
        hexChar = binascii.unhexlify(char)
        emoji.write(hexChar)
    emoji.close()

print('*** Fixing names of compound emojis ***')
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
            if (path / 'emoji_{}.png'.format(glyph)).exists():
                rename_to('emoji_{}.png'.format(glyph),
                    'emoji_{}_{}.png'.format(ligature_set, component))
    else:
        rename_to_resolved_unicode(ligature_set)

print('*** Fixing remaining names ***')
emojis = path.glob('*.png')
for emoji in emojis:
    if not emoji.name.startswith('emoji_u'):
        emoji = emoji.with_name(emoji.name.replace('emoji_', '').replace('.png', ''))
        rename_to_resolved_unicode(emoji.name)

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

# Fix these flag names
for old, new in trans_table.items():
    old_name = 'emoji_u{}.png'.format(old)
    new_name = 'emoji_u{}.png'.format(new)
    rename_to(old_name, new_name)
