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
        g = [x.strip() for x in genres.split(',') if x.strip()]
        k = key(name)
        if k in styles and styles[k] != g:
            print(f'AVISO: "{name.strip()}" aparece com estilos diferentes: {styles[k]} e {g}')
        styles[k] = g
    return styles


def styles_for(artist, styles):
    """Estilos do cantor. Em colaborações ("X e Y & Z, W") procura o trecho mais longo
    que seja um cantor conhecido, começando pelo primeiro nome: assim "Bruno e Marrone &
    Chitãozinho e Xororó" acha a dupla "Bruno e Marrone", e não "Bruno"."""
    base = FEAT.sub('', artist).strip() or artist
    no_parens = PARENS.sub('', base).strip() or base
    for cand in (base, no_parens):
        if key(cand) in styles:
            return styles[key(cand)]
    keys = [key(p) for p in SPLIT.split(no_parens) if p.strip()]
    for i in range(len(keys)):
        for j in range(len(keys) - 1, i - 1, -1):
            k = ''.join(keys[i:j + 1])
            if k in styles:
                return styles[k]
    return None


def main():
    styles = load_styles()
    status = 0
    for lista in ('internacional', 'nacional'):
        counts = collections.Counter()
        missing = collections.Counter()
        for line in open(ASSETS + lista + '.tsv', encoding='utf-8'):
            artist = line.split('\t')[1]
            g = styles_for(artist, styles)
            if g is None:
                missing[artist] += 1
            else:
                for x in g:
                    counts[x] += 1
        print(f'== {lista}: ' + ', '.join(f'{g} {n}' for g, n in counts.most_common()))
        for a, n in missing.most_common():
            print('SEM ESTILO:', a, f'({n})')
            status = 1
    return status


if __name__ == '__main__':
    sys.exit(main())
