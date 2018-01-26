#!/usr/bin/env python3

import argparse
import math
from pathlib import Path
import xml.etree.ElementTree as ElementTree

from PIL import Image

parser = argparse.ArgumentParser(
    prog='gen-sprite',
    description="""Generate sprites from extracted emojis.""")
parser.add_argument(
    '-e', '--emojis',
    help='folder where emojis are stored',
    default='output/',
    required=False)
parser.add_argument(
    '-i', '--xml',
    help='XML containing emojis map',
    default='emoji-categories.xml',
    required=False)
parser.add_argument(
    '-s', '--size',
    help='Maximum number of emojis per line',
    default='15',
    required=False)
parser.add_argument(
    '-r', '--resize',
    help='Maximum width for sprites',
    default='1530',
    required=False)
args = parser.parse_args()

emoji_path = Path(args.emojis)

xml = ElementTree.parse(args.xml).getroot()
parsed_items = []

for group in xml:
    group_name = group.attrib['name']
    emojis = []
    output = open('{}.txt'.format(group_name), 'w')
    for item in group:
        emoji = item.text.replace(',', '_u').lower()
        emoji_file = emoji_path / 'emoji_u{}.png'.format(emoji)
        if '|' not in emoji and emoji not in parsed_items and emoji_file.is_file():
            parsed_items.append(emoji)
            emojis.append(emoji_file)
            final_code_points = []
            emoji_code_point = '\\U' + emoji.replace('_u', '\\U')
            for point in emoji_code_point.split('\\U'):
                point = point.replace('\\U', '')
                if len(point) > 0:
                    if len(point) < 8:
                        point = "0" * (8 - len(point)) + point
                    final_code_points.append(point)
            char = '\\U' + '\\U'.join(final_code_points)
            output.write(char + '\n')
    images = [Image.open(filename) for filename in emojis]
    output.close()

    if len(images) > 0:
        print("Generating sprite for {}".format(group_name))
        master_width = 128 * int(args.size)
        lines = math.ceil(len(images) / float(args.size))
        master_height = 128 * int(lines)
        master = Image.new(
            mode='RGBA',
            size=(master_width, master_height),
            color=(0, 0, 0, 0)
        )

        offset = -1
        for count, image in enumerate(images):
            location = (128 * count) % master_width
            if location == 0:
                offset += 1
            master.paste(image, (location, 128 * offset))
        ratio = float(master_width) / float(args.resize)
        new_height = math.ceil(master_height / ratio)
        master = master.resize((int(args.resize), int(new_height)))
        master.save(group_name + '.png', 'PNG')
    else:
        print('Ignoring {}...'.format(group_name))
