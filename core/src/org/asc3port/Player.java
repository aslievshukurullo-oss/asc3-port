package org.asc3port;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/**
 * Игровой персонаж (Connor) с физикой и боёвкой.
 *
 * Спрайт: p5_000 (1066 анимационных кадров Connor-а).
 * Анимации (индексы из оригинала):
 *   0 = стоять (idle)
 *   1 = ходьба
 *   2 = бег
 *   3 = прыжок
 *   4 = падение
 *   5 = атака 1 (лёгкий удар)
 *   6 = атака 2 (тяжёлый удар / combo)
 *
 * Боёвка:
 *   FIRE (OK) в воздухе = атака в воздухе
 *   NUM1      = лёгкий удар (стоя)
 *   NUM3      = тяжёлый удар / counter (стоя)
 *   Combo-окно: 400 мс после первого удара разрешает второй
 *
 * Физика: AABB коллизия с тайловой картой, гравитация, прыжок.
 */
public class Player implements Disposable {

    // Физические константы (в пикселях/сек)
    private static final float WALK_SPEED  = 100f;
    private static final float RUN_SPEED   = 200f;
    private static final float JUMP_VY     = -380f;
    private static final float GRAVITY     = 700f;
    private static final float MAX_FALL    = 500f;

    // Хитбокс персонажа
    public static final int HIT_W = 14;
    public static final int HIT_H = 28;

    // Хитбокс атаки (выдвигается вперёд)
    public static final int ATK_W = 22;
    public static final int ATK_H = 20;

    // Длительности состояний (секунды)
    private static final float ATTACK_DURATION  = 0.40f; // длина анимации удара
    private static final float COMBO_WINDOW     = 0.40f; // время для комбо после удара
    private static final float HIT_STUN         = 0.25f; // фриз при получении урона
    private static final float INVINCIBLE_TIME  = 0.60f; // неуязвимость после урона

    // Спрайт
    private ASprite sprite;
    private int animIdx = 0;
    private int animStep = 0;
    private float animTimer = 0;
    private static final float TICK = 1f / 25f;

    // Позиция (центр ног, пиксели, y-down)
    public float x, y;

    // Скорость
    public float vx, vy;

    // Базовое состояние
    public boolean onGround  = false;
    public boolean facingRight = true;

    // --- Боёвка ---
    public enum AttackState { NONE, LIGHT, HEAVY }
    public AttackState attackState = AttackState.NONE;

    /** Сейчас активная фаза нанесения урона (хитбокс активен) */
    public boolean attackHitActive = false;

    private float attackTimer   = 0f; // время с начала текущего удара
    private float comboTimer    = 0f; // окно для комбо
    private boolean comboPending = false; // следующий удар в очереди

    // Получение урона
    public int   health        = 5;
    public int   maxHealth     = 5;
    private float hitStunTimer  = 0f;
    private float invincTimer   = 0f;
    public boolean isDead       = false;

    // Индексы анимаций (p5_000)
    // 0: IDLE  — frames 11-13, n=5, дыхание
    // 1: WALK  — frames 19-28, n=10, равномерная ходьба
    // 3: RUN   — frames 29-36, n=8, бег
    // 4: JUMP  — frames 231-237, n=7, прыжок
    // 11: ATTACK— frames 50-61, n=13, лёгкая атака с паузой
    // 20: HIT  — frames 96-98, n=3, получение урона
    // 32: FALL — frames 48-49, n=2, падение
    // 33: DEAD — frames 261x3, n=3, смерть (freeze)
    // 38: HEAVY— frames 385+, n=34, тяжёлая атака
    private static final int ANIM_IDLE   = 0;
    private static final int ANIM_WALK   = 1;
    private static final int ANIM_RUN    = 3;
    private static final int ANIM_JUMP   = 4;
    private static final int ANIM_FALL   = 32;
    private static final int ANIM_ATTACK = 11;
    private static final int ANIM_HEAVY  = 38;
    private static final int ANIM_HIT    = 20;
    private static final int ANIM_DEAD   = 33;

    // Предыдущие входы для edge-detection
    private int prevInput = 0;

    // Coyote time — можно прыгнуть чуть позже края платформы
    private static final float COYOTE_TIME   = 0.12f;
    private static final float JUMP_BUFFER   = 0.12f; // буфер нажатия прыжка
    private float coyoteTimer  = 0f;
    private float jumpBufTimer = 0f;
    private boolean wasOnGround = false;

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
        if (isDead) { updateAnimation(dt); return; }

        // Edge-detection: кнопки нажатые только в этом кадре
        int justPressed = input & ~prevInput;
        prevInput = input;

        // Таймер неуязвимости
        if (invincTimer > 0) invincTimer -= dt;

