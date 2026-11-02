package org.asc3port;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Система боя: проверяет перекрытие хитбоксов атак и распределяет урон.
 *
 * Вызывается каждый кадр из AscGame.updateGame():
 *   combat.update(player, enemies);
 *
 * Урон:
 *   Игрок → враг:  1 очко (LIGHT), 2 очка (HEAVY)
 *   Враг  → игрок: 1 очко
 */
public class CombatSystem {

    // Размеры хитбокса персонажей для проверки перекрытия
    private static final float OVERLAP_EPSILON = 2f;

    private final List<Enemy> enemies = new ArrayList<Enemy>();

    /**
     * Позиции врагов для каждого уровня (levelNum → {{x,y}, ...}).
     * Координаты в пикселях, y = ноги врага (нижняя граница хитбокса).
     * Генерируются на основе данных тайловой карты.
     *
     * Индексы соответствуют LevelManager.LEVEL_NUMS:
     *   levelNum 1 → raw/12, levelNum 2 → raw/13, ... levelNum 9 → raw/20
     */
    private static final float[][][] LEVEL_ENEMY_POSITIONS = {
        // levelNum 1 (raw/12, 9700x3100px)
        { {1010,1720},{2070,2740},{3130,2240},{4190,2420},{5250,2560},{6310,2660},{7370,2860},{8430,2880} },
        // levelNum 2 (raw/13, 9900x3460px)
        { {1050,2120},{2150,2080},{3250,1660},{4350,2580},{5450,2420},{6550,2720},{7650,2660},{8750,2800} },
        // levelNum 3 (raw/14, 6400x2900px)
        { {650,2460},{1350,2280},{2050,2340},{2750,2540},{3450,2540},{4150,2480},{4850,2760},{5550,2200} },
        // levelNum 5 (raw/16, 8800x2200px)
        { {910,1940},{1870,2000},{2830,2060},{3790,1400},{4750,1240},{5710,1060},{6670,1380},{7630,1060} },
        // levelNum 6 (raw/17, 9200x2400px)
        { {970,2020},{1990,1680},{3010,1460},{4030,1740},{5050,1440},{6070,1700},{7090,1980},{8110,1300} },
        // levelNum 8 (raw/19, 9200x1400px)
        { {970,480},{1990,560},{3010,1160},{4030,580},{5050,660},{7090,680},{8110,580} },
        // levelNum 9 (raw/20, 9000x1200px)
        { {990,420},{3950,740},{6010,880} },
    };

    /** Добавить врага на уровень. */
    public void addEnemy(Enemy e) {
        enemies.add(e);
    }

    /** Удалить всех врагов (при смене уровня). */
    public void clear() {
        for (Enemy e : enemies) e.dispose();
        enemies.clear();
    }

    /** Список врагов для рендера. */
    public List<Enemy> getEnemies() {
        return enemies;
    }

    /**
     * Обновить всех врагов и проверить коллизии.
     * Вызывать после player.update(), до рендера.
     */
    public void update(float dt, TileMap map, Player player) {
        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy e = it.next();
            e.update(dt, map, player);

            if (e.removed) {
                e.dispose();
                it.remove();
                continue;
            }

            // Игрок атакует врага
            if (!player.isDead) {
                float[] atkBox = player.getAttackBox();
                if (atkBox != null && overlaps(atkBox, e.x, e.y, Enemy.HIT_W, Enemy.HIT_H)) {
                    int dmg = (player.attackState == Player.AttackState.HEAVY) ? 2 : 1;
                    e.takeDamage(dmg, player.x);
                }
            }

            // Враг атакует игрока
            if (!player.isDead) {
                float[] eAtkBox = e.getAttackBox();
                if (eAtkBox != null && overlaps(eAtkBox, player.x, player.y, Player.HIT_W, Player.HIT_H)) {
                    player.takeDamage(1, e.x);
                }
            }
        }
    }

    /**
     * Проверить пересечение прямоугольника атаки [ax,ay,aw,ah]
     * с хитбоксом цели (центр-ноги: tx,ty, ширина tw, высота th).
     */
    private boolean overlaps(float[] atk, float tx, float ty, float tw, float th) {
        // atk = {x, y, w, h} — левый верхний угол
        float ax = atk[0], ay = atk[1], aw = atk[2], ah = atk[3];
        // Хитбокс цели: центр по X = tx, ноги = ty, голова = ty - th
        float bx = tx - tw / 2f, by = ty - th;
        float bw = tw, bh = th;

        return ax < bx + bw - OVERLAP_EPSILON
            && ax + aw > bx + OVERLAP_EPSILON
            && ay < by + bh - OVERLAP_EPSILON
            && ay + ah > by + OVERLAP_EPSILON;
    }

    /**
     * Расставить врагов на уровне по данным из LEVEL_ENEMY_POSITIONS.
     *
     * @param levelIndex индекс уровня (0..6) из LevelManager
     * @param spawnX     позиция спавна игрока X (fallback, если нет данных)
     * @param spawnY     позиция спавна игрока Y (fallback)
     */
    public void spawnLevelEnemies(int levelIndex, float spawnX, float spawnY) {
        clear();
        if (levelIndex >= 0 && levelIndex < LEVEL_ENEMY_POSITIONS.length) {
            float[][] positions = LEVEL_ENEMY_POSITIONS[levelIndex];
            for (float[] pos : positions) {
                addEnemy(new Enemy(pos[0], pos[1]));
            }
        } else {
            // Fallback: 3 врага правее старта
            for (int i = 0; i < 3; i++) {
                addEnemy(new Enemy(spawnX + 180 + i * 120, spawnY));
            }
        }
    }

    /**
     * Старый метод — оставлен для совместимости.
     * @deprecated Используй spawnLevelEnemies(levelIndex, x, y)
     */
    @Deprecated
    public void spawnLevelEnemies(float spawnX, float spawnY) {
        spawnLevelEnemies(-1, spawnX, spawnY);
    }
}
