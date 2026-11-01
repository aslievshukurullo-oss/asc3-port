package org.asc3port;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
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

    // Размеры — только полный экран
    public static final int VIEW_W = 480, VIEW_H = 320;

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
        viewport = new FitViewport(VIEW_W, VIEW_H, cam);
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

        state = STATE_SPLASH;
        stateTimer = 0;
    }

    @Override
    public void resize(int w, int h) {
        viewport.update(w, h, true);
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
                    int tapped = getTappedMenuItem(pad.touchX, pad.touchY);
                    if (tapped >= 0) {
                        if (tapped == menuSel) activateMenu(tapped);
                        else menuSel = tapped;
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
        gameCam.update(player.x, player.y);
    }

    private int getTappedMenuItem(float tx, float ty) {
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            float itemY = MENU_Y0 + i * MENU_DY;
            if (tx >= MENU_X - 20 && tx <= MENU_X + MENU_W + 20
                    && ty >= itemY - 16 && ty <= itemY + 16) return i;
        }
        return -1;
    }

    private void activateMenu(int item) {
        switch (item) {
            case 0: // NEW GAME
                startGame();
                break;
            default:
                break;
        }
    }

    private void startGame() {
        // Пробуем загрузить TileMap уровня 1
        try {
            tileMap  = new TileMap(1);
            player   = new Player(80, 200);
            gameCam  = new GameCamera(VIEW_W, VIEW_H,
                                      tileMap.getPixelWidth(),
                                      tileMap.getPixelHeight());
            gameCam.snapTo(player.x, player.y);
            gameLoaded = true;
            useSpriteViewer = false;
        } catch (Exception e) {
            // Карта не загрузилась — используем просмотрщик спрайтов
            gameLoaded = false;
            useSpriteViewer = true;
            loadSprite(0);
        }
        state = STATE_GAME;
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
        shapes.rect(0, 0, VIEW_W, VIEW_H);
        shapes.end();

        batch.begin();
        if (splashSprite != null && splashSprite.frames.length > 0) {
            int fi = (stateTimer > 1.5f && splashSprite.frames.length > 1) ? 1 : 0;
            splashSprite.drawFrame(batch, fi, VIEW_W / 2f, VIEW_H / 2f, 0);
        } else {
            font.setColor(1f, 0.85f, 0.2f, 1f);
            font.draw(batch, "ASSASSIN'S CREED III", VIEW_W / 2f - 110, VIEW_H / 2f - 30);
            font.setColor(0.7f, 0.7f, 0.7f, 1f);
            font.draw(batch, "Mobile Port", VIEW_W / 2f - 50, VIEW_H / 2f + 10);
            font.setColor(0.4f, 0.4f, 0.4f, 1f);
            font.draw(batch, "Tap to continue", VIEW_W / 2f - 70, VIEW_H - 30);
        }
        batch.end();

        float pct = Math.min(stateTimer / SPLASH_TIME, 1f);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.20f, 0.20f, 0.25f, 1f);
        shapes.rect(40, VIEW_H - 18, VIEW_W - 80, 6);
        shapes.setColor(0.9f, 0.7f, 0.1f, 1f);
        shapes.rect(40, VIEW_H - 18, (VIEW_W - 80) * pct, 6);
        shapes.end();
    }

    private void drawMenu() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.05f, 0.06f, 0.10f, 1f);
        shapes.rect(0, 0, VIEW_W, VIEW_H);
        shapes.setColor(0.12f, 0.10f, 0.03f, 1f);
        shapes.rect(0, 0, 140, VIEW_H);
        shapes.rect(VIEW_W - 140, 0, 140, VIEW_H);
        shapes.end();

        batch.begin();
        font.setColor(0.95f, 0.80f, 0.15f, 1f);
        font.draw(batch, "ASSASSIN'S CREED III", VIEW_W / 2f - 120, 40);
        font.setColor(0.5f, 0.5f, 0.5f, 1f);
        font.draw(batch, "J2ME  Port", VIEW_W / 2f - 45, 68);
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.5f, 0.4f, 0.1f, 1f);
        shapes.rect(MENU_X - 20, 82, MENU_W + 40, 1);
        shapes.end();

        batch.begin();
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            float itemY = MENU_Y0 + i * MENU_DY;
            boolean sel = i == menuSel;
            if (sel) {
                batch.end();
                shapes.begin(ShapeRenderer.ShapeType.Filled);
                shapes.setColor(0.22f, 0.18f, 0.04f, 0.9f);
                shapes.rect(MENU_X - 10, itemY - 16, MENU_W + 20, 28);
                shapes.setColor(0.9f, 0.7f, 0.1f, 1f);
                shapes.rect(MENU_X - 10, itemY - 16, 3, 28);
                shapes.end();
                batch.begin();
            }
            font.setColor(sel ? 1f : 0.65f, sel ? 0.85f : 0.65f, sel ? 0.1f : 0.65f, 1f);
            font.draw(batch, MENU_ITEMS[i], MENU_X + (sel ? 8 : 6), itemY + 8);
        }
        font.setColor(0.35f, 0.35f, 0.35f, 1f);
        font.draw(batch, "TAP to select  TAP again / OK to confirm", 60, VIEW_H - 10);
        batch.end();
    }

    private void drawGame() {
        if (tileMap == null || gameCam == null) return;

        // Небо / фон
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.35f, 0.55f, 0.75f, 1f);
        shapes.rect(0, 0, VIEW_W, VIEW_H);
        shapes.end();

        batch.begin();

        // Рисуем тайловую карту
        tileMap.draw(batch, gameCam.x, gameCam.y, VIEW_W, VIEW_H);

        // Рисуем игрока
        if (player != null) {
            player.draw(batch, gameCam.x, gameCam.y);
        }

        // HUD
        font.setColor(1f, 1f, 1f, 0.8f);
        if (player != null) {
            font.draw(batch, String.format("X:%.0f Y:%.0f", player.x, player.y), 125, 14);
        }
        font.setColor(0.35f, 0.35f, 0.35f, 1f);
        font.draw(batch, "L/R: move  OK: jump  L-soft: menu", 125, VIEW_H - 8);

        batch.end();
    }

    private void drawSpriteViewer() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.08f, 0.09f, 0.12f, 1f);
        shapes.rect(0, 0, VIEW_W, VIEW_H);
        shapes.end();

        if (curSprite == null) {
            batch.begin();
            font.setColor(1f, 0.3f, 0.3f, 1f);
            font.draw(batch, "No sprite loaded", VIEW_W / 2f - 70, VIEW_H / 2f);
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
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
