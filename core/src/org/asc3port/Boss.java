package org.asc3port;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/**
 * Финальный босс — Charles Lee / Haytham Kenway.
 *
 * Спрайт: p5_025 (1589 кадров, 456 анимаций)
 * Анимации (определены эмпирически):
 *   0  = IDLE  (n=6, avg=4.5)
 *   1  = WALK  (n=10, avg=1.0)
 *   3  = RUN   (n=8, avg=1.0)
 *   6  = DEAD  (n=5, last dur=30, freeze)
 *   11 = ATTACK(n=11, last dur=10, hold)
 *
 * Поведение (3 фазы):
 *   HP > 66%: PATROL/CHASE, атакует как обычный враг
 *   HP > 33%: агрессивнее (быстрее бегает, чаще атакует)
 *   HP ≤ 33%: ENRAGED — телепортирует и атакует сдвоено
 *
 * Здоровье: 10 очков (в 3x больше обычного врага).
 */
public class Boss implements Disposable {

    public enum State { PATROL, CHASE, ATTACK, HIT, DEAD, ENRAGE }

    // Физика
    private static final float WALK_SPEED   =  60f;
    private static final float CHASE_SPEED  = 100f;
    private static final float ENRAGE_SPEED = 150f;
    private static final float GRAVITY      = 700f;
    private static final float MAX_FALL     = 500f;

    public static final int HIT_W = 16;
    public static final int HIT_H = 30;

    private static final float DETECT_RANGE  = 200f;
    private static final float ATTACK_RANGE  =  36f;
    private static final float ATTACK_CD     =   0.9f;
    private static final float ATTACK_DUR    =   0.5f;
    private static final float HIT_STUN      =   0.25f;
    private static final float DEAD_TIME     =   1.5f;
    private static final float ENRAGE_FLASH  =   0.1f; // мигание при ярости

    private static final float ATK_W = 24f;
    private static final float ATK_H = 20f;

    // Параметры
    public float x, y;
    public float vx, vy;
    public boolean onGround   = false;
    public boolean facingRight = true;
    public int health    = 10;
    public int maxHealth = 10;
    public State state   = State.PATROL;
    public boolean removed = false;

    // Таймеры
    private float stateTimer  = 0;
    private float attackCd    = 0;
    private float patrolTimer = 0;
    private float enrageFlash = 0;
    private boolean enraged   = false;
    private int     combo     = 0;      // счётчик атак в серии (enrage)

    public boolean attackHitActive = false;

    // Спрайт
    private ASprite sprite;
    private int animIdx  = 0;
    private int animStep = 0;
    private float animTimer = 0;
    private static final float TICK = 1f / 25f;

    private static final int ANIM_IDLE   = 0;
    private static final int ANIM_WALK   = 1;
    private static final int ANIM_RUN    = 3;
    private static final int ANIM_ATTACK = 11;
    private static final int ANIM_DEAD   = 6;

    public Boss(float startX, float startY) {
        x = startX; y = startY;
        try { sprite = new ASprite("p5_025"); }
        catch (Exception e) { sprite = null; }
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
        if (enrageFlash > 0) enrageFlash -= dt;

        // Проверка ярости (HP ≤ 33%)
        if (!enraged && health <= maxHealth / 3) {
            enraged = true;
            state = State.ENRAGE;
            stateTimer = 0;
        }

        float distX = player.x - x;
        float dist  = Math.abs(distX);

        switch (state) {
            case PATROL:
                doPatrol(dt, map);
                if (dist < DETECT_RANGE && !player.isDead) {
                    state = State.CHASE; stateTimer = 0;
                }
                break;

            case CHASE:
                doChase(distX);
                if (dist < ATTACK_RANGE && attackCd <= 0) {
                    state = State.ATTACK; stateTimer = 0;
                    attackCd = enraged ? ATTACK_CD * 0.6f : ATTACK_CD;
                    combo = 0;
                } else if (dist > DETECT_RANGE * 1.5f || player.isDead) {
                    state = State.PATROL; stateTimer = 0;
                }
                break;

            case ATTACK:
                vx = 0;
                float hitStart = ATTACK_DUR * 0.30f;
                float hitEnd   = ATTACK_DUR * 0.65f;
                attackHitActive = (stateTimer >= hitStart && stateTimer <= hitEnd);
                if (stateTimer >= ATTACK_DUR) {
                    combo++;
                    if (enraged && combo < 2 && dist < ATTACK_RANGE * 1.5f) {
                        // второй удар в серии при ярости
                        stateTimer = 0;
                        attackCd = ATTACK_CD * 0.4f;
                    } else {
                        state = dist < DETECT_RANGE ? State.CHASE : State.PATROL;
                        stateTimer = 0;
                    }
                }
                break;

            case HIT:
                vx *= 0.5f;
                if (stateTimer >= HIT_STUN) {
                    state = State.CHASE; stateTimer = 0;
                }
                break;

            case ENRAGE:
                // Короткая пауза + рывок к игроку
                vx = distX > 0 ? ENRAGE_SPEED : -ENRAGE_SPEED;
                facingRight = distX > 0;
                enrageFlash = ENRAGE_FLASH;
                if (stateTimer > 0.4f) {
                    state = State.ATTACK; stateTimer = 0;
                    attackCd = 0;
                }
                break;
        }

        vy += GRAVITY * dt;
        if (vy > MAX_FALL) vy = MAX_FALL;
        x += vx * dt;
        resolveHorizontal(map);
        y += vy * dt;
        resolveVertical(map);
        if (x < HIT_W / 2f) { x = HIT_W / 2f; vx = 0; }

        updateAnimation(dt);
    }

