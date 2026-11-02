package org.asc3port;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/**
 * Рендер тайловой карты уровня.
 *
 * Формат данных (из анализа raw/12/):
 *   011.bin = 6 bytes → u16[3] = (width, height, 0) — размеры карты в тайлах
 *   009.bin = width*height bytes → u8 массив ID тайлов (1-based, 255=пустой)
 *   010.bin = второй слой (фон, тот же формат)
 *
 * Маппинг tile ID:
 *   255 = пустой тайл (не рисуем)
 *   1..N = frame (N-1) в тайловом паке (p8_002 = 240 frames)
 *
 * Каждый тайл = 16×16 пикселей.
 */
public class TileMap implements Disposable {

    public static final int TILE_SIZE = 20; // тайлы 20×20 пикселей (не 16!)
    public static final int EMPTY_TILE = 255;

    /** Ширина и высота карты в тайлах */
    public final int mapW, mapH;

    /** Слои: layer[0] = foreground (009.bin), layer[1] = background (010.bin) */
    private final byte[][] layers;

    /** Тайловые спрайты */
    private final ASprite tileSet;

    /** Номер уровня (1-9), соответствует raw/11+n/ */
    public final int levelNum;

    public TileMap(int levelNum) {
        this.levelNum = levelNum;
        String rawDir = "raw/" + (11 + levelNum) + "/";

        // Читаем размеры карты из 011.bin (3 × u16 little-endian)
        byte[] dimBytes = Gdx.files.internal(rawDir + "011.bin").readBytes();
        mapW = (dimBytes[0] & 0xFF) | ((dimBytes[1] & 0xFF) << 8);
        mapH = (dimBytes[2] & 0xFF) | ((dimBytes[3] & 0xFF) << 8);

        // Читаем тайловые слои
        layers = new byte[2][];
        layers[0] = Gdx.files.internal(rawDir + "009.bin").readBytes(); // foreground
        layers[1] = Gdx.files.internal(rawDir + "010.bin").readBytes(); // background

        // Загружаем тайловый спрайтовый пак
        tileSet = new ASprite("p8_002");
    }

    /**
     * Получить ID тайла на позиции (tx, ty) в слое layer.
     * Возвращает EMPTY_TILE(255) если вне границ.
     */
    public int getTile(int layer, int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= mapW || ty >= mapH) return EMPTY_TILE;
        int idx = ty * mapW + tx;
        if (idx >= layers[layer].length) return EMPTY_TILE;
        return layers[layer][idx] & 0xFF;
    }

    /**
     * Рендерить видимую область карты.
     *
     * @param batch    SpriteBatch (должен быть уже начат)
     * @param camX     левый край камеры в пикселях (world coords)
     * @param camY     верхний край камеры в пикселях (y-down)
     * @param viewW    ширина видимой области
     * @param viewH    высота видимой области
     */
    public void draw(SpriteBatch batch, float camX, float camY, int viewW, int viewH) {
        if (tileSet == null) return;

        // Диапазон тайлов для рендеринга (с запасом +1)
        int tileX0 = Math.max(0, (int)(camX / TILE_SIZE));
        int tileY0 = Math.max(0, (int)(camY / TILE_SIZE));
        int tileX1 = Math.min(mapW - 1, (int)((camX + viewW) / TILE_SIZE) + 1);
        int tileY1 = Math.min(mapH - 1, (int)((camY + viewH) / TILE_SIZE) + 1);

        // Рисуем сначала фон (layer 1), потом передний план (layer 0)
        for (int layer = 1; layer >= 0; layer--) {
            for (int ty = tileY0; ty <= tileY1; ty++) {
                for (int tx = tileX0; tx <= tileX1; tx++) {
                    int tileId = getTile(layer, tx, ty);
                    if (tileId == EMPTY_TILE || tileId == 0) continue;

                    int frameIdx = tileId - 1; // 1-based -> 0-based
                    if (frameIdx < 0 || frameIdx >= tileSet.frames.length) continue;

                    // Пиксельная позиция тайла (левый верхний угол)
                    float px = tx * TILE_SIZE - camX;
                    float py = ty * TILE_SIZE - camY;

                    drawTileFrame(batch, frameIdx, px, py);
                }
            }
        }
    }

    /**
     * Рисует frame тайла с верхним-левым углом ячейки в (px, py).
     * bounds = [bx, by, bw, bh] — смещение и размер спрайта внутри ячейки TILE_SIZE×TILE_SIZE.
     */
    private void drawTileFrame(SpriteBatch batch, int frameIdx, float px, float py) {
        ASprite.Frame frame = tileSet.frames[frameIdx];
        for (int fmi = frame.first; fmi < frame.first + frame.n; fmi++) {
            if (fmi >= tileSet.fms.length) break;
            ASprite.FrameModule fm = tileSet.fms[fmi];
            if (fm.m < 0 || fm.m >= tileSet.modules.length) continue;
            ASprite.Module mod = tileSet.modules[fm.m];
            if (mod.region == null) continue;

            // fm.x, fm.y = смещение модуля внутри frame (уже учитывает bounds)
            float mx = px + fm.x;
            float my = py + fm.y;

            boolean flipX = (fm.tr & 1) != 0;
            boolean flipY = (fm.tr & 2) != 0;

            if (flipX || flipY) {
                batch.draw(mod.region,
                    mx, my, mod.w / 2f, mod.h / 2f,
                    mod.w, mod.h,
                    flipX ? -1f : 1f, flipY ? -1f : 1f,
                    0f);
            } else {
                batch.draw(mod.region, mx, my, mod.w, mod.h);
            }
        }
    }

    /**
     * Находит стартовую позицию игрока — над первой твёрдой платформой слева.
     * @return float[]{x, y} в пикселях (y = ноги игрока, y-down)
     */
    public float[] findSpawn() {
        // Ищем первую колонку с твёрдым тайлом слева
        for (int tx = 0; tx < Math.min(20, mapW); tx++) {
            // Ищем снизу вверх первую твёрдую поверхность
            for (int ty = mapH - 1; ty >= 0; ty--) {
                int t = getTile(0, tx, ty);
                if (t != EMPTY_TILE && t != 0) {
                    // Стоим над этим тайлом
                    float spawnX = tx * TILE_SIZE + TILE_SIZE / 2f;
                    float spawnY = ty * TILE_SIZE; // ноги на верхнем крае тайла
                    return new float[]{ spawnX, spawnY };
                }
            }
        }
        // Запасной вариант
        return new float[]{ 3 * TILE_SIZE, 50 * TILE_SIZE };
    }

    /** Ширина карты в пикселях */
    public int getPixelWidth()  { return mapW * TILE_SIZE; }
    /** Высота карты в пикселях */
    public int getPixelHeight() { return mapH * TILE_SIZE; }

    @Override
    public void dispose() {
        if (tileSet != null) tileSet.dispose();
    }
}
