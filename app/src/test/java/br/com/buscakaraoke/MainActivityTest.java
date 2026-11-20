package br.com.buscakaraoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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

    private static List<MainActivity.Song> results(MainActivity a) {
        ListView list = a.findViewById(R.id.list);
        List<MainActivity.Song> out = new ArrayList<>();
        for (int i = 0; i < list.getAdapter().getCount(); i++) {
            out.add((MainActivity.Song) list.getAdapter().getItem(i));
        }
        return out;
    }

    private static void type(MainActivity a, String text) {
        ((EditText) a.findViewById(R.id.search)).setText(text);
        ShadowLooper.idleMainLooper();
    }

    private static void mode(MainActivity a, int id) {
        ((RadioButton) a.findViewById(id)).performClick();
        ShadowLooper.idleMainLooper();
    }

    @Test
    public void carregaTodasAsMusicasDoPdf() {
        MainActivity a = start();
        assertEquals(3920, results(a).size());
        assertEquals("3.920 músicas internacionais",
                ((TextView) a.findViewById(R.id.subtitle)).getText().toString());
    }

    @Test
    public void buscaPorMusica() {
        MainActivity a = start();
        mode(a, R.id.mode_song);
        type(a, "dancing queen");
        List<MainActivity.Song> r = results(a);
        assertEquals("04574", r.get(0).number);
        assertEquals("ABBA", r.get(0).artist);
        // Busca por música não deve olhar o nome do cantor.
        type(a, "abba");
        assertEquals(0, results(a).size());
    }

    @Test
    public void buscaPorCantor() {
        MainActivity a = start();
        mode(a, R.id.mode_artist);
        type(a, "adele");
        List<MainActivity.Song> r = results(a);
        assertEquals(17, r.size());
        for (MainActivity.Song s : r) assertTrue(s.artist, s.artist.toLowerCase().contains("adele"));
        // Participações ("feat.") também aparecem.
        type(a, "katy perry");
        boolean feat = false;
        for (MainActivity.Song s : results(a)) feat |= s.artist.equals("3OH!3 feat. Katy Perry");
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
    public void semResultadoMostraMensagem() {
        MainActivity a = start();
        type(a, "xyzxyzxyz");
        assertEquals(0, results(a).size());
        assertEquals(View.VISIBLE, a.findViewById(R.id.empty).getVisibility());
        assertEquals("0 resultados", ((TextView) a.findViewById(R.id.count)).getText().toString());
    }

    @Test
    public void toqueCopiaONumero() {
        MainActivity a = start();
        type(a, "dancing queen");
        ListView list = a.findViewById(R.id.list);
        list.performItemClick(null, 0, 0);
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
