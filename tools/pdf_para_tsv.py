#!/usr/bin/env python3
"""Converte a lista de karaokê em PDF para o arquivo usado pelo app.

Uso:
    pdftotext -layout LISTA.pdf lista.txt
    python3 tools/pdf_para_tsv.py lista.txt app/src/main/assets/musicas.tsv

Saída: uma linha por música, separada por TAB:
    NUMERO  CANTOR  MUSICA  ATT
"""
import re
import sys

# Linha normal: colunas separadas por 2+ espaços.
LINHA = re.compile(r'^\s*(\S+)\s+(.+?)\s{2,}(\d{4,6})(?:\s+(.+?))?\s*$')
# Cantor muito longo encostado no número (ex.: "...BLACKPINK26368    SG").
LINHA_COLADA = re.compile(r'^\s*(\S+)\s+(.+?)\s*(\d{5})(?:\s+(.+?))?\s*$')
IGNORAR = re.compile(r'HD-OK|NÚMERO|Página \d+ de')


def converter(entrada, saida):
    linhas, ruins = [], []
    with open(entrada, encoding='utf-8') as f:
        for bruta in f:
            s = bruta.rstrip('\n')
            if not s.strip() or IGNORAR.search(s):
                continue
            m = LINHA.match(s) or LINHA_COLADA.match(s)
            if not m:
                ruins.append(s)
                continue
            att, cantor, numero, musica = m.groups()
            linhas.append((numero, cantor.strip(), (musica or '').strip(), att))
    with open(saida, 'w', encoding='utf-8', newline='\n') as f:
        for row in linhas:
            f.write('\t'.join(row) + '\n')
    print(f'{len(linhas)} músicas gravadas em {saida}', file=sys.stderr)
    for r in ruins:
        print('NÃO RECONHECIDA:', r, file=sys.stderr)
    return 1 if ruins else 0


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    sys.exit(converter(sys.argv[1], sys.argv[2]))
