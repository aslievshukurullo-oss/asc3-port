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
 * Оригинальное окно 240x320 по центру (x=120..360), панели управления по бокам.
 * Возвращает оригинальные битовые маски клавиш (см. Keys).
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
    // Правая панель: OK (FIRE), цифры 1 3 5 7 9, левый/правый софт
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

    public TouchOverlay(Viewport vp) { viewport = vp; }

    public void update() {
        int now = 0;
        for (int p = 0; p < MAX_POINTERS; p++) {
            if (!Gdx.input.isTouched(p)) continue;
            tmp.set(Gdx.input.getX(p), Gdx.input.getY(p));
            viewport.unproject(tmp);
            for (Btn b : btns) {
                float dx = tmp.x - b.cx, dy = tmp.y - b.cy;
                float rr = b.r + 8; // небольшой запас для пальца
                if (dx * dx + dy * dy <= rr * rr) now |= b.bit;
            }
        }
        // Клавиатура (эмулятор / внешняя клавиатура)
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
