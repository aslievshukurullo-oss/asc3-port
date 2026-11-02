package org.asc3port;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/**
 * Базовый враг — стражник.
 *
 * Поведение:
 *   PATROL  — ходит туда-сюда по платформе
 *   CHASE   — видит игрока (дальность 120px), бежит к нему
 *   ATTACK  — в радиусе 30px наносит удар каждые ~1.2 сек
 *   HIT     — принял урон, краткое оцепенение
 *   DEAD    — проигрывает анимацию смерти, затем исчезает
 *
 * Спрайт: p6_000 (стражник, 400+ кадров).
 * Если спрайт отсутствует — рисуется цветной прямоугольник (debug).
 */
public class Enemy implements Disposable {

    public enum State { PATROL, CHASE, ATTACK, HIT, DEAD }

    // Физика
    private static final float PATROL_SPEED = 55f;
    private static final float CHASE_SPEED  = 90f;
    private static final float GRAVITY      = 700f;
    private static final float MAX_FALL     = 500f;

    public static final int HIT_W = 14;
    public static final int HIT_H = 28;

    // Дальность агро и атаки
    private static final float DETECT_RANGE = 120f;
    private static final float ATTACK_RANGE =  32f;
    private static final float ATTACK_CD    =  1.2f; // cooldown между ударами
    private static final float ATTACK_DUR   =  0.45f;
    private static final float HIT_STUN     =  0.30f;
    private static final float DEAD_TIME    =  1.0f;

    // Атрибуты
    public float x, y;
    public float vx, vy;
    public boolean onGround = false;
    public boolean facingRight = true;
    public int health = 3;
    public State state = State.PATROL;
    public boolean removed = false; // пометить для удаления из списка

    // Таймеры
    private float stateTimer   = 0;
    private float attackCd     = 0;
    private float patrolTimer  = 0;
    private static final float PATROL_TURN = 2.0f; // сек до разворота

    // Хитбокс удара (активен в середине анимации атаки)
    public boolean attackHitActive = false;
    private static final float ATK_W = 20f;
    private static final float ATK_H = 18f;

    // Спрайт
    private ASprite sprite;
    private int animIdx  = 0;
    private int animStep = 0;
    private float animTimer = 0;
    private static final float TICK = 1f / 25f;

    // Индексы анимаций (p5_001 — guard/soldier)
    // 0:  IDLE   — n=2, медленное дыхание
    // 1:  WALK   — n=8, равномерная ходьба (frames 341,318-320...)
    // 13: RUN    — n=6, быстрое движение
    // 17: ATTACK — n=10, атака с паузой в конце (dur=5 last)
    // 30: HIT    — n=8, получение урона (dur=4 first)
    // 4:  DEAD   — n=4, смерть (dur=5 last, freeze)
    private static final int ANIM_IDLE   = 0;
    private static final int ANIM_WALK   = 1;
    private static final int ANIM_RUN    = 13;
    private static final int ANIM_ATTACK = 17;
    private static final int ANIM_HIT    = 30;
    private static final int ANIM_DEAD   = 4;

    public Enemy(float startX, float startY) {
        x = startX;
        y = startY;
        try {
            sprite = new ASprite("p5_001");
        } catch (Exception e) {
            sprite = null;
        }
    }

