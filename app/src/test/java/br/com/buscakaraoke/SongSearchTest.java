package br.com.buscakaraoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Testes da busca sem Android, com a lista real extraída do PDF. */
public class SongSearchTest {

    private static SongSearch search;
    private static List<Song> all;

    @BeforeClass
    public static void load() throws IOException {
        Genres genres = Genres.load(Files.newInputStream(Paths.get("src/main/assets/estilos.txt")));
        List<Song> songs = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get("src/main/assets/musicas.tsv"), StandardCharsets.UTF_8)) {
            String[] f = line.split("\t", -1);
            songs.add(new Song(f[0], f[1], f[2], f[3], genres.forArtist(f[1])));
        }
        all = songs;
        search = new SongSearch(songs);
    }

    private static SongSearch.Result find(String q, SongSearch.Field f) {
        return search.search(q, f);
    }

    private static void assertFirst(String q, SongSearch.Field f, String number) {
        SongSearch.Result r = find(q, f);
        assertTrue("sem resultado para \"" + q + "\"", !r.songs.isEmpty());
        assertEquals("primeiro resultado para \"" + q + "\"", number, r.songs.get(0).number);
    }

    private static void assertArtistFound(String q, String artist) {
        SongSearch.Result r = find(q, SongSearch.Field.ARTIST);
        assertTrue("sem resultado para \"" + q + "\"", !r.songs.isEmpty());
        assertEquals("cantor para \"" + q + "\"", artist, r.songs.get(0).artist);
    }

    private static void assertArtistAmong(String q, String... artists) {
        List<String> found = new ArrayList<>();
        for (Song x : find(q, SongSearch.Field.ARTIST).songs) found.add(x.artist);
        for (String a : artists) assertTrue(a + " em " + found, found.contains(a));
    }

    @Test
    public void buscaVaziaMostraTudo() {
        assertEquals(3920, find("", SongSearch.Field.ALL).songs.size());
        assertEquals(SongSearch.Kind.LIST, find("  ", SongSearch.Field.TITLE).kind);
    }

    @Test
    public void nomesDeMusicas() {
        assertFirst("imagine", SongSearch.Field.TITLE, "04523");
        assertFirst("Bohemian Rhapsody", SongSearch.Field.TITLE, "18024");
        assertFirst("BOHEMIAN", SongSearch.Field.TITLE, "18024");
        assertFirst("dancing queen", SongSearch.Field.ALL, "04574");
        assertFirst("I'm not in love", SongSearch.Field.TITLE, "04920");
        assertFirst("im not in love", SongSearch.Field.TITLE, "04920");
    }

    @Test
    public void nomesDeCantores() {
        assertArtistFound("adele", "Adele");
        assertArtistFound("Queen", "Queen");
        assertArtistFound("beatles", "Beatles (The)");
        // A lista tem "Beatles (The)" e "The Beatles": os dois aparecem.
        SongSearch.Result beatles = find("the beatles", SongSearch.Field.ARTIST);
        assertEquals(49, beatles.songs.size());
        for (Song x : beatles.songs) assertTrue(x.artist, x.artist.contains("Beatles"));
        assertArtistFound("beyonce", "Beyoncé");
        assertArtistFound("bon jovi", "Bon Jovi");
        assertArtistFound("bonjovi", "Bon Jovi");
        assertArtistFound("ac dc", "AC/DC");
        // A lista escreve de 3 jeitos ("Guns N' Roses", "Guns 'N Roses"...): todos aparecem.
        assertEquals(13, find("guns n roses", SongSearch.Field.ARTIST).songs.size());
        assertEquals(13, find("guns and roses", SongSearch.Field.ARTIST).songs.size());
        assertArtistFound("simon e garfunkel", "Simon & Garfunkel");
        assertEquals(17, find("adele", SongSearch.Field.ARTIST).songs.size());
    }

    @Test
    public void musicaECantorJuntos() {
        assertFirst("hello adele", SongSearch.Field.ALL, "24376");
        assertFirst("adele hello", SongSearch.Field.ALL, "24376");
        // Com o filtro errado a busca amplia sozinha.
        SongSearch.Result r = find("hello adele", SongSearch.Field.TITLE);
        assertEquals(SongSearch.Kind.WIDENED, r.kind);
        assertEquals("24376", r.songs.get(0).number);
        r = find("queen bohemian", SongSearch.Field.ARTIST);
        assertEquals(SongSearch.Kind.WIDENED, r.kind);
        assertEquals("18024", r.songs.get(0).number);
    }

    @Test
    public void errosDeDigitacao() {
        assertFirst("bohemian rapsody", SongSearch.Field.TITLE, "18024");
        assertFirst("bohemian rapsody", SongSearch.Field.ALL, "18024");
        assertEquals("04523", find("imagin", SongSearch.Field.TITLE).songs.get(1).number); // depois de IMAGINARY
        assertFirst("imagene", SongSearch.Field.TITLE, "04523");
        assertArtistFound("bom jovi", "Bon Jovi");
        assertArtistFound("mariah carrey", "Mariah Carey");
        assertArtistFound("whitney huston", "Whitney Houston");
        assertArtistFound("michel jackson", "Michael Jackson");
        assertArtistFound("micheal jakson", "Michael Jackson");
        assertEquals(SongSearch.Kind.FUZZY, find("bom jovi", SongSearch.Field.ARTIST).kind);
    }

    @Test
    public void semAcentoAchaComAcento() {
        assertArtistFound("beyonce", "Beyoncé");
        assertArtistFound("BEYONCE", "Beyoncé");
        assertArtistFound("bjork", "Björk");
        // A lista escreve alguns nomes com e sem acento: sem acento acha os dois.
        assertArtistAmong("julio iglesias", "Júlio Iglesias", "Julio Iglesias");
        assertArtistAmong("luis miguel", "Luís Miguel", "Luis Miguel");
        assertFirst("cancao do mar", SongSearch.Field.TITLE, "06363");
        assertFirst("corazon partio", SongSearch.Field.TITLE, "04105");
        assertFirst("el bano", SongSearch.Field.TITLE, "24704");
        assertFirst("la cosa mas bella", SongSearch.Field.TITLE, "18889");
        assertFirst("de musica ligera", SongSearch.Field.TITLE, "26662");
        assertFirst("non ce", SongSearch.Field.TITLE, "26165");
        assertArtistAmong("motley crue", "Mötley Crue", "Mötley Crüe");
    }

    @Test
    public void comAcentoTambemAcha() {
        assertArtistFound("Beyoncé", "Beyoncé");
        assertFirst("CANÇÃO DO MAR", SongSearch.Field.TITLE, "06363");
        assertFirst("canção", SongSearch.Field.TITLE, "06363");
        assertFirst("corazón partío", SongSearch.Field.TITLE, "04105");
        // Acento "a mais" onde a lista não tem também não atrapalha.
        assertFirst("hellô", SongSearch.Field.TITLE, "24376");
        assertArtistFound("adéle", "Adele");
    }

    @Test
    public void letrasEspeciais() {
        assertEquals("royksopp", Song.normalize("Røyksopp"));
        assertEquals("strasse", Song.normalize("Straße"));
        assertEquals("aeon", Song.normalize("Æon"));
        assertEquals("cancao do mar", Song.normalize("CANÇÃO DO MAR"));
        assertEquals("non ce", Song.normalize("NON C'È"));
    }

    @Test
    public void todaMusicaTemEstilo() {
        for (Song x : all) {
            assertTrue("sem estilo: " + x.artist, x.genres.length > 0);
            for (String g : x.genres) assertTrue("estilo desconhecido: " + g, Genres.LABELS.containsKey(g));
        }
    }

    @Test
    public void estiloDeColaboracaoVemDoPrimeiroCantor() {
        assertGenre("Rihanna ft.Calvin Harris", "pop");
        assertGenre("Alicia Keys, Beyoncé", "soul");
        assertGenre("Beatles (The)", "rock");
        assertGenre("The Beatles", "rock");
        assertGenre("Guns 'N Roses", "rock");
        assertGenre("KC and The Sunshine Band", "dance");
        assertGenre("Simon and Garfunkel", "rock");
        assertGenre("FIFTY FIFTY (피프티피프티)", "kpop");
        assertGenre("High School Musical (Sharpay)", "filmes");
        assertGenre("J Balvin & Pitbull ft. Camila Cabello", "latina");
    }

    private static void assertGenre(String artist, String genre) {
        for (Song x : all) {
            if (x.artist.equals(artist)) {
                assertTrue(artist + " deveria ser " + genre, x.hasGenre(genre));
                return;
            }
        }
        throw new AssertionError("cantor não está na lista: " + artist);
    }

    @Test
    public void filtroPorEstilo() {
        List<Song> rock = find("", SongSearch.Field.ALL, "rock").songs;
        assertTrue(rock.size() > 1000);
        for (Song x : rock) assertTrue(x.artist, x.hasGenre("rock"));
        boolean queen = false;
        for (Song x : rock) queen |= x.artist.equals("Queen");
        assertTrue(queen);

        // Busca + estilo juntos.
        SongSearch.Result r = find("love", SongSearch.Field.TITLE, "romantica");
        assertTrue(r.songs.size() > 10);
        for (Song x : r.songs) assertTrue(x.hasGenre("romantica") && x.normTitle.contains("love"));
        // Adele não é rock: com o filtro Rock ela aparece mesmo assim, avisando o outro estilo.
        SongSearch.Result adele = find("adele", SongSearch.Field.ARTIST, "rock");
        assertEquals(SongSearch.Kind.OTHER_GENRE, adele.kind);
        assertEquals("Adele", adele.songs.get(0).artist);
        assertEquals(17, adele.songs.size());
        assertEquals(17, find("adele", SongSearch.Field.ARTIST, "pop").songs.size());
    }

    private static SongSearch.Result find(String q, SongSearch.Field f, String genre) {
        return search.search(q, f, genre);
    }

    @Test
    public void escritoPeloSom() {
        assertArtistFound("xakira", "Shakira");
        assertArtistFound("uitney huston", "Whitney Houston");
        assertArtistFound("ed xiran", "Ed Sheeran");
        assertArtistFound("bionce", "Beyoncé");
        assertArtistFound("rianna", "Rihanna");
        assertArtistFound("metalica", "Metallica");
        assertArtistFound("rolim stones", "Rolling Stones (The)");
        assertArtistFound("aerosmit", "Aerosmith");
        assertFirst("tanderstruck", SongSearch.Field.TITLE, "18592");
    }

    @Test
    public void numero() {
        assertFirst("04920", SongSearch.Field.ALL, "04920");
        assertFirst("4920", SongSearch.Field.ALL, "04920");
    }

    @Test
    public void lixoNaoAchaNada() {
        assertTrue(find("xyzxyzxyz", SongSearch.Field.ALL).songs.isEmpty());
        assertTrue(find("qwertyuiop", SongSearch.Field.TITLE).songs.isEmpty());
    }

    @Test
    public void distancia() {
        assertEquals(0, search.distance("abc", "abc", 3, 2));
        assertEquals(1, search.distance("abc", "acb", 3, 2));
        assertEquals(1, search.distance("rapsody", "rhapsody", 8, 2));
        assertEquals(3, search.distance("abc", "xyz", 3, 2));
    }
}
