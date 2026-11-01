package org.asc3port;

/**
 * Камера, следящая за игроком.
 * Удерживает игрока в центре экрана, не выходя за границы карты.
 *
 * Координаты y-down, как у камеры LibGDX (setToOrtho(true)).
 */
public class GameCamera {

    /** Текущая позиция камеры (левый-верхний угол видимой области) */
    public float x, y;

    /** Размер видимой области (обновляется при resize) */
    private int viewW, viewH;

    /** Размер карты в пикселях */
    private final int mapPixelW, mapPixelH;

    /** Плавность следования (0=мгновенно, 1=не движется, ~0.1 = плавно) */
    private static final float LERP = 0.12f;

    /** Обновить размер видимой области (вызывать при resize). */
    public void setViewSize(int w, int h) {
        viewW = w;
        viewH = h;
        clamp();
    }

    public GameCamera(int viewW, int viewH, int mapPixelW, int mapPixelH) {
        this.viewW = viewW;
        this.viewH = viewH;
        this.mapPixelW = mapPixelW;
        this.mapPixelH = mapPixelH;
    }

    /**
     * Обновить позицию камеры так чтобы игрок был по центру.
     * @param playerX x игрока (центр)
     * @param playerY y игрока (низ)
     */
    public void update(float playerX, float playerY) {
        // Целевая позиция камеры — игрок по центру экрана
        float targetX = playerX - viewW / 2f;
        float targetY = playerY - viewH / 2f;

        // Плавное следование
        x += (targetX - x) * LERP;
        y += (targetY - y) * LERP;

        // Ограничиваем границами карты
        clamp();
    }

    /** Мгновенно переместить камеру на игрока (для телепорта/начала уровня). */
    public void snapTo(float playerX, float playerY) {
        x = playerX - viewW / 2f;
        y = playerY - viewH / 2f;
        clamp();
    }

    private void clamp() {
        float maxX = Math.max(0, mapPixelW - viewW);
        float maxY = Math.max(0, mapPixelH - viewH);
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x > maxX) x = maxX;
        if (y > maxY) y = maxY;
    }
}
