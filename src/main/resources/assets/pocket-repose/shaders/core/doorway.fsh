#version 150

#moj_import <fog.glsl>

// Draws a hallway behind an open pocket block, as big as its opening: for a door, walls and ceiling of the door's
// wood (as planks) and a floor that carries on the top of the block the door stands on; for a suitcase or chest, a
// shaft down, walled on all four sides. It falls into darkness within about a block.

uniform sampler2D Sampler0; // the walls (for the chest, the whole chest texture)
uniform sampler2D Sampler1; // the floor: the top of the block the door stands on
// (Sampler2 is the lightmap)
uniform sampler2D Sampler3; // the door's bottom texture
uniform sampler2D Sampler4; // the door's top texture

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
// The camera's position within its block, so the floor lines up with the real blocks around it
uniform vec3 CameraFraction;

in float vertexDistance;
in vec3 viewPos;
in vec2 doorPos;
in vec4 doorBounds;
in vec3 floorTint;
in vec3 planeNormal;
in vec4 lightMapColor;

out vec4 fragColor;

// How far back (in pixels, 16 to a block) the hallway can be seen before it's completely dark
const float FADE_DEPTH = 16.0;

// A texture tiled every 16 pixels (whatever its resolution), using only its first frame if it's animated
vec3 tiled(sampler2D tex, vec2 st) {
    ivec2 size = textureSize(tex, 0);
    vec2 f = fract(st / 16.0);
    return texelFetch(tex, ivec2(min(f * float(size.x), vec2(size.x - 1))), 0).rgb;
}

// The inside of the chest lid (the dark planks in the middle of it, 10x10 at 16,2 of the 64x64 chest texture)
vec3 chestLining(vec2 st) {
    ivec2 size = textureSize(Sampler0, 0);
    float scale = float(size.x) / 64.0;
    vec2 f = fract(st / 10.0);
    return texelFetch(Sampler0, ivec2((vec2(16.0, 2.0) + f * 10.0) * scale), 0).rgb;
}

// A pixel of the door, 16 across (0 at the hinge) by 32 up
vec4 doorTexel(vec2 st) {
    float col = clamp(floor(st.x), 0.0, 15.0);
    float row = clamp(floor(st.y), 0.0, 31.0);
    if (row < 16.0) {
        ivec2 size = textureSize(Sampler3, 0);
        return texelFetch(Sampler3, ivec2(vec2(col, 15.0 - row) / 16.0 * vec2(size)), 0);
    }
    ivec2 size = textureSize(Sampler4, 0);
    return texelFetch(Sampler4, ivec2(vec2(col, 31.0 - row) / 16.0 * vec2(size)), 0);
}

vec3 surface(float kind, vec2 st, bool chest) {
    if (kind > 2.5) {
        // The opened door, lying along the wall from the doorway back; the planks show through its windows
        if (st.x < 16.0) {
            vec4 door = doorTexel(st);
            if (door.a > 0.5) {
                return door.rgb;
            }
        }
        return tiled(Sampler0, st);
    }
    if (kind > 1.5) {
        return tiled(Sampler1, st) * floorTint;
    }
    if (chest) {
        return chestLining(st);
    }
    return tiled(Sampler0, st);
}

void main() {
    vec3 dir = normalize(viewPos);
    vec3 n = normalize(planeNormal);
    int flags = int(doorBounds.r + 0.5);
    float uSign = (flags & 1) != 0 ? 1.0 : -1.0;
    bool shaft = (flags & 2) != 0;
    bool chest = (flags & 4) != 0;
    // The opening's axes: a doorway runs across its tangent and up; a top opening runs along x and z
    bool facingUp = abs(n.y) > 0.5;
    vec3 t = facingUp ? vec3(1.0, 0.0, 0.0) : vec3(n.z, 0.0, -n.x);
    vec3 b = facingUp ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);

    // The view ray in opening space: across, along, and into the hallway (from either side)
    vec2 across = vec2(dot(dir, t) * uSign, dot(dir, b));
    float into = max(abs(dot(dir, n)), 0.0001);

    float uMin = doorBounds.g;
    float uMax = doorBounds.b;
    float vMax = doorBounds.a;

    // Distance along the ray (in pixels) to the side walls and to the ceiling or floor
    float ku = 1.0e6;
    if (across.x > 0.0) ku = (uMax - doorPos.x) / across.x;
    else if (across.x < 0.0) ku = (uMin - doorPos.x) / across.x;
    float kv = 1.0e6;
    if (across.y > 0.0) kv = (vMax - doorPos.y) / across.y;
    else if (across.y < 0.0) kv = (0.0 - doorPos.y) / across.y;

    float k = max(min(ku, kv), 0.0);
    float depth = into * k;

    // Where we hit, in pixels on that surface: 0 = side wall, 1 = ceiling (or a shaft's other walls), 2 = floor,
    // 3 = a doorway's hinge-side wall, where the opened door lies
    float kind;
    vec2 st;
    float shade;
    if (ku < kv) {
        // The hinge side is at uMin; in a double door the far side (past column 16) is the other door's hinge side.
        // The opened door only lies along it when the hallway runs the way the door swings: from the front of the
        // plane (its normal's side; doors swing away from it). From behind, the real door is already in view.
        bool fromFront = dot(dir, n) < 0.0;
        bool hingeSide = !shaft && fromFront && (across.x < 0.0 || uMax > 16.5);
        kind = hingeSide ? 3.0 : 0.0;
        st = vec2(depth, doorPos.y + across.y * k);
        shade = 0.7;
    } else if (across.y > 0.0 || shaft) {
        kind = 1.0;
        st = vec2(depth, doorPos.x + across.x * k);
        shade = 0.85;
    } else {
        // The floor follows the world's block grid, like the block under the door
        kind = 2.0;
        vec3 hit = CameraFraction + viewPos + dir * (k / 16.0);
        st = fract(hit.xz) * 16.0;
        shade = 1.0;
    }

    // Average four samples spread over this pixel's footprint, so thin lines far down the hallway don't flicker
    vec2 dx = clamp(dFdx(st), vec2(-4.0), vec2(4.0)) * 0.25;
    vec2 dy = clamp(dFdy(st), vec2(-4.0), vec2(4.0)) * 0.25;
    vec3 wall = (surface(kind, st + dx + dy, chest) + surface(kind, st + dx - dy, chest)
               + surface(kind, st - dx + dy, chest) + surface(kind, st - dx - dy, chest)) * 0.25;

    float light = clamp(1.0 - depth / FADE_DEPTH, 0.0, 1.0);
    light *= light;

    vec4 color = vec4(wall * shade * light, 1.0) * ColorModulator * lightMapColor;
    color.a = 1.0;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
