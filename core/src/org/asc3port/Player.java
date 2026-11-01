package org.asc3port;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/**
 * Игровой персонаж (Connor) с базовой физикой.
 *
 * Спрайт: p5_000 (1066 анимационных кадров Connor-а).
 * Анимации (индексы из оригинала):
 *   0 = стоять (idle)
 *   1 = ходьба
 *   2 = бег
 *   3 = прыжок
 *   4 = падение
 *   5 = атака
 *
 * Физика: AABB коллизия с тайловой картой, гравитация, прыжок.
 */
public class Player implements Disposable {

    // Физические константы (в пикселях/сек)
    private static final float WALK_SPEED  = 60f;
    private static final float RUN_SPEED   = 120f;
    private static final float JUMP_VY     = -300f;   // вверх = отрицательный y (y-down)
    private static final float GRAVITY     = 600f;    // вниз = положительный y
    private static final float MAX_FALL    = 400f;

    // Размер хитбокса (ширина, высота) в пикселях
    public static final int HIT_W = 14;
    public static final int HIT_H = 28;

    // Спрайт
    private ASprite sprite;
    private int animIdx = 0;
    private int animStep = 0;
    private float animTimer = 0;
    private static final float TICK = 1f / 25f;

    // Позиция (центр ног персонажа, пиксели, y-down)
    public float x, y;

    // Скорость
    public float vx, vy;

    // Состояние
    public boolean onGround = false;
    public boolean facingRight = true;

    // Индексы анимаций (будут определены после загрузки спрайта)
    private static final int ANIM_IDLE  = 0;
    private static final int ANIM_WALK  = 1;
    private static final int ANIM_RUN   = 2;
    private static final int ANIM_JUMP  = 3;
    private static final int ANIM_FALL  = 4;

    public Player(float startX, float startY) {
        x = startX;
        y = startY;
        try {
            sprite = new ASprite("p5_000");
        } catch (Exception e) {
            sprite = null;
        }
    }

    public void update(float dt, TileMap map, int input) {
        // Горизонтальное движение
        boolean moveLeft  = (input & Keys.LEFT)  != 0;
        boolean moveRight = (input & Keys.RIGHT) != 0;
        boolean run       = (input & Keys.NUM7)  != 0; // NUM7 = бег
        boolean jump      = (input & Keys.FIRE)  != 0;

        float speed = run ? RUN_SPEED : WALK_SPEED;

        vx = 0;
        if (moveLeft)  { vx = -speed; facingRight = false; }
        if (moveRight) { vx =  speed; facingRight = true;  }

        // Прыжок
        if (jump && onGround) {
            vy = JUMP_VY;
            onGround = false;
        }

        // Гравитация
        vy += GRAVITY * dt;
        if (vy > MAX_FALL) vy = MAX_FALL;

        // Движение + коллизии
        x += vx * dt;
        resolveHorizontal(map);
        y += vy * dt;
        resolveVertical(map);

        // Граница карты (не выходим за левый край)
        if (x < HIT_W / 2f) { x = HIT_W / 2f; vx = 0; }
        if (x > map.getPixelWidth() - HIT_W / 2f) x = map.getPixelWidth() - HIT_W / 2f;

        // Обновляем анимацию
        updateAnimation(dt);
    }

    private void resolveHorizontal(TileMap map) {
        float left  = x - HIT_W / 2f;
        float right = x + HIT_W / 2f;
        float top   = y - HIT_H;
        float bottom = y;

        int ts = TileMap.TILE_SIZE;
        // Проверяем тайлы слева и справа на уровне ног/середины/головы
        for (float checkY = top + 2; checkY <= bottom - 2; checkY += ts / 2f) {
            if (vx < 0) { // движемся влево
                int tx = (int)(left / ts);
                int ty = (int)(checkY / ts);
                if (isSolid(map, tx, ty)) {
                    x = (tx + 1) * ts + HIT_W / 2f;
                    vx = 0;
                    break;
                }
            } else if (vx > 0) { // движемся вправо
                int tx = (int)(right / ts);
                int ty = (int)(checkY / ts);
                if (isSolid(map, tx, ty)) {
                    x = tx * ts - HIT_W / 2f;
                    vx = 0;
                    break;
                }
            }
        }
    }

