#!/usr/bin/env python3
"""Confere se todo cantor da lista tem estilo em estilos.txt (mesma regra do app).

Uso: python3 tools/conferir_estilos.py
"""
import collections
import re
import sys
import unicodedata

ASSETS = 'app/src/main/assets/'
FEAT = re.compile(r'(?i)\s*\(?(?:\bfeaturing\b|\bfeat\b\.?|ft\.|\bft\b|\bpart\.)\s*.*$')
PARENS = re.compile(r'\s*\([^)]*\)')
SPLIT = re.compile(r'(?i)\s*(?:,|&|\+|/|\bx\b|\be\b|\band\b|\bvs\b\.?|\bwith\b)\s*')
SPECIAL = {'ø': 'o', 'æ': 'ae', 'œ': 'oe', 'ß': 'ss', 'ł': 'l', 'đ': 'd', 'ð': 'd', 'þ': 'th', 'ı': 'i'}
IGNORE = {'the', 'and', 'e'}


def normalize(s):
    n = unicodedata.normalize('NFD', s)
    n = ''.join(c for c in n if not unicodedata.category(c).startswith('M')).lower()
    n = re.sub(r"['’‘`´]", '', n)
    n = ''.join(SPECIAL.get(c, c) for c in n)
    return re.sub(r'[^\w]+|_', ' ', n).strip()


def key(s):
    return ''.join(t for t in normalize(s).split() if t not in IGNORE)


def load_styles():
    styles = {}
    for line in open(ASSETS + 'estilos.txt', encoding='utf-8'):
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        name, _, genres = line.partition('|')
        styles[key(name)] = [g.strip() for g in genres.split(',') if g.strip()]
    return styles


def styles_for(artist, styles):
    base = FEAT.sub('', artist).strip() or artist
    no_parens = PARENS.sub('', base).strip() or base
    for cand in (base, no_parens):
        if key(cand) in styles:
            return styles[key(cand)]
    for part in SPLIT.split(no_parens):
        if part.strip() and key(part) in styles:
            return styles[key(part)]
    return None


def main():
    styles = load_styles()
    counts = collections.Counter()
    missing = collections.Counter()
    for line in open(ASSETS + 'musicas.tsv', encoding='utf-8'):
        artist = line.split('\t')[1]
        g = styles_for(artist, styles)
        if g is None:
            missing[artist] += 1
        else:
            for x in g:
                counts[x] += 1
    for g, n in counts.most_common():
        print(f'{n:5d}  {g}')
    for a, n in missing.most_common():
        print('SEM ESTILO:', a, f'({n})')
    return 1 if missing else 0


if __name__ == '__main__':
    sys.exit(main())
