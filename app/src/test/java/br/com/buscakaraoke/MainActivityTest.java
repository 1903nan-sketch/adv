package br.com.buscakaraoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w393dp-h852dp-xxhdpi")
public class MainActivityTest {

    private MainActivity start() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        return c.get();
    }

    private static List<Song> results(MainActivity a) {
        ListView list = a.findViewById(R.id.list);
        List<Song> out = new ArrayList<>();
        for (int i = 0; i < list.getAdapter().getCount(); i++) {
            out.add((Song) list.getAdapter().getItem(i));
        }
        return out;
    }

    private static void type(MainActivity a, String text) {
        ((EditText) a.findViewById(R.id.search)).setText(text);
        ShadowLooper.idleMainLooper();
    }

    /** Toque de verdade (dedo na tela), não um atalho de código. */
    static void tap(View v) {
        assertTrue("botão sem tamanho na tela", v.getWidth() > 0 && v.getHeight() > 0);
        long t = SystemClock.uptimeMillis();
        float x = v.getWidth() / 2f, y = v.getHeight() / 2f;
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0);
        v.dispatchTouchEvent(down);
        v.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        ShadowLooper.idleMainLooper();
    }

    private static void mode(MainActivity a, int id) {
        tap(a.findViewById(id));
        assertTrue("o filtro não ficou marcado", ((RadioButton) a.findViewById(id)).isChecked());
    }

    @Test
    public void carregaTodasAsMusicasDoPdf() {
        MainActivity a = start();
        assertEquals(9157, results(a).size());
        assertEquals("versão " + BuildConfig.VERSION_NAME,
                ((TextView) a.findViewById(R.id.version)).getText().toString());
        assertEquals("9.157 músicas · toque para copiar o número",
                ((TextView) a.findViewById(R.id.count)).getText().toString());
    }

    @Test
    public void buscaPorMusica() {
        MainActivity a = start();
        mode(a, R.id.mode_song);
        type(a, "dancing queen");
        List<Song> r = results(a);
        assertEquals("04574", r.get(0).number);
        assertEquals("ABBA", r.get(0).artist);
        // Nome da música + cantor com o filtro "Música": procura nos dois e avisa.
        type(a, "hello adele");
        assertEquals("24376", results(a).get(0).number);
        assertEquals("1 resultado buscando em música e cantor",
                ((TextView) a.findViewById(R.id.count)).getText().toString());
    }

    @Test
    public void buscaPorCantor() {
        MainActivity a = start();
        mode(a, R.id.mode_artist);
        type(a, "adele");
        List<Song> r = results(a);
        assertEquals(17, r.size());
        for (Song s : r) assertTrue(s.artist, s.artist.toLowerCase().contains("adele"));
        // Participações ("feat.") também aparecem.
        type(a, "katy perry");
        boolean feat = false;
        for (Song s : results(a)) feat |= s.artist.equals("3OH!3 feat. Katy Perry");
        assertTrue(feat);
    }

    @Test
    public void ignoraAcentosMaiusculasEApostrofos() {
        MainActivity a = start();
        mode(a, R.id.mode_song);
        type(a, "dont trust me");
        assertEquals("DON'T TRUST ME", results(a).get(0).title);
        type(a, "Im Not In Love");
        assertEquals("I'M NOT IN LOVE", results(a).get(0).title);
        mode(a, R.id.mode_artist);
        type(a, "acdc");
        assertTrue(results(a).size() >= 7);
        assertEquals("AC/DC", results(a).get(0).artist);
        type(a, "elley duhe");
        assertEquals("Zedd, Elley Duhé", results(a).get(0).artist);
    }

    @Test
    public void modoTudoAchaPorNumeroEPalavrasMisturadas() {
        MainActivity a = start();
        type(a, "04920");
        assertEquals("I'M NOT IN LOVE", results(a).get(0).title);
        type(a, "adele hello");
        assertEquals("24376", results(a).get(0).number);
    }

    @Test
    public void tituloComecandoComTextoVemPrimeiro() {
        MainActivity a = start();
        mode(a, R.id.mode_song);
        type(a, "hello");
        assertTrue(results(a).get(0).title.startsWith("HELLO"));
    }

    @Test
    public void aceitaErroDeDigitacao() {
        MainActivity a = start();
        mode(a, R.id.mode_song);
        type(a, "bohemian rapsody");
        assertEquals("18024", results(a).get(0).number);
        assertTrue(((TextView) a.findViewById(R.id.count)).getText().toString().startsWith("Nada igual"));
    }

    @Test
    public void semResultadoMostraMensagem() {
        MainActivity a = start();
        type(a, "xyzxyzxyz");
        assertEquals(0, results(a).size());
        assertEquals(View.VISIBLE, a.findViewById(R.id.empty).getVisibility());
        assertEquals("Nenhum resultado", ((TextView) a.findViewById(R.id.count)).getText().toString());
    }

    @Test
    public void toqueCopiaONumero() {
        MainActivity a = start();
        type(a, "dancing queen");
        ListView list = a.findViewById(R.id.list);
        View row = list.getChildAt(0);
        long t = SystemClock.uptimeMillis();
        float x = row.getWidth() / 2f, y = row.getTop() + row.getHeight() / 2f;
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0);
        list.dispatchTouchEvent(down);
        list.dispatchTouchEvent(up);
        ShadowLooper.idleMainLooper();
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
        assertEquals("04574", cm.getPrimaryClip().getItemAt(0).getText().toString());
    }

    // ----------------------------------------------------- capturas de tela

    @Test
    public void capturaTelaInicial() throws Exception {
        screenshot(start(), "tela-inicial.png");
    }

    @Test
    public void capturaBusca() throws Exception {
        MainActivity a = start();
        mode(a, R.id.mode_artist);
        type(a, "queen");
        screenshot(a, "busca-cantor.png");
    }

    @Test
    public void capturaBuscaSemAcento() throws Exception {
        MainActivity a = start();
        type(a, "cancao do mar");
        assertEquals("06363", results(a).get(0).number);
        mode(a, R.id.mode_artist);
        type(a, "beyonce");
        assertEquals("Beyoncé", results(a).get(0).artist);
        screenshot(a, "busca-sem-acento.png");
    }

    private static void genre(MainActivity a, String label) {
        android.widget.RadioGroup g = a.findViewById(R.id.genres);
        for (int i = 0; i < g.getChildCount(); i++) {
            RadioButton b = (RadioButton) g.getChildAt(i);
            if (b.getText().toString().equals(label)) {
                tap(b);
                assertTrue("o estilo não ficou marcado: " + label, b.isChecked());
                return;
            }
        }
        throw new AssertionError("filtro não encontrado: " + label);
    }

    @Test
    public void filtroDeEstilo() throws Exception {
        MainActivity a = start();
        android.widget.RadioGroup g = a.findViewById(R.id.genres);
        assertEquals("Todos os estilos", ((RadioButton) g.getChildAt(0)).getText().toString());
        assertTrue(g.getChildCount() >= 12);
        genre(a, "Rock");
        List<Song> r = results(a);
        assertTrue(r.size() > 1000);
        for (Song s : r) assertTrue(s.hasGenre("rock"));
        assertTrue(((TextView) a.findViewById(R.id.count)).getText().toString().startsWith("Rock: "));
        // Estilo + busca.
        type(a, "bon jovi");
        assertEquals("Bon Jovi", results(a).get(0).artist);
        // Com o estilo Rock marcado, "adele" ainda acha a Adele e avisa que é de outro estilo.
        type(a, "adele");
        assertEquals("Adele", results(a).get(0).artist);
        assertEquals("Nada em Rock · 17 resultados em outros estilos",
                ((TextView) a.findViewById(R.id.count)).getText().toString());
        type(a, "");
        genre(a, "Românticas");
        screenshot(a, "estilo-romanticas.png");
        genre(a, "Todos os estilos");
        assertEquals(9157, results(a).size());
    }

    @Test
    public void estiloSobreviveAGirarATela() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        genre(c.get(), "Latinas");
        int latinas = results(c.get()).size();
        c.recreate();
        ShadowLooper.idleMainLooper();
        assertEquals(latinas, results(c.get()).size());
    }

    @Test
    public void listaNacional() throws Exception {
        MainActivity a = start();
        mode(a, R.id.list_nac);
        assertEquals(5237, results(a).size());
        for (Song s : results(a)) assertTrue(s.national);
        android.widget.RadioGroup g = a.findViewById(R.id.genres);
        assertEquals("Sertanejo", ((RadioButton) g.getChildAt(1)).getText().toString());
        type(a, "marilia mendonca");
        assertTrue(results(a).get(0).artist.startsWith("Marília Mendonça"));
        type(a, "bruno e marrone");
        assertTrue(results(a).get(0).artist.matches("Bruno [&e] Marrone"));
        screenshot(a, "busca-nacional.png");
        type(a, "");
        genre(a, "Sertanejo");
        for (Song s : results(a)) assertTrue(s.national && s.hasGenre("sertanejo"));
        screenshot(a, "lista-nacional-sertanejo.png");
    }

    @Test
    public void internacionaisSemResultadoMostraNacionais() {
        MainActivity a = start();
        mode(a, R.id.list_int);
        type(a, "marilia mendonca");
        assertTrue(results(a).get(0).national);
        assertTrue(((TextView) a.findViewById(R.id.count)).getText().toString()
                .startsWith("Nada nas internacionais · "));
    }

    @Test
    public void listaSobreviveAGirarATela() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        mode(c.get(), R.id.list_nac);
        genre(c.get(), "Forró e Piseiro");
        int n = results(c.get()).size();
        c.recreate();
        ShadowLooper.idleMainLooper();
        assertEquals(n, results(c.get()).size());
        for (Song s : results(c.get())) assertTrue(s.national && s.hasGenre("forro"));
    }

    @Test
    @Config(qualifiers = "+night")
    public void capturaModoEscuro() throws Exception {
        MainActivity a = start();
        mode(a, R.id.mode_song);
        type(a, "love");
        screenshot(a, "modo-escuro.png");
    }

    private static void screenshot(MainActivity a, String name) throws Exception {
        String dir = System.getProperty("screenshots.dir");
        if (dir == null) return;
        View root = a.getWindow().getDecorView();
        ShadowLooper.idleMainLooper();
        Bitmap bmp = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bmp));
        File out = new File(dir, name);
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }
}
