package org.asc3port;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Игровой HUD: здоровье, счёт, уведомления.
 *
 * Использование:
 *   hud.update(dt);
 *   hud.draw(sr, sb, font, player, screenW, screenH);
 *   hud.showNotification("Checkpoint!");
 *   hud.addScore(100);
 */
public class GameHUD {

    public int score = 0;

    // Уведомление (временное сообщение на экране)
    private String notifText  = null;
    private float  notifTimer = 0f;
    private static final float NOTIF_DURATION = 1.8f;

    // Анимация очков (+N всплывает)
    private String scorePopText  = null;
    private float  scorePopTimer = 0f;
    private float  scorePopY     = 0f;
    private static final float POP_DURATION = 1.0f;

    public void update(float dt) {
        if (notifTimer  > 0) notifTimer  -= dt;
        if (scorePopTimer > 0) { scorePopTimer -= dt; scorePopY -= 18f * dt; }
    }

    public void showNotification(String text) {
        notifText  = text;
        notifTimer = NOTIF_DURATION;
    }

    public void addScore(int points) {
        score += points;
        scorePopText  = "+" + points;
        scorePopTimer = POP_DURATION;
        scorePopY     = 60f;
    }

    public void draw(ShapeRenderer sr, SpriteBatch sb, BitmapFont font,
                     Player player, int screenW, int screenH) {
        if (player == null) return;

        final int HP_X = 125, HP_Y = 9;
        final int HP_W = 76,  HP_H = 8;

        // HP background + bar
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.08f, 0.08f, 0.08f, 0.80f);
        sr.rect(HP_X - 2, HP_Y - 2, HP_W + 4, HP_H + 4);
        float pct = (float) player.health / player.maxHealth;
        if      (pct > 0.60f) sr.setColor(0.15f, 0.80f, 0.20f, 1f);
        else if (pct > 0.30f) sr.setColor(0.90f, 0.70f, 0.10f, 1f);
        else                   sr.setColor(0.90f, 0.12f, 0.12f, 1f);
        sr.rect(HP_X, HP_Y, HP_W * Math.max(pct, 0f), HP_H);

        // Notification background
        if (notifTimer > 0 && notifText != null) {
            float alpha = Math.min(notifTimer, 0.4f) / 0.4f;
            sr.setColor(0f, 0f, 0f, 0.55f * alpha);
            float nw = notifText.length() * 7.5f + 20;
            sr.rect(screenW / 2f - nw / 2f, screenH / 2f - 14, nw, 22);
        }
        sr.end();

        sb.begin();
        // HP label
        font.setColor(1f, 1f, 1f, 0.9f);
        font.getData().setScale(1.1f);
        font.draw(sb, "HP", HP_X - 20, HP_Y + HP_H);

        // Score
        font.setColor(0.95f, 0.80f, 0.15f, 0.9f);
        font.draw(sb, String.valueOf(score), HP_X + HP_W + 10, HP_Y + HP_H);

        // BACK hint
        font.setColor(0.75f, 0.75f, 0.75f, 0.75f);
        font.draw(sb, "BACK", screenW - 62f, HP_Y + HP_H);

        // Notification
        if (notifTimer > 0 && notifText != null) {
            float alpha = Math.min(notifTimer, 0.5f) / 0.5f;
            font.getData().setScale(1.4f);
            font.setColor(1f, 0.95f, 0.3f, alpha);
            float nw = notifText.length() * 8.5f;
            font.draw(sb, notifText, screenW / 2f - nw / 2f, screenH / 2f + 6);
        }

        // Score pop
        if (scorePopTimer > 0 && scorePopText != null) {
            float alpha = scorePopTimer / POP_DURATION;
            font.getData().setScale(1.3f);
            font.setColor(1f, 1f, 0.2f, alpha);
            font.draw(sb, scorePopText, HP_X + HP_W + 8, scorePopY);
        }

        font.getData().setScale(1.4f);
        sb.end();
    }

    /** Нарисовать экран смерти. */
    public void drawDeathScreen(ShapeRenderer sr, SpriteBatch sb, BitmapFont font,
                                 float deathTimer, int screenW, int screenH) {
        float alpha = Math.min(deathTimer / 1.0f, 0.85f);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.5f, 0f, 0f, alpha * 0.6f);
        sr.rect(0, 0, screenW, screenH);
        sr.end();
        if (deathTimer > 0.5f) {
            sb.begin();
            float a2 = Math.min((deathTimer - 0.5f) / 0.5f, 1f);
            font.getData().setScale(1.8f);
            font.setColor(1f, 0.15f, 0.15f, a2);
            String msg = Lang.get("you_died");
            font.draw(sb, msg, screenW / 2f - msg.length() * 6f, screenH / 2f);
            font.getData().setScale(1.2f);
            font.setColor(0.8f, 0.8f, 0.8f, a2 * 0.7f);
            String sub = Lang.get("returning_menu");
            font.draw(sb, sub, screenW / 2f - sub.length() * 4.5f, screenH / 2f + 22);
            font.getData().setScale(1.4f);
            sb.end();
        }
    }
}
