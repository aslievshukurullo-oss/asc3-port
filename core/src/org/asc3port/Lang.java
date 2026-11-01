package org.asc3port;

import java.util.HashMap;
import java.util.Map;

/**
 * Локализация интерфейса: русский и английский языки.
 * Переключается тапом по кнопке [RU]/[EN] в меню.
 *
 * Использование:
 *   Lang.get("key")        — получить строку на текущем языке
 *   Lang.menuItem(i)       — название пункта меню i
 *   Lang.toggleLang()      — переключить язык
 *   Lang.currentLang()     — "RU" или "EN"
 */
public class Lang {

    private static boolean russian = false; // по умолчанию English

    private static final Map<String, String> EN = new HashMap<String, String>();
    private static final Map<String, String> RU = new HashMap<String, String>();

    // Пункты меню
    private static final String[][] MENU_EN = {
        {"NEW GAME"},
        {"CONTINUE"},
        {"OPTIONS"},
        {"HELP"},
        {"ABOUT"},
    };
    private static final String[][] MENU_RU = {
        {"НОВАЯ ИГРА"},
        {"ПРОДОЛЖИТЬ"},
        {"НАСТРОЙКИ"},
        {"ПОМОЩЬ"},
        {"ОБ ИГРЕ"},
    };

    static {
        // Заставка
        EN.put("tap_continue",  "Tap to continue");
        RU.put("tap_continue",  "Нажмите чтобы продолжить");

        // Меню
        EN.put("menu_hint",     "TAP to select  TAP again to confirm");
        RU.put("menu_hint",     "ТАП — выбор   ТАП снова — подтвердить");

        // Игровой экран
        EN.put("game_hint",     "L/R: move  OK: jump  Back: menu");
        RU.put("game_hint",     "Л/П: движение  OK: прыжок  Назад: меню");

        // HUD
        EN.put("loading",       "Loading...");
        RU.put("loading",       "Загрузка...");

        EN.put("no_sprite",     "No sprite loaded");
        RU.put("no_sprite",     "Спрайт не загружен");

        // Диалоги / сообщения
        EN.put("coming_soon",   "Coming soon");
        RU.put("coming_soon",   "Скоро будет");

        EN.put("about_text",    "AC3 J2ME port by LibGDX");
        RU.put("about_text",    "Порт AC3 J2ME на LibGDX");

        EN.put("options_text",  "Options coming soon");
        RU.put("options_text",  "Настройки скоро будут");

        EN.put("help_text",     "Use D-pad or touch to move");
        RU.put("help_text",     "Используй джойстик или касание");

        // Бонусно: уведомление о языке
        EN.put("lang_switched", "English");
        RU.put("lang_switched", "Русский");
    }

    /** Получить строку по ключу на текущем языке. */
    public static String get(String key) {
        Map<String, String> map = russian ? RU : EN;
        String val = map.get(key);
        return val != null ? val : key; // возвращаем сам ключ если перевод не найден
    }

    /** Название пункта меню по индексу. */
    public static String menuItem(int i) {
        String[][] items = russian ? MENU_RU : MENU_EN;
        if (i < 0 || i >= items.length) return "???";
        return items[i][0];
    }

    /** Переключить язык. */
    public static void toggleLang() {
        russian = !russian;
    }

    /** Текущий язык как строка для отображения на кнопке. */
    public static String currentLang() {
        return russian ? "RU" : "EN";
    }

    /** Установить язык явно. */
    public static void setRussian(boolean ru) {
        russian = ru;
    }

    public static boolean isRussian() {
        return russian;
    }
}
