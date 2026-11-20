package br.com.buscakaraoke;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Uma linha da lista de karaokê, com versões normalizadas para a busca. */
final class Song {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern APOSTROPHES = Pattern.compile("['’‘`´]");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final String[] NO_WORDS = new String[0];

    final String number;
    final String artist;
    final String title;
    final String att;

    final String normTitle;
    final String normArtist;
    final String normAll;
    final String compactTitle;
    final String compactArtist;
    final String compactAll;
    final String[] titleWords;
    final String[] artistWords;
    final String[] allWords;

    Song(String number, String artist, String title, String att) {
        this.number = number;
        this.artist = artist;
        this.title = title;
        this.att = att;
        normTitle = normalize(title);
        normArtist = normalize(artist);
        normAll = normTitle + " " + normArtist + " " + number;
        compactTitle = normTitle.replace(" ", "");
        compactArtist = normArtist.replace(" ", "");
        compactAll = compactTitle + " " + compactArtist + " " + number;
        titleWords = words(normTitle);
        artistWords = words(normArtist);
        allWords = words(normAll);
    }

    /** Minúsculas, sem acentos, sem apóstrofos e só letras/números separados por espaço. */
    static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = MARKS.matcher(n).replaceAll("").toLowerCase(Locale.ROOT);
        n = APOSTROPHES.matcher(n).replaceAll("");
        return NON_ALNUM.matcher(n).replaceAll(" ").trim();
    }

    private static String[] words(String norm) {
        return norm.isEmpty() ? NO_WORDS : norm.split(" ");
    }
}
