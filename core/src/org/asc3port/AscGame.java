package org.asc3port;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * ASC3 Port — LibGDX/Android, горизонтальный режим.
 *
 * Виртуальный экран: 480×320 (y-down). Контент заполняет весь экран.
 * Автомат состояний:
 *   STATE_INIT  (1) – инициализация
 *   STATE_SPLASH(2) – заставка Gameloft / AC3 (~3 сек)
 *   STATE_MENU  (5) – главное меню (touch + D-pad)
 *   STATE_GAME  (7) – геймплей с TileMap, Player, Camera
 */
public class AscGame extends ApplicationAdapter {

    // Базовые размеры виртуального экрана (реальные обновляются в resize)
    public static final int VIEW_W = 480, VIEW_H = 320;
    // Актуальные размеры после адаптации к экрану устройства
    private int screenW = VIEW_W, screenH = VIEW_H;

    // Состояния
    private static final int STATE_INIT   = 1;
    private static final int STATE_SPLASH = 2;
    private static final int STATE_MENU   = 5;
    private static final int STATE_GAME   = 7;

    private static final float SPLASH_TIME = 3.0f;

    // --- Графика ---
    private OrthographicCamera cam;
    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private TouchOverlay pad;

    // --- Ассеты заставки/меню ---
    private ASprite splashSprite;

    // --- Меню ---
    private static final String[] MENU_ITEMS = {
        "NEW GAME", "CONTINUE", "OPTIONS", "HELP", "ABOUT"
    };
    private static final int MENU_X  = 160;
    private static final int MENU_W  = 160;
    private static final int MENU_Y0 = 110;
    private static final int MENU_DY = 36;
    private int menuSel = 0;

    // --- Геймплей (STATE_GAME) ---
    private TileMap tileMap;
    private Player player;
    private GameCamera gameCam;
    private CombatSystem combat;
    private Checkpoint checkpoint;
    private boolean gameLoaded = false;

    // Запасной просмотрщик спрайтов (если карта не загрузится)
    private String[] spriteNames;
    private int sprCur = 0, sprAnim = 0, sprStep = 0;
    private float sprTimer = 0;
    private ASprite curSprite;
    private static final float TICK = 1f / 25f;
    private boolean useSpriteViewer = false;

    // --- Автомат состояний ---
    private int state = STATE_INIT;
    private float stateTimer = 0;

    @Override
    public void create() {
        cam = new OrthographicCamera();
        cam.setToOrtho(true, VIEW_W, VIEW_H); // y-down
        // ExtendViewport: минимум 480×320, расширяется на весь реальный экран без чёрных полос
        viewport = new ExtendViewport(VIEW_W, VIEW_H, cam);
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont(true); // flipped для y-down
        font.getData().setScale(1.4f);
        pad = new TouchOverlay(viewport);

        try { splashSprite = new ASprite("p4_000"); } catch (Exception e) { splashSprite = null; }

        // Загружаем список спрайтов как запасной вариант
        try {
            String idx = Gdx.files.internal("sprites/index.txt").readString().trim();
            spriteNames = idx.split("\\s+");
        } catch (Exception e) {
            spriteNames = new String[0];
        }

        // Загружаем сохранённый язык
        Lang.load();

        state = STATE_SPLASH;
        stateTimer = 0;
    }

