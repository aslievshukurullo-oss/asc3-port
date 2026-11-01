package org.asc3port;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Gameloft "ASprite" (class `a` of the original). Data comes from tools/pack_assets.py:
 * modules (bitmaps in an atlas) -> frame-modules -> frames -> animation frames -> animations.
 * All coordinates use the original's y-down convention; the camera must be y-down (setToOrtho(true)).
 * Transform bits: 1 = flip X, 2 = flip Y, 4 = rotate 90 clockwise (applied after flips).
 */
public class ASprite implements Disposable {
    public static final class Module { public int w, h, type, sx, sy; public TextureRegion region; }
    public static final class FrameModule { public int m, x, y, tr; }
    public static final class Frame { public int n, first; }
    public static final class AnimFrame { public int frame, dur, x, y, tr; }
    public static final class Anim { public int n, first; }

    public final String name;
    public Texture atlas;
    public Module[] modules;
    public FrameModule[] fms;
    public Frame[] frames;
    public AnimFrame[] afs;
    public Anim[] anims;

    public ASprite(String name) {
        this.name = name;
        JsonValue j = new JsonReader().parse(Gdx.files.internal("sprites/" + name + ".json"));
        JsonValue a = j.get("atlas");
        if (a != null && a.isString()) atlas = new Texture(Gdx.files.internal("sprites/" + a.asString()));
        modules = new Module[j.get("modules").size];
        int i = 0;
        for (JsonValue v : j.get("modules")) {
            Module m = new Module();
            m.w = v.getInt("w"); m.h = v.getInt("h"); m.type = v.getInt("type");
            m.sx = v.getInt("x"); m.sy = v.getInt("y");
            if (atlas != null && m.type == 0 && m.sx >= 0 && m.w > 0 && m.h > 0) {
                m.region = new TextureRegion(atlas, m.sx, m.sy, m.w, m.h);
                m.region.flip(false, true); // upright under a y-down camera
            }
            modules[i++] = m;
        }
        fms = new FrameModule[j.get("frameModules").size];
        i = 0;
        for (JsonValue v : j.get("frameModules")) {
            FrameModule f = new FrameModule();
            f.m = v.getInt("m"); f.x = v.getInt("x"); f.y = v.getInt("y"); f.tr = v.getInt("tr");
            fms[i++] = f;
        }
        frames = new Frame[j.get("frames").size];
        i = 0;
        for (JsonValue v : j.get("frames")) {
            Frame f = new Frame(); f.n = v.getInt("n"); f.first = v.getInt("first"); frames[i++] = f;
        }
        afs = new AnimFrame[j.get("animFrames").size];
        i = 0;
        for (JsonValue v : j.get("animFrames")) {
            AnimFrame f = new AnimFrame();
            f.frame = v.getInt("frame"); f.dur = v.getInt("dur"); f.x = v.getInt("x"); f.y = v.getInt("y"); f.tr = v.getInt("tr");
            afs[i++] = f;
        }
        anims = new Anim[j.get("anims").size];
        i = 0;
        for (JsonValue v : j.get("anims")) {
            Anim an = new Anim(); an.n = v.getInt("n"); an.first = v.getInt("first"); anims[i++] = an;
        }
    }

    /** Draw one module with its top-left at (px, py). */
    public void drawModule(SpriteBatch b, int mi, float px, float py, int tr) {
        if (mi < 0 || mi >= modules.length) return;
        Module m = modules[mi];
        if (m.region == null) return;
        float sx = (tr & 1) != 0 ? -1f : 1f, sy = (tr & 2) != 0 ? -1f : 1f;
        if ((tr & 4) == 0) {
            b.draw(m.region, px, py, m.w / 2f, m.h / 2f, m.w, m.h, sx, sy, 0f);
        } else {
            // rotated result is h wide and w tall, top-left at (px, py)
            float cx = px + m.h / 2f, cy = py + m.w / 2f;
            b.draw(m.region, cx - m.w / 2f, cy - m.h / 2f, m.w / 2f, m.h / 2f, m.w, m.h, sx, sy, 90f);
        }
    }

    /** Draw frame `fi` with its origin at (ox, oy). Only bitmap modules are drawn (vector modules are TODO). */
    public void drawFrame(SpriteBatch b, int fi, float ox, float oy, int tr) {
        if (fi < 0 || fi >= frames.length) return;
        Frame f = frames[fi];
        for (int k = 0; k < f.n; k++) {
            int idx = f.first + k;
            if (idx >= fms.length) break;
            FrameModule fm = fms[idx];
            if (fm.m < 0 || fm.m >= modules.length) continue;
            Module m = modules[fm.m];
            float x = fm.x, y = fm.y;
            int t = fm.tr;
            if ((tr & 1) != 0) { x = -x - ((t & 4) != 0 ? m.h : m.w); t ^= 1; }
            if ((tr & 2) != 0) { y = -y - ((t & 4) != 0 ? m.w : m.h); t ^= 2; }
            drawModule(b, fm.m, ox + x, oy + y, t);
        }
    }

    public int animLength(int ai) {
        return ai >= 0 && ai < anims.length ? anims[ai].n : 0;
    }

    @Override
    public void dispose() { if (atlas != null) atlas.dispose(); }
}
