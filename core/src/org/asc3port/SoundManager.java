package org.asc3port;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;

/**
 * Менеджер звука для AC3 порта.
 *
 * Аудио файлы (android/assets/audio/):
 *   p3_006..p3_037 — mp3 файлы (SFX и короткие треки)
 *   p3_000..p3_005 — mid файлы (фоновая музыка, LibGDX может не поддерживать)
 *
 * Маппинг (определён эмпирически по размеру и порядку в J2ME):
 *   p3_006 — шаги/footstep
 *   p3_007 — прыжок
 *   p3_008 — приземление
 *   p3_009 — лёгкий удар (light attack hit)
 *   p3_010 — тяжёлый удар (heavy attack)
 *   p3_011 — получение урона (hurt)
 *   p3_012 — смерть врага
 *   p3_013 — смерть игрока
 *   p3_014 — чекпоинт
 *   p3_015 — переход уровня
 *   p3_016 — фоновая музыка уровня (самый большой файл)
 *   p3_037 — музыка меню (6KB, самый большой из оставшихся)
 */
public class SoundManager {

    private static SoundManager instance;

    // SFX
    private Sound sndFootstep;
    private Sound sndJump;
    private Sound sndLand;
    private Sound sndLightHit;
    private Sound sndHeavyHit;
    private Sound sndHurt;
    private Sound sndEnemyDie;
    private Sound sndPlayerDie;
    private Sound sndCheckpoint;
    private Sound sndLevelEnd;

    // Music
    private Music bgmGame;
    private Music bgmMenu;
    private Music currentMusic;

    // Настройки
    private boolean sfxEnabled  = true;
    private boolean musicEnabled = true;
    private float sfxVolume   = 0.8f;
    private float musicVolume = 0.5f;

    // Кулдаун шагов (не спамить)
    private float footstepCooldown = 0f;
    private static final float FOOTSTEP_INTERVAL = 0.28f;

    public static SoundManager get() {
        if (instance == null) instance = new SoundManager();
        return instance;
    }

    private SoundManager() {
        load();
    }

    private void load() {
        sndFootstep  = loadSound("p3_006.mp3");
        sndJump      = loadSound("p3_007.mp3");
        sndLand      = loadSound("p3_008.mp3");
        sndLightHit  = loadSound("p3_009.mp3");
        sndHeavyHit  = loadSound("p3_010.mp3");
        sndHurt      = loadSound("p3_011.mp3");
        sndEnemyDie  = loadSound("p3_012.mp3");
        sndPlayerDie = loadSound("p3_013.mp3");
        sndCheckpoint= loadSound("p3_014.mp3");
        sndLevelEnd  = loadSound("p3_015.mp3");

        bgmGame = loadMusic("p3_016.mp3");
        bgmMenu = loadMusic("p3_037.mp3");

        if (bgmGame != null) { bgmGame.setLooping(true); bgmGame.setVolume(musicVolume); }
        if (bgmMenu != null) { bgmMenu.setLooping(true); bgmMenu.setVolume(musicVolume); }
    }

    private Sound loadSound(String name) {
        try {
            return Gdx.audio.newSound(Gdx.files.internal("audio/" + name));
        } catch (Exception e) { return null; }
    }

    private Music loadMusic(String name) {
        try {
            return Gdx.audio.newMusic(Gdx.files.internal("audio/" + name));
        } catch (Exception e) { return null; }
    }

    // ── SFX ─────────────────────────────────────────────────────────

    public void update(float dt) {
        if (footstepCooldown > 0) footstepCooldown -= dt;
    }

    public void playFootstep() {
        if (footstepCooldown <= 0) {
            play(sndFootstep, 0.6f);
            footstepCooldown = FOOTSTEP_INTERVAL;
        }
    }

    public void playJump()      { play(sndJump,      1.0f); }
    public void playLand()      { play(sndLand,      0.8f); }
    public void playLightHit()  { play(sndLightHit,  1.0f); }
    public void playHeavyHit()  { play(sndHeavyHit,  1.0f); }
    public void playHurt()      { play(sndHurt,      1.0f); }
    public void playEnemyDie()  { play(sndEnemyDie,  1.0f); }
    public void playPlayerDie() { play(sndPlayerDie, 1.0f); }
    public void playCheckpoint(){ play(sndCheckpoint, 1.0f); }
    public void playLevelEnd()  { play(sndLevelEnd,  1.0f); }

    private void play(Sound s, float vol) {
        if (sfxEnabled && s != null) s.play(vol * sfxVolume);
    }

    // ── Music ────────────────────────────────────────────────────────

    public void playGameMusic() {
        switchMusic(bgmGame);
    }

    public void playMenuMusic() {
        switchMusic(bgmMenu);
    }

    public void stopMusic() {
        if (currentMusic != null) { currentMusic.stop(); currentMusic = null; }
    }

    private void switchMusic(Music next) {
        if (!musicEnabled || next == null) return;
        if (currentMusic == next && currentMusic.isPlaying()) return;
        if (currentMusic != null) currentMusic.stop();
        currentMusic = next;
        currentMusic.setVolume(musicVolume);
        currentMusic.play();
    }

    // ── Settings ─────────────────────────────────────────────────────

    public void setSfxEnabled(boolean v)   { sfxEnabled = v; }
    public void setMusicEnabled(boolean v) {
        musicEnabled = v;
        if (!v) stopMusic();
    }
    public boolean isSfxEnabled()   { return sfxEnabled; }
    public boolean isMusicEnabled() { return musicEnabled; }

    public void dispose() {
        stopMusic();
        disposeSound(sndFootstep); disposeSound(sndJump);     disposeSound(sndLand);
        disposeSound(sndLightHit); disposeSound(sndHeavyHit); disposeSound(sndHurt);
        disposeSound(sndEnemyDie); disposeSound(sndPlayerDie);
        disposeSound(sndCheckpoint); disposeSound(sndLevelEnd);
        disposeMusic(bgmGame); disposeMusic(bgmMenu);
        instance = null;
    }

    private void disposeSound(Sound s) { if (s != null) s.dispose(); }
    private void disposeMusic(Music m) { if (m != null) m.dispose(); }
}
