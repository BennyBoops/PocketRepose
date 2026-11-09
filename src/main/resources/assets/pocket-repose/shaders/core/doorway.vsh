#version 150

#moj_import <fog.glsl>

// The opening of a pocket block: a door's doorway, or the top of a suitcase, barrel or chest. Its vertices carry:
//   UV0    - where the vertex is in the opening, in pixels: across (u) and up/along (v, from 0)
//   Color  - r: flags (1: u grows along the plane's tangent, see fsh; 2: a shaft, walled on all four sides with
//            no floor; 4: walls lined with the chest texture's lid lining); g, b: the opening's first and last+1 u;
//            a: its length along v in pixels
//   UV1    - the tint of the floor block's top (grass, for example): x = red | green << 8, y = blue
//   Normal - the plane's normal on the side it was built facing (either side is drawn)

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
// In 1.20.1 block entities arrive turned to face the camera; this turns them back to the world's axes
uniform mat3 IViewRotMat;
uniform int FogShape;

out float vertexDistance;
out vec3 viewPos;
out vec2 doorPos;
out vec4 doorBounds;
out vec3 floorTint;
out vec3 planeNormal;
out vec4 lightMapColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // Relative to the camera, along the world's axes: also the direction we're looking along
    vec3 worldPos = IViewRotMat * Position;
    vertexDistance = fog_distance(ModelViewMat, worldPos, FogShape);
    viewPos = worldPos;
    doorPos = UV0;
    doorBounds = Color * 255.0;
    floorTint = vec3(UV1.x & 255, (UV1.x >> 8) & 255, UV1.y & 255) / 255.0;
    planeNormal = IViewRotMat * Normal;
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
}
