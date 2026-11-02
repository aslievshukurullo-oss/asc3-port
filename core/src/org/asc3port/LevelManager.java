package org.asc3port;

/**
 * Управление последовательностью уровней.
 *
 * Доступные уровни (raw/12..20, только те у кого есть 009/010/011.bin):
 *   Индекс 0 → levelNum 1 → raw/12/
 *   Индекс 1 → levelNum 2 → raw/13/
 *   Индекс 2 → levelNum 3 → raw/14/
 *   Индекс 3 → levelNum 5 → raw/16/
 *   Индекс 4 → levelNum 6 → raw/17/
 *   Индекс 5 → levelNum 8 → raw/19/
 *   Индекс 6 → levelNum 9 → raw/20/
 */
public class LevelManager {

    /** TileMap levelNum для каждого индекса уровня. */
    public static final int[] LEVEL_NUMS = { 1, 2, 3, 5, 6, 8, 9 };

    public static final int TOTAL_LEVELS = LEVEL_NUMS.length;

    private int currentIndex = 0;

    /** Текущий индекс уровня (0-based). */
    public int getIndex() { return currentIndex; }

    /** TileMap levelNum для текущего уровня. */
    public int getLevelNum() { return LEVEL_NUMS[currentIndex]; }

    /** Есть ли следующий уровень? */
    public boolean hasNext() { return currentIndex < TOTAL_LEVELS - 1; }

    /** Перейти на следующий уровень. Возвращает новый levelNum или -1 если конец. */
    public int next() {
        if (!hasNext()) return -1;
        currentIndex++;
        return getLevelNum();
    }

    /** Установить уровень по индексу. */
    public void setIndex(int idx) {
        currentIndex = Math.max(0, Math.min(idx, TOTAL_LEVELS - 1));
    }

    /** Найти индекс по levelNum. Возвращает 0 если не найден. */
    public static int indexOfLevelNum(int levelNum) {
        for (int i = 0; i < LEVEL_NUMS.length; i++) {
            if (LEVEL_NUMS[i] == levelNum) return i;
        }
        return 0;
    }

    /**
     * Триггер перехода на следующий уровень:
     * игрок достиг правого края карты (в пределах 2 тайлов от конца).
     */
    public static boolean isAtLevelEnd(Player player, TileMap map) {
        if (player == null || map == null) return false;
        return player.x >= map.getPixelWidth() - TileMap.TILE_SIZE * 2;
    }
}
