package org.dynamisengine.light.api.scene;

/**
 * Vec3 API type. Lightweight scene-descriptor DTO.
 */
public record Vec3(float x, float y, float z) {

    public static Vec3 zero()    { return new Vec3(0, 0, 0); }
    public static Vec3 one()     { return new Vec3(1, 1, 1); }
    public static Vec3 up()      { return new Vec3(0, 1, 0); }
    public static Vec3 down()    { return new Vec3(0, -1, 0); }
    public static Vec3 forward() { return new Vec3(0, 0, 1); }
    public static Vec3 back()    { return new Vec3(0, 0, -1); }
    public static Vec3 right()   { return new Vec3(1, 0, 0); }
    public static Vec3 left()    { return new Vec3(-1, 0, 0); }
    public static Vec3 of(float x, float y, float z) { return new Vec3(x, y, z); }
    public static Vec3 all(float v) { return new Vec3(v, v, v); }

    /** Color from 0-255 int RGB. */
    public static Vec3 rgb(int r, int g, int b) { return new Vec3(r / 255f, g / 255f, b / 255f); }
}
