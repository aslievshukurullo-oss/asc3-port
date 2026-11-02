package org.asc3port;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/**
 * Система чекпоинтов.
 *
 * Чекпоинт срабатывает когда игрок проходит определённый X (каждые INTERVAL px).
 * Позиция сохраняется через LibGDX Preferences и восстанавливается при CONTINUE.
 */
public class Checkpoint {

    private static final String PREFS      = "asc3port";
    private static final String KEY_X      = "checkpoint_x";
    private static final String KEY_Y      = "checkpoint_y";
    private static final String KEY_LEVEL  = "checkpoint_level";
    private static final String KEY_EXISTS = "checkpoint_exists";

    // Сохраняем чекпоинт каждые N пикселей вправо
    public static final float INTERVAL = 200f;

    private float lastSavedX = 0f;

    /**
     * Вызывать каждый кадр в updateGame().
     * Возвращает true если был сохранён новый чекпоинт.
     */
    public boolean update(Player player, int levelNum) {
        if (player == null || player.isDead) return false;
        if (player.x > lastSavedX + INTERVAL) {
            save(player.x, player.y, levelNum);
            lastSavedX = player.x;
            return true;
        }
        return false;
    }

    /** Сохранить чекпоинт явно. */
    public void save(float x, float y, int levelNum) {
        try {
            Preferences p = Gdx.app.getPreferences(PREFS);
            p.putFloat(KEY_X, x);
            p.putFloat(KEY_Y, y);
            p.putInteger(KEY_LEVEL, levelNum);
            p.putBoolean(KEY_EXISTS, true);
            p.flush();
        } catch (Exception e) { /* игнорируем */ }
    }

    /** Есть ли сохранённый чекпоинт? */
    public static boolean hasSave() {
        try {
            return Gdx.app.getPreferences(PREFS).getBoolean(KEY_EXISTS, false);
        } catch (Exception e) { return false; }
    }

    /** Загрузить сохранённую позицию. Возвращает {x, y, level} или null. */
    public static float[] load() {
        try {
            Preferences p = Gdx.app.getPreferences(PREFS);
            if (!p.getBoolean(KEY_EXISTS, false)) return null;
            return new float[]{
                p.getFloat(KEY_X, 0),
                p.getFloat(KEY_Y, 0),
                p.getInteger(KEY_LEVEL, 1)
            };
        } catch (Exception e) { return null; }
    }

    /** Удалить сохранение (при начале новой игры). */
    public static void clear() {
        try {
            Preferences p = Gdx.app.getPreferences(PREFS);
            p.putBoolean(KEY_EXISTS, false);
            p.flush();
        } catch (Exception e) { /* игнорируем */ }
    }
}
