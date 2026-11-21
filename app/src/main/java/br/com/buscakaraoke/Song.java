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
    /** Chaves de estilo (ex.: "pop", "rock"); pode ser vazio. */
    final String[] genres;

    Song(String number, String artist, String title, String att) {
        this(number, artist, title, att, NO_WORDS);
    }

    Song(String number, String artist, String title, String att, String[] genres) {
        this.genres = genres;
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
        n = foldSpecialLetters(n);
        return NON_ALNUM.matcher(n).replaceAll(" ").trim();
    }

    /** Letras que não perdem o "acento" sozinhas (ø, æ, ß...). */
    private static String foldSpecialLetters(String n) {
        StringBuilder out = null;
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            String r;
            switch (c) {
                case 'ø': r = "o"; break;
                case 'æ': r = "ae"; break;
                case 'œ': r = "oe"; break;
                case 'ß': r = "ss"; break;
                case 'ł': r = "l"; break;
                case 'đ': case 'ð': r = "d"; break;
                case 'þ': r = "th"; break;
                case 'ı': r = "i"; break;
                default: r = null;
            }
            if (r != null && out == null) out = new StringBuilder(n.length() + 4).append(n, 0, i);
            if (out != null) {
                if (r != null) out.append(r); else out.append(c);
            }
        }
        return out == null ? n : out.toString();
    }

    boolean hasGenre(String genre) {
        for (String g : genres) {
            if (g.equals(genre)) return true;
        }
        return false;
    }

    private static String[] words(String norm) {
        return norm.isEmpty() ? NO_WORDS : norm.split(" ");
    }
}
