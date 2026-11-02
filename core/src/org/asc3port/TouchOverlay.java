package org.asc3port;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Экранные кнопки для горизонтального режима.
 *
 * Расположение (виртуальный экран 480×320, y-down):
 *
 *  Левая сторона — крестовина:
 *    Вверх:    (62, 185)
 *    Вниз:     (62, 285)
 *    Влево:    (16, 235)
 *    Вправо:  (108, 235)
 *
 *  Правая сторона — Action кнопки:
 *    OK (прыжок): (430, 245) r=38
 *    RUN:         (380, 195) r=28
 *
 *  Верхний правый угол — BACK:
 *    BACK (ESC):  (455, 22)  r=20
 *
 * D-pad и кнопки видны ТОЛЬКО в STATE_GAME (gameMode=true).
 */
public class TouchOverlay {
    private static final int MAX_POINTERS = 10;

    private static final class Btn {
        final String label;
        final int bit;
        final float cx, cy, r;
        final boolean important; // выделить яркостью?
        Btn(String l, int b, float x, float y, float r, boolean imp) {
            label = l; bit = b; cx = x; cy = y; this.r = r; important = imp;
        }
        Btn(String l, int b, float x, float y, float r) {
            this(l, b, x, y, r, false);
        }
    }

    // Кнопки
    private final Btn[] btns = {
        // D-pad (левая сторона)
        new Btn("^",    Keys.UP,     62,  185, 30),
        new Btn("v",    Keys.DOWN,   62,  285, 30),
        new Btn("<",    Keys.LEFT,   16,  235, 30),
        new Btn(">",    Keys.RIGHT, 108,  235, 30),
        // Действия (правая сторона)
        new Btn("JMP",  Keys.FIRE,  430,  255, 36, true),  // Прыжок (большая)
        new Btn("ATK",  Keys.NUM1,  380,  220, 26),        // Лёгкий удар
        new Btn("HIT",  Keys.NUM3,  380,  280, 26),        // Тяжёлый удар
        new Btn("RUN",  Keys.NUM7,  462,  200, 22),        // Бег
        // Навигация
        new Btn("BACK", Keys.SOFT_L, 455, 22,  20),        // ESC / меню
    };

    private final Vector2 tmp = new Vector2();
    private final Viewport viewport;
    public int held, pressed, released;

    /** Когда true — рисуем и обрабатываем D-pad. Когда false — скрыт. */
    public boolean gameMode = false;

    /** Координаты последнего касания (в виртуальном пространстве) для меню */
    public float touchX = -1, touchY = -1;
    public boolean touchJustDown = false;

    private boolean wasTouched = false;

    public TouchOverlay(Viewport vp) { viewport = vp; }

    public void update() {
        int now = 0;
        touchJustDown = false;
        touchX = -1; touchY = -1;

        boolean isTouched = Gdx.input.isTouched(0);

        // Определяем момент касания (just down) для меню
        if (isTouched && !wasTouched) {
            touchJustDown = true;
            tmp.set(Gdx.input.getX(0), Gdx.input.getY(0));
            viewport.unproject(tmp);
            touchX = tmp.x;
            touchY = tmp.y;
        }
        wasTouched = isTouched;

        if (gameMode) {
            // Обрабатываем нажатия кнопок только в игровом режиме
            for (int p = 0; p < MAX_POINTERS; p++) {
                if (!Gdx.input.isTouched(p)) continue;
                tmp.set(Gdx.input.getX(p), Gdx.input.getY(p));
                viewport.unproject(tmp);
                for (Btn b : btns) {
                    float dx = tmp.x - b.cx, dy = tmp.y - b.cy;
                    float rr = b.r + 8; // зона касания чуть больше визуальной
                    if (dx * dx + dy * dy <= rr * rr) now |= b.bit;
                }
            }
        }

        // Клавиатура (эмулятор / внешняя клавиатура) — всегда
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.UP))    now |= Keys.UP;
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.DOWN))  now |= Keys.DOWN;
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.LEFT))  now |= Keys.LEFT;
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.RIGHT)) now |= Keys.RIGHT;
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ENTER) ||
            Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SPACE)) now |= Keys.FIRE;
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT) ||
            Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT)) now |= Keys.NUM7;
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.Z)) now |= Keys.NUM1; // лёгкий удар
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.X)) now |= Keys.NUM3; // тяжёлый удар
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) now |= Keys.SOFT_L;

        pressed  = now & ~held;
        released = held & ~now;
        held = now;
    }

    public void draw(ShapeRenderer sr, SpriteBatch sb, BitmapFont font) {
        if (!gameMode) return; // Не рисуем D-pad в меню/заставке

        // Фоновые круги кнопок
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (Btn b : btns) {
            boolean on = (held & b.bit) != 0;
            if (b.important) {
                // OK кнопка — ярко красная
                sr.setColor(on ? 1.0f : 0.75f,
                            on ? 0.3f : 0.15f,
                            on ? 0.1f : 0.15f, 0.85f);
            } else {
                sr.setColor(on ? 0.80f : 0.18f,
                            on ? 0.70f : 0.20f,
                            on ? 0.20f : 0.26f, 0.75f);
            }
            sr.circle(b.cx, b.cy, b.r);
        }
        sr.end();

        // Обводка
        sr.begin(ShapeRenderer.ShapeType.Line);
        for (Btn b : btns) {
            boolean on = (held & b.bit) != 0;
            sr.setColor(on ? 1f : 0.5f, on ? 0.9f : 0.5f, on ? 0.3f : 0.55f, 1f);
            sr.circle(b.cx, b.cy, b.r);
        }
        sr.end();

        // Метки кнопок
        sb.begin();
        for (Btn b : btns) {
            boolean on = (held & b.bit) != 0;
            font.setColor(on ? Color.YELLOW : Color.WHITE);
            // Центрируем текст
            float fw = b.label.length() * 5.5f;
            font.draw(sb, b.label, b.cx - fw / 2f, b.cy + 5.5f);
        }
        sb.end();
    }
}
