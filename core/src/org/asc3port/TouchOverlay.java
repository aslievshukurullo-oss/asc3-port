package org.asc3port;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Экранные кнопки для горизонтального режима (480x320, y-down).
 * D-pad и кнопки видны ТОЛЬКО в STATE_GAME (gameMode=true).
 * В меню/заставке — только обработка кликов по меню.
 */
public class TouchOverlay {
    private static final int MAX_POINTERS = 10;

    private static final class Btn {
        final String label;
        final int bit;
        final float cx, cy, r;
        Btn(String l, int b, float x, float y, float r) {
            label = l; bit = b; cx = x; cy = y; this.r = r;
        }
    }

    // Левая панель: крестовина + * #
    // Правая панель: OK (FIRE), цифры, левый/правый софт
    private final Btn[] btns = {
        new Btn("^",  Keys.UP,     60,  188, 26),
        new Btn("v",  Keys.DOWN,   60,  276, 26),
        new Btn("<",  Keys.LEFT,   18,  232, 26),
        new Btn(">",  Keys.RIGHT, 102,  232, 26),
        new Btn("OK", Keys.FIRE,  420,  240, 38),
        new Btn("1",  Keys.NUM1,  378,  148, 20),
        new Btn("5",  Keys.NUM5,  420,  148, 20),
        new Btn("3",  Keys.NUM3,  462,  148, 20),
        new Btn("7",  Keys.NUM7,  378,  190, 20),
        new Btn("9",  Keys.NUM9,  462,  190, 20),
        new Btn("L",  Keys.SOFT_L, 378, 30,  22),
        new Btn("R",  Keys.SOFT_R, 462, 30,  22),
        new Btn("*",  Keys.STAR,   30,  30,  22),
        new Btn("#",  Keys.POUND,  90,  30,  22),
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
                    float rr = b.r + 8;
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
            sr.setColor(on ? 0.85f : 0.20f,
                        on ? 0.75f : 0.22f,
                        on ? 0.25f : 0.28f, 0.80f);
            sr.circle(b.cx, b.cy, b.r);
        }
        sr.end();
        // Обводка
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0.55f, 0.55f, 0.60f, 1f);
        for (Btn b : btns) sr.circle(b.cx, b.cy, b.r);
        sr.end();
        // Метки
        sb.begin();
        font.setColor(Color.WHITE);
        for (Btn b : btns) {
            float fw = b.label.length() * 5f;
            font.draw(sb, b.label, b.cx - fw, b.cy + 5);
        }
        sb.end();
    }
}
