package br.com.buscakaraoke;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Busca tolerante na lista de músicas.
 *
 * <ol>
 *   <li>Procura o texto no campo escolhido (música, cantor ou tudo).</li>
 *   <li>Se não achar nada só na música ou só no cantor, procura nos dois.</li>
 *   <li>Se ainda não achar, aceita pequenos erros de digitação ("rapsody", "bom jovi").</li>
 * </ol>
 */
final class SongSearch {

    enum Field { ALL, TITLE, ARTIST }

    enum Kind {
        /** Busca vazia: lista completa. */
        LIST,
        /** Achou no campo escolhido. */
        EXACT,
        /** Não achou no campo escolhido, mas achou procurando em música e cantor. */
        WIDENED,
        /** Não achou no estilo escolhido, mas achou em outros estilos. */
        OTHER_GENRE,
        /** Só achou aceitando erros de digitação (pode estar vazio). */
        FUZZY
    }

    static final class Result {
        final Kind kind;
        final List<Song> songs;

        Result(Kind kind, List<Song> songs) {
            this.kind = kind;
            this.songs = songs;
        }
    }

    /** Palavras que podem faltar ou estar escritas de outro jeito ("and" x "&" x "e"). */
    private static final Set<String> OPTIONAL = new HashSet<>(Arrays.asList(
            "the", "a", "an", "and", "e", "n", "y", "feat", "ft", "featuring", "com", "part", "by"));

    private static final int MAX_FUZZY = 100;

    private final List<Song> byArtist;
    private final List<Song> byTitle;
    private int[] row0 = new int[32];
    private int[] row1 = new int[32];
    private int[] row2 = new int[32];

    SongSearch(List<Song> songsInListOrder) {
        byArtist = Collections.unmodifiableList(new ArrayList<>(songsInListOrder));
        List<Song> t = new ArrayList<>(songsInListOrder);
        // Ordena pelo nome da música; as sem título vão para o fim.
        Collections.sort(t, (a, b) -> {
            if (a.normTitle.isEmpty() != b.normTitle.isEmpty()) return a.normTitle.isEmpty() ? 1 : -1;
            int c = a.normTitle.compareTo(b.normTitle);
            return c != 0 ? c : a.normArtist.compareTo(b.normArtist);
        });
        byTitle = Collections.unmodifiableList(t);
    }

    int size() {
        return byArtist.size();
    }

    Result search(String rawQuery, Field field) {
        return search(rawQuery, field, null);
    }

    /** Músicas de um estilo, na ordem da lista. */
    int countGenre(String genre) {
        int n = 0;
        for (Song s : byArtist) {
            if (s.hasGenre(genre)) n++;
        }
        return n;
    }

    /** @param genre chave do estilo ("rock"...) ou {@code null} para todos. */
    Result search(String rawQuery, Field field, String genre) {
        List<Song> all = field == Field.TITLE ? byTitle : byArtist;
        List<Song> inGenre = all;
        if (genre != null) {
            inGenre = new ArrayList<>();
            for (Song s : all) {
                if (s.hasGenre(genre)) inGenre.add(s);
            }
        }
        String q = Song.normalize(rawQuery);
        if (q.isEmpty()) return new Result(Kind.LIST, inGenre);

        String[] tokens = requiredTokens(q);
        String compact = q.replace(" ", "");

        // 1) Texto exato: no estilo escolhido e, se não achar, em todos os estilos.
        List<Song> r = exact(q, tokens, compact, field, inGenre);
        if (!r.isEmpty()) return new Result(Kind.EXACT, r);
        if (field != Field.ALL) {
            r = exact(q, tokens, compact, Field.ALL, inGenre);
            if (!r.isEmpty()) return new Result(Kind.WIDENED, r);
        }
        if (genre != null) {
            r = exact(q, tokens, compact, field, all);
            if (r.isEmpty() && field != Field.ALL) r = exact(q, tokens, compact, Field.ALL, all);
            if (!r.isEmpty()) return new Result(Kind.OTHER_GENRE, r);
        }

        // 2) Com erros de digitação ou escrito "pelo som".
        r = fuzzy(tokens, field, inGenre);
        if (r.isEmpty() && field != Field.ALL) r = fuzzy(tokens, Field.ALL, inGenre);
        if (r.isEmpty() && genre != null) {
            r = fuzzy(tokens, field, all);
            if (r.isEmpty() && field != Field.ALL) r = fuzzy(tokens, Field.ALL, all);
        }
        return new Result(Kind.FUZZY, r);
    }

    /** Tira as palavras opcionais, a não ser que a busca só tenha elas. */
    private static String[] requiredTokens(String q) {
        String[] all = q.split(" ");
        List<String> req = new ArrayList<>();
        for (String t : all) {
            if (!OPTIONAL.contains(t)) req.add(t);
        }
        return req.isEmpty() ? all : req.toArray(new String[0]);
    }

    // ------------------------------------------------------------- exata

