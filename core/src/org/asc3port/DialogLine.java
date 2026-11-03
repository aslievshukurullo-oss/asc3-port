package org.asc3port;

/**
 * Одна реплика диалога.
 *
 * Формат строки: "#XY текст" или просто "текст"
 *   X = speaker index (0=Connor, 1=tutorial/hint, 2=ally, 3+=enemy/boss)
 *   Y = portrait variant
 *   "*" after Y = hint box (instructions), not a dialog bubble
 */
public class DialogLine {
    public final String text;
    public final int    speaker;  // 0=Connor,1=hint,2=ally,3=enemy,11=Haytham,12=kid,13=CharlesLee,20=ally2
    public final boolean isHint;  // tutorial instruction

    public DialogLine(String raw) {
        String t = raw.trim();
        if (t.length() > 2 && t.charAt(0) == '#' && Character.isDigit(t.charAt(1))) {
            // parse speaker
            int spk = Character.getNumericValue(t.charAt(1));
            // second char: portrait variant or '*'
            boolean hint = (t.length() > 2 && t.charAt(2) == '*');
            isHint = hint;
            // speaker can be 2-digit: #XY or #X* (single digit)
            int start;
            if (!hint && t.length() > 3 && Character.isDigit(t.charAt(2))) {
                // two-digit speaker: e.g. #20, #13, #11
                spk = spk * 10 + Character.getNumericValue(t.charAt(2));
                start = 3;
            } else {
                start = hint ? 3 : 3;
            }
            speaker = spk;
            text = (start < t.length()) ? t.substring(start).trim() : "";
        } else {
            speaker = -1; // narrator / unknown
            isHint  = false;
            text    = t;
        }
    }

    /** Human-readable speaker name for HUD. */
    public String speakerName() {
        switch (speaker) {
            case 0:  return "Connor";
            case 1:  return "Connor";
            case 2:  return "Ally";
            case 3:  return "Enemy";
            case 11: return "Haytham";
            case 12: return "Child";
            case 13: return "Charles Lee";
            case 20: return "Captain";
            case 21: return "Ally";
            default: return "";
        }
    }
}
