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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(new Locale("pt", "BR"));

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
        ((TextView) findViewById(R.id.subtitle))
                .setText(getString(R.string.subtitle, numberFormat.format(songSearch.size())));

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
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                getAssets().open("musicas.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (f.length < 4) continue;
                songs.add(new Song(f[0], f[1], f[2], f[3]));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler a lista de músicas", e);
        }
        return songs;
    }

    private void applyFilter() {
        SongSearch.Result r = songSearch.search(search.getText().toString(), field);
        shown = r.songs;
        int n = shown.size();
        String num = numberFormat.format(n);
        if (r.kind == SongSearch.Kind.LIST) {
            count.setText(getString(R.string.total, num));
        } else if (n == 0) {
            count.setText(R.string.no_results);
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
