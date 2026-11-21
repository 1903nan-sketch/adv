package br.com.buscakaraoke;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Estilo musical de cada cantor, lido de {@code assets/estilos.txt}.
 * A mesma regra está em {@code tools/conferir_estilos.py}.
 */
final class Genres {

    /** Ordem e nome de cada estilo nos filtros. */
    static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("pop", "Pop");
        LABELS.put("rock", "Rock");
        LABELS.put("romantica", "Românticas");
        LABELS.put("soul", "R&B e Soul");
        LABELS.put("dance", "Dance");
        LABELS.put("rap", "Rap e Hip-Hop");
        LABELS.put("latina", "Latinas");
        LABELS.put("country", "Country");
        LABELS.put("jazz", "Jazz e Blues");
        LABELS.put("filmes", "Filmes e Musicais");
        LABELS.put("reggae", "Reggae");
        LABELS.put("europeia", "Italianas e Europeias");
        LABELS.put("kpop", "K-pop");
        LABELS.put("sertanejo", "Sertanejo");
        LABELS.put("mpb", "MPB");
        LABELS.put("gospel", "Gospel");
    }

    private static final Pattern FEAT = Pattern.compile(
            "(?i)\\s*\\(?(?:\\bfeaturing\\b|\\bfeat\\b\\.?|ft\\.|\\bft\\b|\\bpart\\.)\\s*.*$");
    private static final Pattern PARENS = Pattern.compile("\\s*\\([^)]*\\)");
    private static final Pattern SPLIT = Pattern.compile(
            "(?i)\\s*(?:,|&|\\+|/|\\bx\\b|\\be\\b|\\band\\b|\\bvs\\b\\.?|\\bwith\\b)\\s*");
    private static final String[] NONE = new String[0];

    private final Map<String, String[]> byKey = new HashMap<>();

    static Genres load(InputStream in) throws IOException {
        Genres g = new Genres();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int bar = line.indexOf('|');
                if (bar < 0) continue;
                List<String> list = new ArrayList<>();
                for (String s : line.substring(bar + 1).split(",")) {
                    s = s.trim();
                    if (!s.isEmpty()) list.add(s);
                }
                g.byKey.put(key(line.substring(0, bar)), list.toArray(NONE));
            }
        }
        return g;
    }

    /** Estilos do cantor; colaborações usam o primeiro cantor reconhecido. */
    String[] forArtist(String artist) {
        String base = FEAT.matcher(artist).replaceFirst("").trim();
        if (base.isEmpty()) base = artist;
        String noParens = PARENS.matcher(base).replaceAll("").trim();
        if (noParens.isEmpty()) noParens = base;
        String[] g = byKey.get(key(base));
        if (g == null) g = byKey.get(key(noParens));
        if (g != null) return g;
        for (String part : SPLIT.split(noParens)) {
            if (part.trim().isEmpty()) continue;
            g = byKey.get(key(part));
            if (g != null) return g;
        }
        return NONE;
    }

    /** "The Beatles", "Beatles (The)" e "beatles" viram a mesma chave. */
    static String key(String name) {
        StringBuilder sb = new StringBuilder();
        for (String t : Song.normalize(name).split(" ")) {
            if (t.equals("the") || t.equals("and") || t.equals("e")) continue;
            sb.append(t);
        }
        return sb.toString();
    }
}