    public void update(float dt, TileMap map, Player player) {
        if (state == State.DEAD) {
            stateTimer += dt;
            if (stateTimer >= DEAD_TIME) removed = true;
            updateAnimation(dt);
            return;
        }

        attackHitActive = false;
        stateTimer += dt;
        if (attackCd > 0) attackCd -= dt;

        float distX = player.x - x;
        float distY = player.y - y;
        float dist  = (float) Math.sqrt(distX * distX + distY * distY);

        switch (state) {
            case PATROL:
                doPatrol(dt, map);
                if (dist < DETECT_RANGE && !player.isDead) {
                    state = State.CHASE;
                    stateTimer = 0;
                }
                break;

            case CHASE:
                doChase(dt, map, player, dist, distX);
                if (dist < ATTACK_RANGE && attackCd <= 0) {
                    state = State.ATTACK;
                    stateTimer = 0;
                    attackCd = ATTACK_CD;
                } else if (dist > DETECT_RANGE * 1.5f || player.isDead) {
                    state = State.PATROL;
                    stateTimer = 0;
                }
                break;

            case ATTACK:
                vx = 0;
                // Хитбокс активен в середине анимации (30-65%)
                float hitStart = ATTACK_DUR * 0.30f;
                float hitEnd   = ATTACK_DUR * 0.65f;
                attackHitActive = (stateTimer >= hitStart && stateTimer <= hitEnd);
                if (stateTimer >= ATTACK_DUR) {
                    state = dist < DETECT_RANGE ? State.CHASE : State.PATROL;
                    stateTimer = 0;
                }
                break;

            case HIT:
                vx *= 0.6f; // торможение при ударе
                if (stateTimer >= HIT_STUN) {
                    state = State.CHASE;
                    stateTimer = 0;
                }
                break;
        }

        // Гравитация + вертикальная коллизия
        vy += GRAVITY * dt;
        if (vy > MAX_FALL) vy = MAX_FALL;
        x += vx * dt;
        resolveHorizontal(map);
        y += vy * dt;
        resolveVertical(map);

        // Не выходим за левый край
        if (x < HIT_W / 2f) { x = HIT_W / 2f; vx = 0; }

        updateAnimation(dt);
    }

    private void doPatrol(float dt, TileMap map) {
        patrolTimer += dt;
        if (patrolTimer >= PATROL_TURN) {
            facingRight = !facingRight;
            patrolTimer = 0;
        }
        vx = facingRight ? PATROL_SPEED : -PATROL_SPEED;

        // Разворот у края платформы
        if (onGround) {
            int ts = TileMap.TILE_SIZE;
            float edgeX = facingRight ? x + HIT_W / 2f + 2 : x - HIT_W / 2f - 2;
            int tx = (int)(edgeX / ts);
            int ty = (int)(y / ts);
            if (!isSolid(map, tx, ty)) { // пропасть впереди
                facingRight = !facingRight;
                patrolTimer = 0;
            }
        }
    }

    private void doChase(float dt, TileMap map, Player player, float dist, float distX) {
        facingRight = distX > 0;
        vx = facingRight ? CHASE_SPEED : -CHASE_SPEED;
    }

    /** Получить хитбокс атаки в мировых координатах. null если неактивен. */
    public float[] getAttackBox() {
        if (!attackHitActive) return null;
        float ax = facingRight ? (x + HIT_W / 2f) : (x - HIT_W / 2f - ATK_W);
        float ay = y - HIT_H * 0.75f;
        return new float[]{ ax, ay, ATK_W, ATK_H };
    }

    /** Нанести урон врагу. */
    public void takeDamage(int dmg, float fromX) {
        if (state == State.DEAD) return;
        health -= dmg;
        if (health <= 0) {
            health = 0;
            state = State.DEAD;
            stateTimer = 0;
            vx = 0;
            return;
        }
        state = State.HIT;
        stateTimer = 0;
        vx = (x > fromX ? 1f : -1f) * 60f;
        vy = -80f;
    }

    // ---------------------------------------------------------------
    //  Физика
    // ---------------------------------------------------------------

    private void resolveHorizontal(TileMap map) {
        float left = x - HIT_W / 2f, right = x + HIT_W / 2f;
        float top = y - HIT_H, bottom = y;
        int ts = TileMap.TILE_SIZE;
        for (float cy = top + 2; cy <= bottom - 2; cy += ts / 2f) {
            if (vx < 0) {
                int tx = (int)(left / ts), ty = (int)(cy / ts);
                if (isSolid(map, tx, ty)) { x = (tx + 1) * ts + HIT_W / 2f; vx = 0; break; }
            } else if (vx > 0) {
                int tx = (int)(right / ts), ty = (int)(cy / ts);
                if (isSolid(map, tx, ty)) { x = tx * ts - HIT_W / 2f; vx = 0; break; }
            }
        }
    }

