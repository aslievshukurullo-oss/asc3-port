package org.asc3port;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * ASC3 Port — LibGDX/Android, горизонтальный режим.
 *
 * Виртуальный экран: 480×320 (y-down).
 * Оригинальное окно 240×320 по центру (GAME_X=120).
 * Автомат состояний по оригиналу k.w(n):
 *   STATE_INIT  (1) – инициализация ассетов
 *   STATE_SPLASH(2) – заставка Gameloft / AC3 (~3 сек)
 *   STATE_MENU  (5) – главное меню
 *   STATE_GAME  (7) – просмотрщик спрайтов (заглушка геймплея)
 */
public class AscGame extends ApplicationAdapter {

    // Размеры
    public static final int GAME_W = 240, GAME_H = 320;
    public static final int VIEW_W = 480, VIEW_H = 320;
    public static final int GAME_X = (VIEW_W - GAME_W) / 2; // 120

    // Состояния (соответствуют k.w оригинала)
    private static final int STATE_INIT   = 1;
    private static final int STATE_SPLASH = 2;
    private static final int STATE_MENU   = 5;
    private static final int STATE_GAME   = 7;

    // Время отображения заставки (мс)
    private static final float SPLASH_TIME = 3.0f;

    // --- Графика ---
    private OrthographicCamera cam;
    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private TouchOverlay pad;

    // --- Ассеты ---
    private ASprite splashSprite;   // пак /4, W[50] — заставка
    private ASprite fontSprite;     // пак /2, W[55] — игровой шрифт
    private StringTable strings;    // EN секция 0

    // --- Меню ---
    private static final String[] MENU_ITEMS = {
        "NEW GAME", "CONTINUE", "OPTIONS", "HELP", "ABOUT"
    };
    private int menuSel = 0;

    // --- Просмотрщик спрайтов (STATE_GAME) ---
    private String[] spriteNames;
    private int sprCur = 0, sprAnim = 0, sprStep = 0;
    private float sprTimer = 0;
    private ASprite curSprite;
    private static final float TICK = 1f / 25f;

    // --- Автомат состояний ---
    private int state = STATE_INIT;
    private float stateTimer = 0;

    @Override
    public void create() {
        cam = new OrthographicCamera();
        cam.setToOrtho(true, VIEW_W, VIEW_H); // y-down как в оригинале
        viewport = new FitViewport(VIEW_W, VIEW_H, cam);
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont(true); // flipped для y-down
        font.getData().setScale(1.2f);
        pad = new TouchOverlay(viewport);

        // Загрузка строк
        try {
            strings = new StringTable("EN_000");
        } catch (Exception e) {
            strings = null;
        }

        // Загрузка заставки (пак /4)
        try {
            splashSprite = new ASprite("p4_000");
        } catch (Exception e) {
            splashSprite = null;
        }

        // Список спрайтов для просмотрщика
        String idx = Gdx.files.internal("sprites/index.txt").readString().trim();
        spriteNames = idx.split("\\s+");

        state = STATE_SPLASH;
        stateTimer = 0;
    }

    @Override
    public void resize(int w, int h) {
        viewport.update(w, h, false);
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        pad.update();
        update(dt);
        draw();
    }

    private void update(float dt) {
        stateTimer += dt;

        switch (state) {
            case STATE_SPLASH:
                // Переход по таймеру или нажатию клавиши
                if (stateTimer >= SPLASH_TIME || (pad.pressed & (Keys.FIRE | Keys.NUM5)) != 0) {
                    state = STATE_MENU;
                    stateTimer = 0;
                }
                break;

            case STATE_MENU:
                if ((pad.pressed & Keys.UP) != 0)
                    menuSel = (menuSel - 1 + MENU_ITEMS.length) % MENU_ITEMS.length;
                if ((pad.pressed & Keys.DOWN) != 0)
                    menuSel = (menuSel + 1) % MENU_ITEMS.length;
                if ((pad.pressed & (Keys.FIRE | Keys.NUM5)) != 0) {
                    if (menuSel == 0) { // NEW GAME -> просмотрщик спрайтов
                        state = STATE_GAME;
                        stateTimer = 0;
                        loadSprite(0);
                    }
                }
                break;

            case STATE_GAME:
                updateSpriteViewer(dt);
                break;
        }
    }

    private void updateSpriteViewer(float dt) {
        if ((pad.pressed & Keys.RIGHT) != 0) loadSprite(sprCur + 1);
        if ((pad.pressed & Keys.LEFT)  != 0) loadSprite(sprCur - 1);
        if ((pad.pressed & Keys.DOWN)  != 0 && curSprite != null && curSprite.anims.length > 0) {
            sprAnim = (sprAnim + 1) % curSprite.anims.length;
            sprStep = 0; sprTimer = 0;
        }
        if ((pad.pressed & Keys.UP) != 0 && curSprite != null && curSprite.anims.length > 0) {
            sprAnim = (sprAnim - 1 + curSprite.anims.length) % curSprite.anims.length;
            sprStep = 0; sprTimer = 0;
        }
        if ((pad.pressed & Keys.SOFT_L) != 0) {
            state = STATE_MENU; stateTimer = 0;
        }
        if (curSprite != null && curSprite.anims.length > 0) {
            ASprite.Anim an = curSprite.anims[sprAnim];
            if (an.n > 0) {
                sprTimer += dt;
                int dur = Math.max(1, curSprite.afs[an.first + sprStep].dur);
                while (sprTimer >= dur * TICK) {
                    sprTimer -= dur * TICK;
                    sprStep = (sprStep + 1) % an.n;
                }
            }
        }
    }