        // Хит-стан (заморозка при получении урона)
        if (hitStunTimer > 0) {
            hitStunTimer -= dt;
            vx = 0;
            updateAnimation(dt);
            return;
        }

        // --- Боёвка ---
        updateAttack(dt, justPressed, input);

        // Движение блокировано во время удара
        boolean inAttack = attackState != AttackState.NONE;

        // Coyote time: считаем время с последнего момента на земле
        if (onGround) {
            coyoteTimer = COYOTE_TIME;
        } else if (coyoteTimer > 0) {
            coyoteTimer -= dt;
        }

        // Jump buffer: запоминаем нажатие прыжка заранее
        boolean jumpPressed = (justPressed & Keys.FIRE) != 0;
        if (jumpPressed) jumpBufTimer = JUMP_BUFFER;
        else if (jumpBufTimer > 0) jumpBufTimer -= dt;

        // Горизонтальное движение (только вне атаки или в воздухе)
        if (!inAttack || !onGround) {
            boolean moveLeft  = (input & Keys.LEFT)  != 0;
            boolean moveRight = (input & Keys.RIGHT) != 0;
            boolean run       = (input & Keys.NUM7)  != 0;
            float speed = run ? RUN_SPEED : WALK_SPEED;
            vx = 0;
            if (moveLeft)  { vx = -speed; facingRight = false; }
            if (moveRight) { vx =  speed; facingRight = true;  }
        } else {
            vx = 0;
        }

        // Прыжок: coyote time + jump buffer
        boolean canJump = coyoteTimer > 0 && vy >= 0;
        if (jumpBufTimer > 0 && canJump && !inAttack) {
            vy = JUMP_VY;
            onGround = false;
            coyoteTimer = 0;
            jumpBufTimer = 0;
            SoundManager.get().playJump();
        }

        // Гравитация
        vy += GRAVITY * dt;
        if (vy > MAX_FALL) vy = MAX_FALL;

        // Движение + коллизии
        boolean groundBefore = onGround;
        x += vx * dt;
        resolveHorizontal(map);
        y += vy * dt;
        resolveVertical(map);

        // Приземление
        if (onGround && !groundBefore) SoundManager.get().playLand();

        // Шаги
        if (onGround && Math.abs(vx) > 10f) SoundManager.get().playFootstep();

        // Граница карты
        if (x < HIT_W / 2f) { x = HIT_W / 2f; vx = 0; }
        if (x > map.getPixelWidth() - HIT_W / 2f) x = map.getPixelWidth() - HIT_W / 2f;
        // Нижняя граница — смерть от падения в пропасть
        if (y > map.getPixelHeight() + 100) { isDead = true; }
        // Верхняя граница
        if (y - HIT_H < 0) { y = HIT_H; vy = 0; }

