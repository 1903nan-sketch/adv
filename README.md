# Busca Karaokê

App Android para procurar músicas da **Lista Completa Internacional** (HD-OKÊ) pelo
**nome da música** ou pelo **nome do cantor** e ver o número para digitar no aparelho.

<p>
  <img src="docs/tela-inicial.png" width="240" alt="Tela inicial">
  <img src="docs/busca-cantor.png" width="240" alt="Busca por cantor">
  <img src="docs/busca-sem-acento.png" width="240" alt="Busca sem acento">
  <img src="docs/estilo-romanticas.png" width="240" alt="Filtro por estilo">
  <img src="docs/modo-escuro.png" width="240" alt="Modo escuro">
</p>

## Instalar

1. Baixe o arquivo [`apk/BuscaKaraoke.apk`](apk/BuscaKaraoke.apk) no celular.
2. Abra o arquivo. Se o Android pedir, permita **"instalar apps desconhecidos"** para o
   navegador ou gerenciador de arquivos.
3. Funciona sem internet, no Android 5.0 ou mais novo.

## Como usar

- Digite na caixa de busca. Os resultados aparecem enquanto você digita.
- Filtros: **Tudo** (música, cantor ou número), **Música** ou **Cantor**.
- Não precisa de acento: `beyonce` acha "Beyoncé", `cancao do mar` acha "CANÇÃO DO MAR".
  Com acento também funciona.
- Não importa maiúscula/minúscula ou apóstrofo: `dont trust me` encontra
  "DON'T TRUST ME", `acdc` encontra "AC/DC".
- Pode misturar música e cantor: `hello adele`. Se o filtro **Música** ou **Cantor** não
  achar nada, o app procura nos dois sozinho e avisa.
- Aceita pequenos erros de digitação: `bohemian rapsody`, `bom jovi`, `whitney huston`.
- Aceita nomes escritos "pelo som": `xakira`, `uitney huston`, `ed xiran`, `bionce`.
- Filtre por **estilo musical** na segunda fileira de botões (role para o lado):
  Pop, Rock, Românticas, R&B e Soul, Dance, Rap e Hip-Hop, Latinas, Country,
  Jazz e Blues, Filmes e Musicais, Reggae, Italianas e Europeias, K-pop.
  O estilo funciona junto com a busca (ex.: estilo Românticas + `love`). Se a música
  não for do estilo marcado, ela aparece mesmo assim, com o aviso "Nada em …".
- A versão instalada aparece no topo da tela.
- Toque numa música para **copiar o número**.

## Atualizar a lista

A lista fica em `app/src/main/assets/musicas.tsv` (3.920 músicas extraídas do PDF).
Para gerar de novo a partir de um PDF novo:

```bash
pdftotext -layout LISTA_COMPLETA_INTERNACIONAL.pdf lista.txt
python3 tools/pdf_para_tsv.py lista.txt app/src/main/assets/musicas.tsv
./gradlew assembleRelease   # APK em app/build/outputs/apk/release/
```

### Estilos musicais

O PDF não traz o estilo das músicas. Ele foi definido **por cantor** no arquivo
`app/src/main/assets/estilos.txt` (uma linha por cantor: `Cantor | estilo,estilo`).
Colaborações ("X ft. Y", "X & Y") usam o estilo do primeiro cantor. Estilos com menos
de 5 músicas não aparecem como filtro. Depois de mudar a lista ou os estilos, confira se
nenhum cantor ficou sem estilo:

```bash
python3 tools/conferir_estilos.py
```

Observação: no PDF original a música **26818 (Celeste)** não tem título; no app ela
aparece como "(sem título na lista)".

## Desenvolvimento

- Java puro, sem bibliotecas externas no app (APK de ~90 KB). `minSdk 21`, `targetSdk 35`.
- Testes (JUnit + Robolectric) cobrem a busca, a cópia do número e geram as capturas em `docs/`:
  `./gradlew testDebugUnitTest`
- O APK é assinado com `app/keystore/app.keystore` (senha `android`), uma chave simples
  guardada no repositório para que versões novas instalem por cima das antigas. Para
  publicar na Play Store, use uma chave própria e secreta.
