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
     * Расставить врагов на уровне вблизи игрока.
     * Временная заглушка — размещаем 3 врагов правее спавна.
     */
    public void spawnLevelEnemies(float spawnX, float spawnY) {
        clear();
        // Расставляем врагов на расстоянии 200-400px правее старта
        for (int i = 0; i < 3; i++) {
            float ex = spawnX + 180 + i * 120;
            addEnemy(new Enemy(ex, spawnY));
        }
    }
}