        updateAnimation(dt);
    }

    // ---------------------------------------------------------------
    //  Боёвка
    // ---------------------------------------------------------------

    private void updateAttack(float dt, int justPressed, int input) {
        boolean lightBtn = (justPressed & Keys.NUM1) != 0;
        boolean heavyBtn = (justPressed & Keys.NUM3) != 0;
        // В воздухе OK = удар
        boolean airAtk   = (justPressed & Keys.FIRE) != 0 && !onGround;

        attackHitActive = false;

        if (attackState != AttackState.NONE) {
            attackTimer += dt;

            // Хитбокс активен в середине анимации (25%-65% длительности)
            float hitStart = ATTACK_DURATION * 0.25f;
            float hitEnd   = ATTACK_DURATION * 0.65f;
            attackHitActive = (attackTimer >= hitStart && attackTimer <= hitEnd);

            if (attackTimer >= ATTACK_DURATION) {
                // Удар завершён — открываем комбо-окно
                attackState = AttackState.NONE;
                attackTimer = 0;
                if (comboPending) {
                    comboPending = false;
                    startAttack(AttackState.HEAVY);
                } else {
                    comboTimer = COMBO_WINDOW;
                }
            }
        } else {
            // Таймер комбо-окна
            if (comboTimer > 0) comboTimer -= dt;

            // Новый удар
            if (lightBtn || airAtk) {
                startAttack(AttackState.LIGHT);
            } else if (heavyBtn) {
                startAttack(AttackState.HEAVY);
            }
        }

        // Комбо: нажали тяжёлый во время лёгкого
        if (attackState == AttackState.LIGHT && heavyBtn) {
            comboPending = true;
        }
    }

    private void startAttack(AttackState type) {
        attackState  = type;
        attackTimer  = 0;
        comboTimer   = 0;
        comboPending = false;
        attackHitActive = false;
        if (type == AttackState.LIGHT) SoundManager.get().playLightHit();
        else                           SoundManager.get().playHeavyHit();
    }

    /**
     * Получить хитбокс атаки в мировых координатах.
     * @return float[]{x, y, w, h} или null если атака неактивна / хитбокс не активен
     */
    public float[] getAttackBox() {
        if (!attackHitActive) return null;
        float dir = facingRight ? 1f : -1f;
        float ax = facingRight ? (x + HIT_W / 2f) : (x - HIT_W / 2f - ATK_W);
        float ay = y - HIT_H * 0.75f; // примерно на уровне груди
        return new float[]{ ax, ay, ATK_W, ATK_H };
    }

    /**
     * Нанести урон персонажу.
     * @param dmg    количество урона
     * @param fromX  источник удара (для knockback)
     */
    public void takeDamage(int dmg, float fromX) {
        if (invincTimer > 0 || isDead) return;
        health -= dmg;
        if (health <= 0) {
            health = 0;
            isDead = true;
            SoundManager.get().playPlayerDie();
        } else {
            SoundManager.get().playHurt();
        }
        hitStunTimer = HIT_STUN;
        invincTimer  = INVINCIBLE_TIME;
        // Knockback
        vx = (x > fromX ? 1f : -1f) * 80f;
        vy = -120f;
        onGround = false;
    }

    // ---------------------------------------------------------------
    //  Физика / коллизии (без изменений)
    // ---------------------------------------------------------------

    private void resolveHorizontal(TileMap map) {
        float left   = x - HIT_W / 2f;
        float right  = x + HIT_W / 2f;
        float top    = y - HIT_H;
        float bottom = y;
        int ts = TileMap.TILE_SIZE;
        for (float checkY = top + 2; checkY <= bottom - 2; checkY += ts / 2f) {
            if (vx < 0) {
                int tx = (int)(left / ts);
                int ty = (int)(checkY / ts);
                if (isSolid(map, tx, ty)) { x = (tx + 1) * ts + HIT_W / 2f; vx = 0; break; }
            } else if (vx > 0) {
                int tx = (int)(right / ts);
                int ty = (int)(checkY / ts);
                if (isSolid(map, tx, ty)) { x = tx * ts - HIT_W / 2f; vx = 0; break; }
            }
        }
    }

    private void resolveVertical(TileMap map) {
        float left   = x - HIT_W / 2f + 2;
        float right  = x + HIT_W / 2f - 2;
        float bottom = y;
        float top    = y - HIT_H;
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
            boolean groundBelow = false;
            for (float cx = left; cx <= right; cx += 4) {
                if (isSolid(map, (int)(cx / ts), (int)(bottom / ts))) { groundBelow = true; break; }
            }
            onGround = groundBelow;
        }
    }

    private boolean isSolid(TileMap map, int tx, int ty) {
        int t0 = map.getTile(0, tx, ty);
        return t0 != TileMap.EMPTY_TILE && t0 != 0;
    }

    // ---------------------------------------------------------------
    //  Анимация
    // ---------------------------------------------------------------

    private void updateAnimation(float dt) {
        if (sprite == null || sprite.anims.length == 0) return;

        int newAnim;
        if (isDead) {
            newAnim = ANIM_DEAD;
        } else if (hitStunTimer > 0) {
            newAnim = ANIM_HIT;
        } else if (attackState == AttackState.HEAVY) {
            newAnim = ANIM_HEAVY;
        } else if (attackState == AttackState.LIGHT) {
            newAnim = ANIM_ATTACK;
        } else if (!onGround) {
            newAnim = (vy < 0) ? ANIM_JUMP : ANIM_FALL;
        } else if (vx != 0) {
            newAnim = (Math.abs(vx) > WALK_SPEED + 10) ? ANIM_RUN : ANIM_WALK;
        } else {
            newAnim = ANIM_IDLE;
        }

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
                    // Анимация атаки не зацикливается
                    if (attackState != AttackState.NONE) {
                        animStep = Math.min(animStep + 1, an.n - 1);
                    } else {
                        animStep = (animStep + 1) % an.n;
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------
    //  Рендер
    // ---------------------------------------------------------------

    public void draw(SpriteBatch batch, float camX, float camY) {
        if (sprite == null) return;
        if (sprite.anims.length == 0 && sprite.frames.length == 0) return;

        // Мигание при неуязвимости
        if (invincTimer > 0) {
            // Мигаем каждые 80 мс
            long ms = (long)(invincTimer * 1000);
            if ((ms / 80) % 2 == 0) return;
        }

        float screenX = x - camX;
        float screenY = (y - HIT_H / 2f) - camY;

        ASprite.Anim an = (animIdx < sprite.anims.length) ? sprite.anims[animIdx] : null;
        if (an != null && an.n > 0) {
            int afIdx = an.first + animStep;
            if (afIdx < sprite.afs.length) {
                ASprite.AnimFrame af = sprite.afs[afIdx];
                int tr = af.tr;
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
