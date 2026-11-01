package org.asc3port;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/** Localised strings (pack "EN", entries EN_000..EN_009 = sections). */
public final class StringTable {
    private final String[] s;
    public StringTable(String file) {
        JsonValue j = new JsonReader().parse(Gdx.files.internal("strings/" + file + ".json"));
        JsonValue arr = j.get("strings");
        s = new String[arr.size];
        int i = 0;
        for (JsonValue v : arr) s[i++] = v.asString();
    }
    public String get(int i) { return i >= 0 && i < s.length ? s[i] : ""; }
    public int size() { return s.length; }
}
