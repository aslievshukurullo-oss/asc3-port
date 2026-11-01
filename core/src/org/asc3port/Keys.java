package org.asc3port;

/** Key bitmasks exactly as produced by k.ag(int) in the original MIDlet (Nokia keycodes -> bit). */
public final class Keys {
    public static final int NUM0 = 1, NUM1 = 2, NUM2 = 4, NUM3 = 8, NUM4 = 16, NUM5 = 32,
            NUM6 = 64, NUM7 = 128, NUM8 = 256, NUM9 = 512;
    public static final int UP = 1024, DOWN = 2048, LEFT = 4096, RIGHT = 8192, FIRE = 16384;
    public static final int SOFT_L = 32768, SOFT_R = 65536, CLEAR = 131072;
    public static final int STAR = 262144, POUND = 524288;
    private Keys() {}
}
