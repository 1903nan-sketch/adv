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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    /** Estilos com menos músicas que isso não viram filtro. */
    private static final int MIN_SONGS_PER_GENRE = 5;
    private static final String STATE_GENRE = "genre";
    private static final String STATE_LIST = "list";

    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(new Locale("pt", "BR"));
    private final Map<Integer, String> genreByChipId = new HashMap<>();
    /** Estilo escolhido, ou {@code null} para todos. */
    private String genre;
    /** {@code true} só nacionais, {@code false} só internacionais, {@code null} as duas listas. */
    private Boolean national;

    private SongSearch songSearch;
    private List<Song> shown = Collections.emptyList();
    private SongSearch.Field field = SongSearch.Field.ALL;

    private EditText search;
    private View clear;
    private TextView count;
    private ListView list;
    private SongAdapter adapter;

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

        songSearch = new SongSearch(loadSongs());
        ((TextView) findViewById(R.id.version)).setText(getString(R.string.version, BuildConfig.VERSION_NAME));

        RadioGroup lists = findViewById(R.id.lists);
        if (savedInstanceState != null) lists.check(savedInstanceState.getInt(STATE_LIST, R.id.list_all));
        national = nationalFor(lists.getCheckedRadioButtonId());
        buildGenreChips(savedInstanceState == null ? null : savedInstanceState.getString(STATE_GENRE));
        lists.setOnCheckedChangeListener((g, id) -> {
            if (id == View.NO_ID) return;
            national = nationalFor(id);
            buildGenreChips(genre);
            applyFilter();
            list.setSelection(0);
        });

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
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_NULL) {
                applyFilter();
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
            setField(checkedId);
            applyFilter();
            list.setSelection(0);
        });
        setField(modes.getCheckedRadioButtonId());

        applyWindowInsets();
        applyFilter();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_GENRE, genre);
        outState.putInt(STATE_LIST, ((RadioGroup) findViewById(R.id.lists)).getCheckedRadioButtonId());
    }

    private static Boolean nationalFor(int listChipId) {
        if (listChipId == R.id.list_nac) return Boolean.TRUE;
        if (listChipId == R.id.list_int) return Boolean.FALSE;
        return null;
    }

    /**
     * Monta os botões de estilo da lista escolhida, do estilo com mais músicas para o com
     * menos. Mantém o estilo marcado se ele existir na lista nova.
     */
    private void buildGenreChips(String selected) {
        RadioGroup group = findViewById(R.id.genres);
        group.setOnCheckedChangeListener(null);
        group.removeAllViews();
        genreByChipId.clear();
        int checked = addGenreChip(group, getString(R.string.all_genres), null);
        List<String> keys = new ArrayList<>(Genres.LABELS.keySet());
        Map<String, Integer> counts = new HashMap<>();
        for (String k : keys) counts.put(k, songSearch.countGenre(k, national));
        Collections.sort(keys, (a, b) -> Integer.compare(counts.get(b), counts.get(a)));
        for (String k : keys) {
            if (counts.get(k) < MIN_SONGS_PER_GENRE) continue;
            int id = addGenreChip(group, Genres.LABELS.get(k), k);
            if (k.equals(selected)) checked = id;
        }
        group.check(checked);
        genre = genreByChipId.get(checked);
        group.setOnCheckedChangeListener((g, id) -> {
            if (id == View.NO_ID) return;
            genre = genreByChipId.get(id);
            applyFilter();
            list.setSelection(0);
        });
    }

    private int addGenreChip(RadioGroup group, String label, String key) {
        RadioButton chip = new RadioButton(this, null, 0, R.style.GenreChip);
        int id = View.generateViewId();
        chip.setId(id);
        chip.setText(label);
        chip.setSaveEnabled(false);
        // Criado por código, o botão não herda "clicável" do tema: sem isso o toque não funciona.
        chip.setClickable(true);
        chip.setFocusable(true);
        RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        lp.setMarginEnd(dp(8));
        group.addView(chip, lp);
        genreByChipId.put(id, key);
        return id;
    }

    @SuppressWarnings("deprecation")
    private int color(int id) {
        return Build.VERSION.SDK_INT >= 23 ? getColor(id) : getResources().getColor(id);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        setField(((RadioGroup) findViewById(R.id.modes)).getCheckedRadioButtonId());
        applyFilter();
    }

    private void setField(int checkedId) {
        if (checkedId == R.id.mode_song) {
            field = SongSearch.Field.TITLE;
            search.setHint(R.string.hint_song);
        } else if (checkedId == R.id.mode_artist) {
            field = SongSearch.Field.ARTIST;
            search.setHint(R.string.hint_artist);
        } else {
            field = SongSearch.Field.ALL;
            search.setHint(R.string.hint_all);
        }
    }

    private List<Song> loadSongs() {
        List<Song> songs = new ArrayList<>();
        try {
            Genres genres = Genres.load(getAssets().open("estilos.txt"));
            readList("internacional.tsv", false, genres, songs);
            readList("nacional.tsv", true, genres, songs);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler a lista de músicas", e);
        }
        return songs;
    }

    private void readList(String file, boolean isNational, Genres genres, List<Song> out) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                getAssets().open(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (f.length < 4) continue;
                out.add(new Song(f[0], f[1], f[2], f[3], genres.forArtist(f[1]), isNational));
            }
        }
    }

    private void applyFilter() {
        SongSearch.Result r = songSearch.search(search.getText().toString(), field, genre, national);
        shown = r.songs;
        int n = shown.size();
        String num = numberFormat.format(n);
        if (r.kind == SongSearch.Kind.LIST) {
            count.setText(genre == null ? getString(R.string.total, num)
                    : getString(R.string.total_genre, num, Genres.LABELS.get(genre)));
        } else if (n == 0) {
            count.setText(R.string.no_results);
        } else if (r.kind == SongSearch.Kind.OTHER_GENRE) {
            count.setText(getResources().getQuantityString(R.plurals.results_other_genre, n, num,
                    Genres.LABELS.get(genre)));
        } else if (r.kind == SongSearch.Kind.OTHER_LIST) {
            count.setText(getResources().getQuantityString(
                    Boolean.TRUE.equals(national) ? R.plurals.results_in_int : R.plurals.results_in_nac, n, num));
        } else if (r.kind == SongSearch.Kind.WIDENED) {
            count.setText(getResources().getQuantityString(R.plurals.results_widened, n, num));
        } else if (r.kind == SongSearch.Kind.FUZZY) {
            count.setText(getResources().getQuantityString(R.plurals.results_fuzzy, n, num));
        } else {
            count.setText(getResources().getQuantityString(R.plurals.results, n, num));
        }
        adapter.notifyDataSetChanged();
    }

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

    // ---------------------------------------------------------------- lista

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
                h.box = convertView.findViewById(R.id.code_box);
                h.att = convertView.findViewById(R.id.att);
                h.title = convertView.findViewById(R.id.title);
                h.artist = convertView.findViewById(R.id.artist);
                convertView.setTag(h);
            } else {
                h = (Holder) convertView.getTag();
            }
            Song s = shown.get(position);
            h.code.setText(s.number);
            h.att.setText(getString(R.string.att, getString(s.national ? R.string.tag_nac : R.string.tag_int), s.att));
            int ink = color(s.national ? R.color.code_text_nac : R.color.code_text);
            h.code.setTextColor(ink);
            h.att.setTextColor(ink);
            h.box.setBackgroundResource(s.national ? R.drawable.code_bg_nac : R.drawable.code_bg);
            h.title.setText(s.title.isEmpty() ? getString(R.string.no_title) : s.title);
            h.artist.setText(s.artist);
            return convertView;
        }
    }

    private static final class Holder {
        View box;
        TextView code;
        TextView att;
        TextView title;
        TextView artist;
    }
}