    private void resolveVertical(TileMap map) {
        float left  = x - HIT_W / 2f + 2;
        float right = x + HIT_W / 2f - 2;
        float bottom = y;
        float top    = y - HIT_H;
        int ts = TileMap.TILE_SIZE;

        if (vy > 0) { // падаем вниз
            int ty = (int)(bottom / ts);
            for (float cx = left; cx <= right; cx += ts / 2f) {
                int tx = (int)(cx / ts);
                if (isSolid(map, tx, ty)) {
                    y = ty * ts;         // ставим на землю
                    vy = 0;
                    onGround = true;
                    return;
                }
            }
            onGround = false;
        } else if (vy < 0) { // прыгаем вверх
            int ty = (int)(top / ts);
            for (float cx = left; cx <= right; cx += ts / 2f) {
                int tx = (int)(cx / ts);
                if (isSolid(map, tx, ty)) {
                    y = (ty + 1) * ts + HIT_H;
                    vy = 0;
                    break;
                }
            }
        }

        // Проверка земли под ногами
        if (vy >= 0) {
            int ty = (int)(bottom / ts) + 1;
            boolean groundBelow = false;
            for (float cx = left; cx <= right; cx += 4) {
                int tx = (int)(cx / ts);
                if (isSolid(map, tx, (int)(bottom / ts))) {
                    groundBelow = true; break;
                }
            }
            onGround = groundBelow;
        }
    }

    /** Является ли тайл на (tx, ty) твёрдым (не пустым). */
    private boolean isSolid(TileMap map, int tx, int ty) {
        int t0 = map.getTile(0, tx, ty);
        // ID 255 = пустой, 0 = нет тайла. Остальные = твёрдые.
        return t0 != TileMap.EMPTY_TILE && t0 != 0;
    }

    private void updateAnimation(float dt) {
        if (sprite == null || sprite.anims.length == 0) return;

        int newAnim;
        if (!onGround) {
            newAnim = (vy < 0) ? ANIM_JUMP : ANIM_FALL;
        } else if (vx != 0) {
            newAnim = (Math.abs(vx) > WALK_SPEED + 10) ? ANIM_RUN : ANIM_WALK;
        } else {
            newAnim = ANIM_IDLE;
        }

        // Ограничиваем animIdx кол-вом доступных анимаций
        newAnim = Math.min(newAnim, sprite.anims.length - 1);

        if (newAnim != animIdx) {
            animIdx = newAnim;
            animStep = 0;
            animTimer = 0;
        }

        ASprite.Anim an = sprite.anims[animIdx];
        if (an.n > 0) {
            animTimer += dt;
            int afIdx = an.first + animStep;
            if (afIdx < sprite.afs.length) {
                int dur = Math.max(1, sprite.afs[afIdx].dur);
                while (animTimer >= dur * TICK) {
                    animTimer -= dur * TICK;
                    animStep = (animStep + 1) % an.n;
                }
            }
        }
    }

    public void draw(SpriteBatch batch, float camX, float camY) {
        if (sprite == null) return;
        if (sprite.anims.length == 0 && sprite.frames.length == 0) return;

        // Позиция на экране (центр спрайта примерно = центр персонажа)
        float screenX = x - camX;
        float screenY = (y - HIT_H / 2f) - camY;

        ASprite.Anim an = (animIdx < sprite.anims.length) ? sprite.anims[animIdx] : null;
        if (an != null && an.n > 0) {
            int afIdx = an.first + animStep;
            if (afIdx < sprite.afs.length) {
                ASprite.AnimFrame af = sprite.afs[afIdx];
                int tr = af.tr;
                // Флип по X если смотрим влево
                if (!facingRight) tr ^= 1;
                sprite.drawFrame(batch, af.frame, screenX + af.x, screenY + af.y, tr);
            }
        } else if (sprite.frames.length > 0) {
            sprite.drawFrame(batch, 0, screenX, screenY, facingRight ? 0 : 1);
        }
    }

    @Override
    public void dispose() {
        if (sprite != null) sprite.dispose();
    }
}