    @Override
    public void resize(int w, int h) {
        viewport.update(w, h, true);
        // Обновляем актуальные размеры виртуального экрана (ExtendViewport расширяет их)
        screenW = (int) cam.viewportWidth;
        screenH = (int) cam.viewportHeight;
        // Пересчитываем GameCamera если она уже создана
        if (gameCam != null && tileMap != null) {
            gameCam.setViewSize(screenW, screenH);
        }
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f); // cap at 20fps
        pad.gameMode = (state == STATE_GAME);
        pad.update();
        update(dt);
        draw();
    }

    private void update(float dt) {
        stateTimer += dt;

        switch (state) {
            case STATE_SPLASH:
                if (stateTimer >= SPLASH_TIME
                        || (pad.pressed & (Keys.FIRE | Keys.NUM5)) != 0
                        || pad.touchJustDown) {
                    state = STATE_MENU;
                    stateTimer = 0;
                }
                break;

            case STATE_MENU:
                if ((pad.pressed & Keys.UP) != 0)
                    menuSel = (menuSel - 1 + MENU_ITEMS.length) % MENU_ITEMS.length;
                if ((pad.pressed & Keys.DOWN) != 0)
                    menuSel = (menuSel + 1) % MENU_ITEMS.length;
                if ((pad.pressed & (Keys.FIRE | Keys.NUM5)) != 0)
                    activateMenu(menuSel);

                if (pad.touchJustDown) {
                    // Кнопка языка (верхний правый угол)
                    if (pad.touchX >= screenW - 70 && pad.touchY <= 30) {
                        Lang.toggleLang();
                    } else {
                        int tapped = getTappedMenuItem(pad.touchX, pad.touchY);
                        if (tapped >= 0) {
                            if (tapped == menuSel) activateMenu(tapped);
                            else menuSel = tapped;
                        }
                    }
                }
                break;

            case STATE_GAME:
                if (useSpriteViewer) {
                    updateSpriteViewer(dt);
                } else {
                    updateGame(dt);
                }
                break;
        }
    }

    private void updateGame(float dt) {
        if (player == null || tileMap == null) return;

        // Возврат в меню
        if ((pad.pressed & Keys.SOFT_L) != 0) {
            state = STATE_MENU;
            stateTimer = 0;
            return;
        }

        player.update(dt, tileMap, pad.held);
        if (combat    != null) combat.update(dt, tileMap, player);
        if (checkpoint != null) checkpoint.update(player, 1);
        gameCam.update(player.x, player.y);

        // Смерть игрока — возврат в меню через 2 сек
        if (player.isDead) {
            stateTimer += dt;
            if (stateTimer > 2.0f) {
                state = STATE_MENU;
                stateTimer = 0;
            }
        }
    }

    private int getTappedMenuItem(float tx, float ty) {
        int menuX = (int)(screenW / 2f - MENU_W / 2f);
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            float itemY = MENU_Y0 + i * MENU_DY;
            if (tx >= menuX - 20 && tx <= menuX + MENU_W + 20
                    && ty >= itemY - 16 && ty <= itemY + 16) return i;
        }
        return -1;
    }

    private void activateMenu(int item) {
        switch (item) {
            case 0: startGame();    break; // NEW GAME
            case 1: continueGame(); break; // CONTINUE
            default: break;
        }
    }

    private void startGame() {
        startGameAt(null, true);
    }

    private void continueGame() {
        float[] saved = Checkpoint.load();
        startGameAt(saved, false);
    }

    private void startGameAt(float[] savedPos, boolean newGame) {
        if (combat != null) combat.clear();
        combat    = new CombatSystem();
        checkpoint = new Checkpoint();

        if (newGame) Checkpoint.clear();

        try {
            tileMap = new TileMap(1);
            float sx, sy;
            if (savedPos != null) {
                sx = savedPos[0]; sy = savedPos[1];
            } else {
                float[] spawn = tileMap.findSpawn();
                sx = spawn[0]; sy = spawn[1];
            }
            player  = new Player(sx, sy);
            gameCam = new GameCamera(screenW, screenH,
                                     tileMap.getPixelWidth(),
                                     tileMap.getPixelHeight());
            gameCam.snapTo(player.x, player.y);
            combat.spawnLevelEnemies(sx, sy);
            gameLoaded     = true;
            useSpriteViewer = false;
        } catch (Exception e) {
            gameLoaded     = false;
            useSpriteViewer = true;
            loadSprite(0);
        }
        state      = STATE_GAME;
        stateTimer = 0;
    }

    // ─── Просмотрщик спрайтов (запасной режим) ───────────────────────────────

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
        if ((pad.pressed & Keys.SOFT_L) != 0) { state = STATE_MENU; stateTimer = 0; }
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
        try { curSprite = new ASprite(spriteNames[sprCur]); } catch (Exception ignored) {}
        sprAnim = 0; sprStep = 0; sprTimer = 0;
    }

    // ─── ОТРИСОВКА ────────────────────────────────────────────────────────────

    private void draw() {
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply(true);
        shapes.setProjectionMatrix(cam.combined);
        batch.setProjectionMatrix(cam.combined);

        switch (state) {
            case STATE_SPLASH: drawSplash(); break;
            case STATE_MENU:   drawMenu();   break;
            case STATE_GAME:
                if (useSpriteViewer) drawSpriteViewer();
                else                 drawGame();
                break;
        }

        pad.draw(shapes, batch, font);
    }

    private void drawSplash() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.02f, 0.02f, 0.05f, 1f);
        shapes.rect(0, 0, screenW, screenH);
        shapes.end();

        batch.begin();
        if (splashSprite != null && splashSprite.frames.length > 0) {
            int fi = (stateTimer > 1.5f && splashSprite.frames.length > 1) ? 1 : 0;
            splashSprite.drawFrame(batch, fi, screenW / 2f, screenH / 2f, 0);
        } else {
            font.setColor(1f, 0.85f, 0.2f, 1f);
            font.draw(batch, "ASSASSIN'S CREED III", screenW / 2f - 110, screenH / 2f - 30);
            font.setColor(0.7f, 0.7f, 0.7f, 1f);
            font.draw(batch, "Mobile Port", screenW / 2f - 50, screenH / 2f + 10);
            font.setColor(0.4f, 0.4f, 0.4f, 1f);
            font.draw(batch, Lang.get("tap_continue"), screenW / 2f - 70, screenH - 30);
        }
        batch.end();

        float pct = Math.min(stateTimer / SPLASH_TIME, 1f);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.20f, 0.20f, 0.25f, 1f);
        shapes.rect(40, screenH - 18, screenW - 80, 6);
        shapes.setColor(0.9f, 0.7f, 0.1f, 1f);
        shapes.rect(40, screenH - 18, (screenW - 80) * pct, 6);
        shapes.end();
    }

    private void drawMenu() {
        float cx = screenW / 2f;
        int menuX = (int)(cx - MENU_W / 2f);

        // Фон — тёмный градиент с золотым отблеском по центру
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        int bgBands = 6;
        float bh = (float) screenH / bgBands;
        for (int i = 0; i < bgBands; i++) {
            float t = (float) i / (bgBands - 1);
            shapes.setColor(0.05f + t * 0.04f, 0.05f + t * 0.04f, 0.08f + t * 0.02f, 1f);
            shapes.rect(0, i * bh, screenW, bh + 1);
        }
        // Боковые панели темнее
        shapes.setColor(0.03f, 0.03f, 0.05f, 0.85f);
        shapes.rect(0, 0, menuX - 15, screenH);
        shapes.rect(menuX + MENU_W + 15, 0, screenW - (menuX + MENU_W + 15), screenH);
        // Центральная колонка с лёгкой подсветкой
        shapes.setColor(0.10f, 0.08f, 0.02f, 0.5f);
        shapes.rect(menuX - 15, 85, MENU_W + 30, screenH - 95);
        shapes.end();

        // Заголовок
        batch.begin();
        font.getData().setScale(1.6f);
        font.setColor(0.95f, 0.80f, 0.15f, 1f);
        font.draw(batch, "ASSASSIN'S CREED III", cx - 130, 38);
        font.getData().setScale(1.0f);
        font.setColor(0.45f, 0.42f, 0.35f, 1f);
        font.draw(batch, "J2ME PORT  ·  LibGDX", cx - 74, 62);
        font.getData().setScale(1.4f);
        // Кнопка языка (верхний правый угол)
        font.setColor(0.85f, 0.85f, 0.25f, 1f);
        font.draw(batch, "[" + Lang.currentLang() + "]", screenW - 54, 18);
        batch.end();

        // Разделитель
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.65f, 0.50f, 0.08f, 1f);
        shapes.rect(menuX - 20, 80, MENU_W + 40, 2);
        shapes.setColor(0.25f, 0.20f, 0.04f, 1f);
        shapes.rect(menuX - 20, 82, MENU_W + 40, 1);
        shapes.end();

        // Пункты меню
        batch.begin();
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            float itemY = MENU_Y0 + i * MENU_DY;
            boolean sel = i == menuSel;
            if (sel) {
                batch.end();
                shapes.begin(ShapeRenderer.ShapeType.Filled);
                shapes.setColor(0.22f, 0.17f, 0.03f, 0.92f);
                shapes.rect(menuX - 12, itemY - 18, MENU_W + 24, 30);
                // Золотая левая полоска
                shapes.setColor(0.95f, 0.72f, 0.08f, 1f);
                shapes.rect(menuX - 12, itemY - 18, 3, 30);
                shapes.end();
                batch.begin();
            }
            float alpha = sel ? 1f : 0.7f;
            font.setColor(sel ? 1.0f : 0.62f,
                          sel ? 0.88f : 0.62f,
                          sel ? 0.08f : 0.62f, alpha);
            font.draw(batch, Lang.menuItem(i), menuX + (sel ? 10 : 7), itemY + 9);
        }
        font.setColor(0.28f, 0.28f, 0.28f, 1f);
        font.getData().setScale(1.0f);
        font.draw(batch, Lang.get("menu_hint"), (int)(cx - 120), screenH - 10);
        font.getData().setScale(1.4f);
        batch.end();
    }

    private void drawGame() {
        if (tileMap == null || gameCam == null) return;

        // --- Параллакс-небо ---
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // Градиент неба: верх темнее (0.25, 0.45, 0.70), низ светлее (0.50, 0.72, 0.90)
        int skyBands = 8;
        float bandH = (float) screenH / skyBands;
        for (int i = 0; i < skyBands; i++) {
            float t = (float) i / (skyBands - 1); // 0 = верх, 1 = низ
            float r = 0.25f + t * 0.25f;
            float g = 0.45f + t * 0.27f;
            float b = 0.70f + t * 0.20f;
            shapes.setColor(r, g, b, 1f);
            shapes.rect(0, i * bandH, screenW, bandH + 1);
        }
        // Простые облака (параллакс × 0.15 от камеры)
        float cloudOffset = (gameCam.x * 0.15f) % screenW;
        shapes.setColor(1f, 1f, 1f, 0.55f);
        // Облако 1
        float c1x = 80 - cloudOffset; if (c1x < -80) c1x += screenW + 80;
        shapes.ellipse(c1x - 30, 35, 70, 24);
        shapes.ellipse(c1x - 10, 22, 50, 28);
        // Облако 2
        float c2x = 260 - cloudOffset; if (c2x < -80) c2x += screenW + 80;
        shapes.ellipse(c2x - 25, 50, 60, 20);
        shapes.ellipse(c2x,      38, 45, 26);
        // Облако 3
        float c3x = 420 - cloudOffset; if (c3x < -80) c3x += screenW + 80;
        shapes.ellipse(c3x - 35, 28, 80, 25);
        shapes.ellipse(c3x - 15, 16, 55, 28);
        shapes.end();

        batch.begin();
        tileMap.draw(batch, gameCam.x, gameCam.y, screenW, screenH);
        // Рендер врагов
        if (combat != null) {
            for (Enemy e : combat.getEnemies()) {
                e.draw(batch, gameCam.x, gameCam.y);
            }
        }
        if (player != null) player.draw(batch, gameCam.x, gameCam.y);
        batch.end();

        // HUD здоровья (ниже SpriteBatch — поверх всего через ShapeRenderer)
        if (player != null) drawHUD();
    }

    private void drawHUD() {
        final int HP_X = 125, HP_Y = 10;
        final int HP_W = 80,  HP_H = 8;
        final int HP_PAD = 2;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // Тёмный фон полосы HP
        shapes.setColor(0.1f, 0.1f, 0.1f, 0.75f);
        shapes.rect(HP_X - HP_PAD, HP_Y - HP_PAD, HP_W + HP_PAD * 2, HP_H + HP_PAD * 2);
        // Красная полоса HP
        float hpPct = (float) player.health / player.maxHealth;
        float hpColor_r = 0.2f + hpPct * 0.6f;
        shapes.setColor(hpColor_r, hpPct * 0.6f, 0.05f, 1f);
        shapes.rect(HP_X, HP_Y, HP_W * hpPct, HP_H);
        // Зелёный если много здоровья
        if (hpPct > 0.6f) shapes.setColor(0.15f, 0.75f, 0.15f, 1f);
        shapes.rect(HP_X, HP_Y, HP_W * Math.min(hpPct, 1f), HP_H);

        // BACK кнопка (правый угол)
        shapes.setColor(0.1f, 0.1f, 0.1f, 0.65f);
        shapes.rect(screenW - 68, 4, 50, 16);
        shapes.end();

        batch.begin();
        font.setColor(1f, 1f, 1f, 0.9f);
        font.getData().setScale(1.1f);
        font.draw(batch, "HP", HP_X - 20, HP_Y + HP_H - 1);
        font.setColor(1f, 0.85f, 0.3f, 0.95f);
        font.draw(batch, "BACK", screenW - 64f, HP_Y + HP_H - 1);

        // Отладка (координаты и стейт)
        font.setColor(0.5f, 0.5f, 0.5f, 0.65f);
        font.getData().setScale(0.85f);
        if (player != null)
            font.draw(batch, String.format("%.0f,%.0f  %s  ATK:%s",
                player.x, player.y,
                player.onGround ? "GND" : "AIR",
                player.attackState.name()), 125, screenH - 8);
        font.getData().setScale(1.4f);
        batch.end();
    }

    private void drawSpriteViewer() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.08f, 0.09f, 0.12f, 1f);
        shapes.rect(0, 0, screenW, screenH);
        shapes.end();

        if (curSprite == null) {
            batch.begin();
            font.setColor(1f, 0.3f, 0.3f, 1f);
            font.draw(batch, "No sprite loaded", screenW / 2f - 70, screenH / 2f);
            batch.end();
            return;
        }
        batch.begin();
        if (curSprite.anims.length > 0) {
            ASprite.Anim an = curSprite.anims[sprAnim];
            if (an.n > 0) {
                int afIdx = an.first + sprStep;
                if (afIdx < curSprite.afs.length) {
                    ASprite.AnimFrame af = curSprite.afs[afIdx];
                    curSprite.drawFrame(batch, af.frame,
                        VIEW_W / 2f + af.x, VIEW_H / 2f + af.y, af.tr);
                }
            }
        } else if (curSprite.frames.length > 0) {
            curSprite.drawFrame(batch, 0, VIEW_W / 2f, VIEW_H / 2f, 0);
        }
        if (spriteNames != null && sprCur < spriteNames.length) {
            font.setColor(1f, 1f, 1f, 0.9f);
            font.draw(batch, spriteNames[sprCur] + "  A:" + sprAnim
                + "/" + curSprite.anims.length + "  F:" + sprStep, 125, 12);
        }
        batch.end();
    }

    @Override
    public void dispose() {
        if (splashSprite != null) splashSprite.dispose();
        if (curSprite    != null) curSprite.dispose();
        if (tileMap      != null) tileMap.dispose();
        if (player       != null) player.dispose();
        if (combat       != null) combat.clear();
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
