package org.asc3port;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Рендер диалогового окна внизу экрана.
 *
 * Вид:
 *   ┌──────────────────────────────────────┐
 *   │ [SpeakerName]                        │
 *   │ Текст диалога, автоперенос...        │
 *   └──────────────────────────────────────┘
 *
 * Занимает нижние ~22% экрана.
 */
public class DialogBox {

    private static final float BOX_H_RATIO  = 0.22f;  // высота бокса как доля экрана
    private static final float PADDING      = 6f;
    private static final float SPEAKER_H    = 9f;      // высота строки имени говорящего

    // Цвета
    private static final Color BG_COLOR     = new Color(0f, 0f, 0f, 0.78f);
    private static final Color BORDER_COLOR = new Color(0.8f, 0.7f, 0.3f, 1f); // золото
    private static final Color SPEAKER_COL  = new Color(1f, 0.85f, 0.3f, 1f);
    private static final Color TEXT_COL     = new Color(1f, 1f, 1f, 1f);

    private final GlyphLayout layout = new GlyphLayout();

    /**
     * Рисует диалоговое окно.
     *
     * @param sr      ShapeRenderer (не начат)
     * @param sb      SpriteBatch (не начат)
     * @param font    BitmapFont для текста
     * @param line    текущая реплика
     * @param screenW ширина экрана
     * @param screenH высота экрана
     */
    public void draw(ShapeRenderer sr, SpriteBatch sb, BitmapFont font,
                     DialogLine line, float screenW, float screenH) {
        if (line == null || line.text.isEmpty()) return;

        float boxH = screenH * BOX_H_RATIO;
        float boxX = 0;
        float boxY = screenH - boxH;
        float boxW = screenW;

        // --- Фон ---
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(BG_COLOR);
        sr.rect(boxX, boxY, boxW, boxH);
        sr.end();

        // --- Рамка (сверху) ---
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(BORDER_COLOR);
        sr.line(boxX, boxY, boxX + boxW, boxY);
        sr.end();

        // --- Текст ---
        sb.begin();

        float textX = boxX + PADDING;
        float maxW  = boxW - PADDING * 2;
        float curY  = boxY + boxH - PADDING;

        // Имя говорящего
        String name = line.speakerName();
        if (!name.isEmpty()) {
            font.setColor(SPEAKER_COL);
            curY -= SPEAKER_H;
            font.draw(sb, name, textX, curY + SPEAKER_H);
            curY -= 3;
        }

        // Основной текст с переносом
        font.setColor(TEXT_COL);
        layout.setText(font, line.text, TEXT_COL, maxW, -1, true);
        font.draw(sb, layout, textX, curY);

        sb.end();
    }
}
