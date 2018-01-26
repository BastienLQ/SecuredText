#!/usr/bin/env python3

import argparse
from pathlib import Path

from PIL import Image

parser = argparse.ArgumentParser(
    prog='emoji-extractor',
    description="""Resize extracted emojis to 128x128.""")
parser.add_argument(
    '-e', '--emojis',
    help='folder where emojis are stored',
    default='output/',
    required=False)
args = parser.parse_args()

path = Path(args.emojis)

for image_path in path.iterdir():
    try:
        print('Cropping {}...'.format(image_path.name))
        image = Image.open(image_path)
        width, height = image.size
        box = (4, 0, width - 4, height)
        crop = image.crop(box)
        crop.save(image_path)
    except:
        print('Cannot crop {}...'.format(image_path.name))