    private static List<Song> exact(String q, String[] tokens, String compact, Field field, List<Song> base) {
        List<Song> starts = new ArrayList<>();
        List<Song> wordStarts = new ArrayList<>();
        List<Song> others = new ArrayList<>();
        for (Song s : base) {
            boolean ok = containsAll(hay(s, field), tokens)
                    || (compact.length() > 1 && compactHay(s, field).contains(compact));
            if (!ok) continue;
            // Prioriza: começa com o texto > alguma palavra começa com ele > contém.
            int rank = rank(s, field, q);
            (rank == 0 ? starts : rank == 1 ? wordStarts : others).add(s);
        }
        starts.addAll(wordStarts);
        starts.addAll(others);
        return starts;
    }

    private static boolean containsAll(String hay, String[] tokens) {
        for (String t : tokens) {
            if (!hay.contains(t)) return false;
        }
        return true;
    }

    private static int rank(Song s, Field field, String q) {
        int best = 2;
        if (field != Field.ARTIST) best = Math.min(best, rank(s.normTitle, q));
        if (field != Field.TITLE) best = Math.min(best, rank(s.normArtist, q));
        if (field == Field.ALL && s.number.startsWith(q)) best = 0;
        return best;
    }

    private static int rank(String text, String q) {
        if (text.startsWith(q)) return 0;
        if (text.contains(" " + q)) return 1;
        return 2;
    }

    private static String hay(Song s, Field f) {
        return f == Field.TITLE ? s.normTitle : f == Field.ARTIST ? s.normArtist : s.normAll;
    }

    private static String compactHay(Song s, Field f) {
        return f == Field.TITLE ? s.compactTitle : f == Field.ARTIST ? s.compactArtist : s.compactAll;
    }

    private static String[] words(Song s, Field f) {
        return f == Field.TITLE ? s.titleWords : f == Field.ARTIST ? s.artistWords : s.allWords;
    }

    private static String[] sounds(Song s, Field f) {
        return f == Field.TITLE ? s.titleSounds : f == Field.ARTIST ? s.artistSounds : s.allSounds;
    }

    // ----------------------------------------------- com erros de digitação

    private List<Song> fuzzy(String[] tokens, Field field, List<Song> base) {
        String[] tokenSounds = new String[tokens.length];
        for (int i = 0; i < tokens.length; i++) tokenSounds[i] = Song.sound(tokens[i]);
        List<Song> hits = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (Song s : base) {
            String hay = hay(s, field);
            String[] words = words(s, field);
            String[] sounds = sounds(s, field);
            int total = 0;
            for (int i = 0; i < tokens.length; i++) {
                String t = tokens[i];
                int max = allowedErrors(t);
                int d = hay.contains(t) ? 0 : bestDistance(t, words, max);
                if (d > max) d = bestDistance(tokenSounds[i], sounds, max);
                if (d > max) {
                    total = -1;
                    break;
                }
                total += d;
            }
            if (total >= 0) {
                // Desempate: quem começa com a primeira palavra buscada vem antes
                // ("mariah carrey" -> "Mariah Carey" antes de "Busta Rhymes ft. Mariah Carey").
                total = total * 2 + (words.length > 0
                        && distance(tokens[0], words[0], words[0].length(), allowedErrors(tokens[0]))
                        <= allowedErrors(tokens[0]) ? 0 : 1);
                hits.add(s);
                scores.add(total);
            }
        }
        Integer[] order = new Integer[hits.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Integer.compare(scores.get(a), scores.get(b))); // estável
        List<Song> out = new ArrayList<>();
        for (int i = 0; i < order.length && i < MAX_FUZZY; i++) out.add(hits.get(order[i]));
        return out;
    }

    static int allowedErrors(String token) {
        int n = token.length();
        if (n <= 2) return 0;
        if (n <= 5) return 1;
        return 2;
    }

    /** Menor distância entre o termo e alguma palavra (inteira ou o começo dela). */
    private int bestDistance(String t, String[] words, int max) {
        int best = max + 1;
        for (String w : words) {
            best = Math.min(best, distance(t, w, w.length(), max));
            // Começo da palavra, para quem ainda está digitando ("rapso" ~ "rhapsody").
            for (int len = t.length() - 1; len <= t.length() + 1; len++) {
                if (len > 0 && len < w.length()) best = Math.min(best, distance(t, w, len, max));
            }
            if (best == 0) break;
        }
        return best;
    }

    /**
     * Distância de edição (com troca de letras vizinhas) entre {@code a} e os primeiros
     * {@code bLen} caracteres de {@code b}. Devolve {@code max + 1} se passar do limite.
     */
    int distance(String a, String b, int bLen, int max) {
        int n = a.length();
        if (Math.abs(n - bLen) > max) return max + 1;
        if (row0.length <= bLen) {
            row0 = new int[bLen + 1];
            row1 = new int[bLen + 1];
            row2 = new int[bLen + 1];
        }
        int[] prev2 = row0, prev = row1, cur = row2;
        for (int j = 0; j <= bLen; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            int rowMin = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= bLen; j++) {
                char cb = b.charAt(j - 1);
                int v = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + (ca == cb ? 0 : 1));
                if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == cb) {
                    v = Math.min(v, prev2[j - 2] + 1);
                }
                cur[j] = v;
                if (v < rowMin) rowMin = v;
            }
            if (rowMin > max) return max + 1;
            int[] tmp = prev2;
            prev2 = prev;
            prev = cur;
            cur = tmp;
        }
        return Math.min(prev[bLen], max + 1);
    }
}
