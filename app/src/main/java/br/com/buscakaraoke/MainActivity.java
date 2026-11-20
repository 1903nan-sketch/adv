package br.com.buscakaraoke;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final int MODE_ALL = 0;
    private static final int MODE_SONG = 1;
    private static final int MODE_ARTIST = 2;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern APOSTROPHES = Pattern.compile("['’‘`´]");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(new Locale("pt", "BR"));

    /** Lista na ordem do PDF (por cantor). */
    private final List<Song> byArtist = new ArrayList<>();
    /** Mesma lista ordenada pelo nome da música. */
    private final List<Song> byTitle = new ArrayList<>();
    private final List<Song> shown = new ArrayList<>();

    private EditText search;
    private View clear;
    private TextView count;
    private ListView list;
    private SongAdapter adapter;
    private int mode = MODE_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupEdgeToEdge();
        setContentView(R.layout.activity_main);

        search = findViewById(R.id.search);
        clear = findViewById(R.id.clear);
        count = findViewById(R.id.count);
        list = findViewById(R.id.list);
        RadioGroup modes = findViewById(R.id.modes);

        loadSongs();
        ((TextView) findViewById(R.id.subtitle))
                .setText(getString(R.string.subtitle, numberFormat.format(byArtist.size())));

        adapter = new SongAdapter();
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.empty));
        list.setOnItemClickListener((parent, view, position, id) -> copyNumber(shown.get(position)));
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_TOUCH_SCROLL) hideKeyboard();
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                applyFilter();
            }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });
        clear.setOnClickListener(v -> {
            search.setText("");
            search.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        });

        modes.setOnCheckedChangeListener((group, checkedId) -> {
            setMode(checkedId);
            applyFilter();
            list.setSelection(0);
        });
        setMode(modes.getCheckedRadioButtonId());

        applyWindowInsets();
        applyFilter();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        setMode(((RadioGroup) findViewById(R.id.modes)).getCheckedRadioButtonId());
        applyFilter();
    }

    private void setMode(int checkedId) {
        if (checkedId == R.id.mode_song) {
            mode = MODE_SONG;
            search.setHint(R.string.hint_song);
        } else if (checkedId == R.id.mode_artist) {
            mode = MODE_ARTIST;
            search.setHint(R.string.hint_artist);
        } else {
            mode = MODE_ALL;
            search.setHint(R.string.hint_all);
        }
    }

    // ---------------------------------------------------------------- dados

    private void loadSongs() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                getAssets().open("musicas.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (f.length < 4) continue;
                byArtist.add(new Song(f[0], f[1], f[2], f[3]));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler a lista de músicas", e);
        }
        byTitle.addAll(byArtist);
        // Músicas sem título vão para o fim.
        Collections.sort(byTitle, (a, b) -> {
            if (a.normTitle.isEmpty() != b.normTitle.isEmpty()) return a.normTitle.isEmpty() ? 1 : -1;
            int c = a.normTitle.compareTo(b.normTitle);
            return c != 0 ? c : a.normArtist.compareTo(b.normArtist);
        });
    }

    static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = MARKS.matcher(n).replaceAll("").toLowerCase(Locale.ROOT);
        n = APOSTROPHES.matcher(n).replaceAll("");
        return NON_ALNUM.matcher(n).replaceAll(" ").trim();
    }

    // ---------------------------------------------------------------- busca

    private void applyFilter() {
        String query = normalize(search.getText().toString());
        List<Song> base = mode == MODE_SONG ? byTitle : byArtist;
        shown.clear();

        if (query.isEmpty()) {
            shown.addAll(base);
            count.setText(getString(R.string.total, numberFormat.format(base.size())));
        } else {
            String[] tokens = query.split(" ");
            String compactQuery = query.replace(" ", "");
            List<Song> starts = new ArrayList<>();
            List<Song> wordStarts = new ArrayList<>();
            List<Song> others = new ArrayList<>();
            for (Song s : base) {
                String hay = haystack(s);
                if (!matches(hay, tokens) && !(compactQuery.length() > 1
                        && haystackCompact(s).contains(compactQuery))) {
                    continue;
                }
                // Prioriza: começa com o texto > alguma palavra começa com ele > contém.
                int rank = rank(s, query);
                (rank == 0 ? starts : rank == 1 ? wordStarts : others).add(s);
            }
            shown.addAll(starts);
            shown.addAll(wordStarts);
            shown.addAll(others);
            int n = shown.size();
            count.setText(getResources().getQuantityString(R.plurals.results, n, numberFormat.format(n)));
        }
        adapter.notifyDataSetChanged();
    }

    private String haystack(Song s) {
        switch (mode) {
            case MODE_SONG: return s.normTitle;
            case MODE_ARTIST: return s.normArtist;
            default: return s.normAll;
        }
    }

    private String haystackCompact(Song s) {
        switch (mode) {
            case MODE_SONG: return s.compactTitle;
            case MODE_ARTIST: return s.compactArtist;
            default: return s.compactAll;
        }
    }

    private static boolean matches(String hay, String[] tokens) {
        for (String t : tokens) {
            if (!hay.contains(t)) return false;
        }
        return true;
    }

    private int rank(Song s, String query) {
        int best = 2;
        if (mode != MODE_ARTIST) best = Math.min(best, rank(s.normTitle, query));
        if (mode != MODE_SONG) best = Math.min(best, rank(s.normArtist, query));
        if (mode == MODE_ALL && s.number.startsWith(query)) best = 0;
        return best;
    }

    private static int rank(String field, String query) {
        if (field.startsWith(query)) return 0;
        if (field.contains(" " + query)) return 1;
        return 2;
    }

    // ---------------------------------------------------------------- ações

    private void copyNumber(Song s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.clip_label), s.number));
        }
        Toast.makeText(this, getString(R.string.copied, s.number), Toast.LENGTH_SHORT).show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    // ---------------------------------------------------------------- janela

    @SuppressWarnings("deprecation")
    private void setupEdgeToEdge() {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            w.setNavigationBarColor(Color.TRANSPARENT);
        } else {
            View decor = w.getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @SuppressWarnings("deprecation")
    private void applyWindowInsets() {
        View root = findViewById(R.id.root);
        View header = findViewById(R.id.header);
        View content = findViewById(R.id.content);
        int headerTop = header.getPaddingTop();
        int headerStart = header.getPaddingLeft();
        int headerEnd = header.getPaddingRight();

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                top = bars.top;
                left = bars.left;
                right = bars.right;
                bottom = Math.max(bars.bottom, ime.bottom);
            } else {
                top = insets.getSystemWindowInsetTop();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            header.setPadding(headerStart + left, headerTop + top, headerEnd + right, header.getPaddingBottom());
            content.setPadding(left, 0, right, bottom);
            return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
    }

    // ---------------------------------------------------------------- modelo

    static final class Song {
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
        }
    }

    private final class SongAdapter extends BaseAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(MainActivity.this);

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Song getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_song, parent, false);
                h = new Holder();
                h.code = convertView.findViewById(R.id.code);
                h.att = convertView.findViewById(R.id.att);
                h.title = convertView.findViewById(R.id.title);
                h.artist = convertView.findViewById(R.id.artist);
                convertView.setTag(h);
            } else {
                h = (Holder) convertView.getTag();
            }
            Song s = shown.get(position);
            h.code.setText(s.number);
            h.att.setText(getString(R.string.att, s.att));
            h.title.setText(s.title.isEmpty() ? getString(R.string.no_title) : s.title);
            h.artist.setText(s.artist);
            return convertView;
        }
    }

    private static final class Holder {
        TextView code;
        TextView att;
        TextView title;
        TextView artist;
    }
}
