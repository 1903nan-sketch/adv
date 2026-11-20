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

    @BeforeClass
    public static void load() throws IOException {
        List<Song> songs = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get("src/main/assets/musicas.tsv"), StandardCharsets.UTF_8)) {
            String[] f = line.split("\t", -1);
            songs.add(new Song(f[0], f[1], f[2], f[3]));
        }
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