    private void loadSprite(int idx) {
        if (spriteNames == null || spriteNames.length == 0) return;
        sprCur = ((idx % spriteNames.length) + spriteNames.length) % spriteNames.length;
        if (curSprite != null) { curSprite.dispose(); curSprite = null; }
        try {
            curSprite = new ASprite(spriteNames[sprCur]);
        } catch (Exception ignored) {}
        sprAnim = 0; sprStep = 0; sprTimer = 0;
    }

    // ─── ОТРИСОВКА ────────────────────────────────────────────────────────────

    private void draw() {
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply(false);
        shapes.setProjectionMatrix(cam.combined);
        batch.setProjectionMatrix(cam.combined);

        // Фон оригинального окна
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.10f, 0.11f, 0.15f, 1f);
        shapes.rect(GAME_X, 0, GAME_W, GAME_H);
        shapes.end();

        switch (state) {
            case STATE_SPLASH: drawSplash(); break;
            case STATE_MENU:   drawMenu();   break;
            case STATE_GAME:   drawGame();   break;
        }

        pad.draw(shapes, batch, font);
    }

    private void drawSplash() {
        if (splashSprite != null && splashSprite.frames.length > 0) {
            batch.begin();
            // Кадр 0 или 1 в зависимости от времени
            int fi = (stateTimer > 1.5f && splashSprite.frames.length > 1) ? 1 : 0;
            splashSprite.drawFrame(batch, fi,
                GAME_X + GAME_W / 2f, GAME_H / 2f, 0);
            batch.end();
        } else {
            // Текстовая заставка если спрайт не загрузился
            batch.begin();
            font.setColor(1f, 0.85f, 0.2f, 1f);
            font.draw(batch, "ASSASSIN'S CREED III", GAME_X + 10, 120);
            font.setColor(0.7f, 0.7f, 0.7f, 1f);
            font.draw(batch, "Gameloft", GAME_X + 80, 160);
            font.setColor(0.4f, 0.4f, 0.4f, 1f);
            font.draw(batch, "Tap to continue", GAME_X + 55, 260);
            batch.end();
        }
        // Прогресс-бар
        float pct = Math.min(stateTimer / SPLASH_TIME, 1f);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.25f, 0.25f, 0.30f, 1f);
        shapes.rect(GAME_X + 20, GAME_H - 16, GAME_W - 40, 6);
        shapes.setColor(0.9f, 0.7f, 0.1f, 1f);
        shapes.rect(GAME_X + 20, GAME_H - 16, (GAME_W - 40) * pct, 6);
        shapes.end();
    }

    private void drawMenu() {
        batch.begin();
        // Заголовок
        font.setColor(0.95f, 0.80f, 0.15f, 1f);
        font.draw(batch, "ASSASSIN'S CREED III", GAME_X + 8, 30);
        // Линия
        batch.end();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.5f, 0.4f, 0.1f, 1f);
        shapes.rect(GAME_X + 8, 42, GAME_W - 16, 1);
        shapes.end();
        // Пункты меню
        batch.begin();
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            boolean sel = i == menuSel;
            if (sel) {
                batch.end();
                shapes.begin(ShapeRenderer.ShapeType.Filled);
                shapes.setColor(0.2f, 0.18f, 0.05f, 1f);
                shapes.rect(GAME_X + 8, 60 + i * 36 - 14, GAME_W - 16, 28);
                shapes.end();
                batch.begin();
            }
            font.setColor(sel ? 1f : 0.7f, sel ? 0.85f : 0.7f, sel ? 0.1f : 0.7f, 1f);
            font.draw(batch, (sel ? "> " : "  ") + MENU_ITEMS[i], GAME_X + 14, 60 + i * 36);
        }
        // Подсказка
        font.setColor(0.4f, 0.4f, 0.4f, 1f);
        font.draw(batch, "UP/DOWN: move  OK: select", GAME_X + 8, GAME_H - 8);
        batch.end();
    }

    private void drawGame() {
        if (curSprite == null) {
            batch.begin();
            font.setColor(1f, 0.3f, 0.3f, 1f);
            font.draw(batch, "No sprite loaded", GAME_X + 20, 150);
            batch.end();
            return;
        }
        batch.begin();
        // Рисуем текущий анимационный кадр
        if (curSprite.anims.length > 0) {
            ASprite.Anim an = curSprite.anims[sprAnim];
            if (an.n > 0) {
                int afIdx = an.first + sprStep;
                if (afIdx < curSprite.afs.length) {
                    ASprite.AnimFrame af = curSprite.afs[afIdx];
                    curSprite.drawFrame(batch, af.frame,
                        GAME_X + GAME_W / 2f + af.x,
                        GAME_H / 2f + af.y, af.tr);
                }
            }
        } else if (curSprite.frames.length > 0) {
            curSprite.drawFrame(batch, 0, GAME_X + GAME_W / 2f, GAME_H / 2f, 0);
        }
        // HUD
        String name = spriteNames[sprCur];
        font.setColor(1f, 1f, 1f, 0.9f);
        font.draw(batch, name, GAME_X + 2, 10);
        font.setColor(0.7f, 0.9f, 0.7f, 1f);
        font.draw(batch, "A:" + sprAnim + "/" + curSprite.anims.length
            + " F:" + sprStep, GAME_X + 2, GAME_H - 8);
        font.setColor(0.4f, 0.4f, 0.4f, 1f);
        font.draw(batch, "L/R:sprite  U/D:anim  BACK:menu", GAME_X + 2, GAME_H - 20);
        batch.end();
    }

    @Override
    public void dispose() {
        if (splashSprite != null) splashSprite.dispose();
        if (fontSprite   != null) fontSprite.dispose();
        if (curSprite    != null) curSprite.dispose();
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