    private void doPatrol(float dt, TileMap map) {
        patrolTimer += dt;
        if (patrolTimer >= 2.5f) { facingRight = !facingRight; patrolTimer = 0; }
        vx = facingRight ? WALK_SPEED : -WALK_SPEED;
        if (onGround) {
            int ts = TileMap.TILE_SIZE;
            float ex = facingRight ? x + HIT_W/2f + 2 : x - HIT_W/2f - 2;
            if (!isSolid(map, (int)(ex/ts), (int)(y/ts))) { facingRight = !facingRight; patrolTimer = 0; }
        }
    }

    private void doChase(float distX) {
        facingRight = distX > 0;
        float spd = enraged ? ENRAGE_SPEED : CHASE_SPEED;
        vx = facingRight ? spd : -spd;
    }

    public void takeDamage(int dmg, float fromX) {
        if (state == State.DEAD) return;
        health -= dmg;
        if (health <= 0) {
            health = 0;
            state = State.DEAD;
            stateTimer = 0; vx = 0;
            SoundManager.get().playEnemyDie();
            return;
        }
        SoundManager.get().playHurt();
        state = State.HIT; stateTimer = 0;
        vx = (x > fromX ? 1f : -1f) * 50f;
        vy = -60f;
    }

    public float[] getAttackBox() {
        if (!attackHitActive) return null;
        float ax = facingRight ? x + HIT_W/2f : x - HIT_W/2f - ATK_W;
        float ay = y - HIT_H * 0.75f;
        return new float[]{ ax, ay, ATK_W, ATK_H };
    }

    /** Заполненность HP-бара [0..1] для UI. */
    public float getHpFraction() { return (float) health / maxHealth; }
    public boolean isDead() { return state == State.DEAD; }
    /** Мигание при ярости — для рендера. */
    public boolean isEnrageFlashing() { return enrageFlash > 0; }

    // ── Физика ─────────────────────────────────────────────────────

    private void resolveHorizontal(TileMap map) {
        float left = x - HIT_W/2f, right = x + HIT_W/2f;
        float top = y - HIT_H, bottom = y;
        int ts = TileMap.TILE_SIZE;
        for (float cy = top+2; cy <= bottom-2; cy += ts/2f) {
            if (vx < 0) { int tx=(int)(left/ts),ty=(int)(cy/ts);
                if(isSolid(map,tx,ty)){x=(tx+1)*ts+HIT_W/2f;vx=0;break;} }
            else if (vx > 0) { int tx=(int)(right/ts),ty=(int)(cy/ts);
                if(isSolid(map,tx,ty)){x=tx*ts-HIT_W/2f;vx=0;break;} }
        }
    }

    private void resolveVertical(TileMap map) {
        float left=x-HIT_W/2f+2, right=x+HIT_W/2f-2;
        float bottom=y, top=y-HIT_H;
        int ts = TileMap.TILE_SIZE;
        if (vy > 0) {
            int ty=(int)(bottom/ts);
            for(float cx=left;cx<=right;cx+=ts/2f){
                int tx=(int)(cx/ts);
                if(isSolid(map,tx,ty)){y=ty*ts;vy=0;onGround=true;return;}
            }
            onGround=false;
        } else if (vy < 0) {
            int ty=(int)(top/ts);
            for(float cx=left;cx<=right;cx+=ts/2f){
                int tx=(int)(cx/ts);
                if(isSolid(map,tx,ty)){y=(ty+1)*ts+HIT_H;vy=0;break;}
            }
        }
        if (vy >= 0) {
            boolean gb=false;
            for(float cx=left;cx<=right;cx+=4){
                if(isSolid(map,(int)(cx/ts),(int)(bottom/ts))){gb=true;break;}
            }
            onGround=gb;
        }
    }

    private boolean isSolid(TileMap map, int tx, int ty) {
        int t = map.getTile(0, tx, ty);
        return t != TileMap.EMPTY_TILE && t != 0;
    }

    // ── Анимация ────────────────────────────────────────────────────

    private void updateAnimation(float dt) {
        if (sprite == null || sprite.anims.length == 0) return;
        int newAnim;
        int maxIdx = sprite.anims.length - 1;
        if (state == State.DEAD)   newAnim = Math.min(ANIM_DEAD, maxIdx);
        else if (state == State.ATTACK || state == State.ENRAGE)
                                   newAnim = Math.min(ANIM_ATTACK, maxIdx);
        else if (Math.abs(vx) > 70) newAnim = Math.min(ANIM_RUN, maxIdx);
        else if (vx != 0)          newAnim = Math.min(ANIM_WALK, maxIdx);
        else                       newAnim = Math.min(ANIM_IDLE, maxIdx);

        if (newAnim != animIdx) { animIdx = newAnim; animStep = 0; animTimer = 0; }
        ASprite.Anim an = sprite.anims[animIdx];
        if (an.n > 0) {
            animTimer += dt;
            int afIdx = an.first + animStep;
            if (afIdx < sprite.afs.length) {
                int dur = Math.max(1, sprite.afs[afIdx].dur);
                while (animTimer >= dur * TICK) {
                    animTimer -= dur * TICK;
                    boolean freeze = (state == State.DEAD || state == State.ATTACK);
                    animStep = freeze ? Math.min(animStep+1, an.n-1) : (animStep+1)%an.n;
                }
            }
        }
    }

    // ── Рендер ──────────────────────────────────────────────────────

    public void draw(SpriteBatch batch, float camX, float camY) {
        if (sprite == null || sprite.anims.length == 0) return;
        // Мигание при ярости: пропускаем кадр
        if (isEnrageFlashing() && ((int)(enrageFlash * 20) % 2 == 0)) return;
        float sx = x - camX;
        float sy = (y - HIT_H/2f) - camY;
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
