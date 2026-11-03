package org.asc3port;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Загружает диалоги из EN_00X.json и управляет очередью реплик для уровня.
 *
 * Маппинг уровней (levelIndex 0..6) → секции JSON:
 *   0→2, 1→3, 2→5, 3→6, 4→8, 5→7, 6→9
 *
 * Использование:
 *   manager.loadForLevel(levelIndex);
 *   // в update():
 *   manager.update(dt, input);
 *   // в draw():
 *   if (manager.isShowing()) manager.draw(batch, font, screenW, screenH);
 */
public class DialogManager {

    private static final int[] LEVEL_SECTIONS = { 2, 3, 5, 6, 8, 7, 9 };

    // Авто-показ реплики через X секунд после старта уровня
    private static final float[] TRIGGER_TIMES = {
        3f,  // level 0: детство, прятки
        5f,  // level 1: деревня
        4f,  // level 2: освобождение
        3f,  // level 3: Бен Франклин
        5f,  // level 4: инфильтрация
        3f,  // level 5: корабль
        2f,  // level 6: финал / Чарльз Ли
    };

    private final List<DialogLine> lines = new ArrayList<DialogLine>();
    private int     cursor       = 0;
    private boolean showing      = false;
    private float   autoTimer    = 0;   // время до первой авто-реплики
    private float   holdTimer    = 0;   // время показа текущей реплики
    private boolean waitForInput = true;
    private static final float AUTO_ADVANCE = 3.5f; // сек на реплику без тапа
    private int currentLevel = -1;

    public void loadForLevel(int levelIndex) {
        lines.clear();
        cursor    = 0;
        showing   = false;
        holdTimer = 0;
        currentLevel = levelIndex;

        if (levelIndex < 0 || levelIndex >= LEVEL_SECTIONS.length) return;
        int section = LEVEL_SECTIONS[levelIndex];
        float trigger = (levelIndex < TRIGGER_TIMES.length) ? TRIGGER_TIMES[levelIndex] : 4f;
        autoTimer = trigger;

        String path = "strings/EN_00" + section + ".json";
        try {
            JsonValue root = new JsonReader().parse(Gdx.files.internal(path));
            JsonValue arr  = root.get("strings");
            if (arr != null) {
                for (JsonValue v = arr.child; v != null; v = v.next) {
                    String raw = v.asString();
                    if (raw == null || raw.isEmpty()) continue;
                    DialogLine dl = new DialogLine(raw);
                    if (!dl.isHint && !dl.text.isEmpty()) {
                        lines.add(dl);
                    }
                }
            }
        } catch (Exception e) {
            // файл не найден или ошибка парсинга — диалоги отключены для уровня
        }
    }

    /**
     * @param input  текущий игровой ввод (Keys.FIRE / Keys.NUM5 = пропуск реплики)
     */
    public void update(float dt, int input) {
        if (lines.isEmpty()) return;

        if (!showing) {
            if (autoTimer > 0) {
                autoTimer -= dt;
                if (autoTimer <= 0) showNext();
            }
            return;
        }

        holdTimer += dt;
        boolean skip = ((input & (Keys.FIRE | Keys.NUM5)) != 0);
        if (skip || holdTimer >= AUTO_ADVANCE) {
            showNext();
        }
    }

    private void showNext() {
        if (cursor >= lines.size()) {
            showing = false;
            return;
        }
        showing   = true;
        holdTimer = 0;
        cursor++;
    }

    public boolean isShowing() {
        return showing && cursor > 0 && cursor <= lines.size();
    }

    public DialogLine current() {
        if (!isShowing()) return null;
        return lines.get(cursor - 1);
    }

    /** Форсированно показать следующую реплику (тап по диалогу). */
    public void advance() {
        if (isShowing()) showNext();
    }

    /** Полностью закрыть диалог. */
    public void dismiss() {
        showing = false;
    }
}