    private void resolveVertical(TileMap map) {
        float left = x - HIT_W / 2f + 2, right = x + HIT_W / 2f - 2;
        float bottom = y, top = y - HIT_H;
        int ts = TileMap.TILE_SIZE;
        if (vy > 0) {
            int ty = (int)(bottom / ts);
            for (float cx = left; cx <= right; cx += ts / 2f) {
                int tx = (int)(cx / ts);
                if (isSolid(map, tx, ty)) { y = ty * ts; vy = 0; onGround = true; return; }
            }
            onGround = false;
        } else if (vy < 0) {
            int ty = (int)(top / ts);
            for (float cx = left; cx <= right; cx += ts / 2f) {
                int tx = (int)(cx / ts);
                if (isSolid(map, tx, ty)) { y = (ty + 1) * ts + HIT_H; vy = 0; break; }
            }
        }
        if (vy >= 0) {
            boolean gb = false;
            for (float cx = left; cx <= right; cx += 4) {
                if (isSolid(map, (int)(cx / ts), (int)(bottom / ts))) { gb = true; break; }
            }
            onGround = gb;
        }
    }

    private boolean isSolid(TileMap map, int tx, int ty) {
        int t = map.getTile(0, tx, ty);
        return t != TileMap.EMPTY_TILE && t != 0;
    }

    // ---------------------------------------------------------------
    //  Анимация
    // ---------------------------------------------------------------

    private void updateAnimation(float dt) {
        if (sprite == null || sprite.anims.length == 0) return;
        int newAnim;
        if (state == State.DEAD) {
            newAnim = safe(ANIM_DEAD);
        } else if (state == State.HIT) {
            newAnim = safe(ANIM_HIT);
        } else if (state == State.ATTACK) {
            newAnim = safe(ANIM_ATTACK);
        } else if (Math.abs(vx) > 60) {
            newAnim = safe(ANIM_RUN);
        } else if (vx != 0) {
            newAnim = safe(ANIM_WALK);
        } else {
            newAnim = ANIM_IDLE;
        }
        if (newAnim != animIdx) { animIdx = newAnim; animStep = 0; animTimer = 0; }
        ASprite.Anim an = sprite.anims[animIdx];
        if (an.n > 0) {
            animTimer += dt;
            int afIdx = an.first + animStep;
            if (afIdx < sprite.afs.length) {
                int dur = Math.max(1, sprite.afs[afIdx].dur);
                while (animTimer >= dur * TICK) {
                    animTimer -= dur * TICK;
                    // Атака и смерть не зацикливаются
                    if (state == State.ATTACK || state == State.DEAD) {
                        animStep = Math.min(animStep + 1, an.n - 1);
                    } else {
                        animStep = (animStep + 1) % an.n;
                    }
                }
            }
        }
    }

    private int safe(int idx) {
        return Math.min(idx, sprite.anims.length - 1);
    }

    // ---------------------------------------------------------------
    //  Рендер
    // ---------------------------------------------------------------

    public void draw(SpriteBatch batch, float camX, float camY) {
        float sx = x - camX;
        float sy = (y - HIT_H / 2f) - camY;
        if (sprite == null || sprite.anims.length == 0) return;
        ASprite.Anim an = (animIdx < sprite.anims.length) ? sprite.anims[animIdx] : null;
        if (an != null && an.n > 0) {
            int afIdx = an.first + animStep;
            if (afIdx < sprite.afs.length) {
                ASprite.AnimFrame af = sprite.afs[afIdx];
                int tr = af.tr;
                if (!facingRight) tr ^= 1;
                sprite.drawFrame(batch, af.frame, sx + af.x, sy + af.y, tr);
            }
        }
    }

    @Override
    public void dispose() {
        if (sprite != null) sprite.dispose();
    }
}
