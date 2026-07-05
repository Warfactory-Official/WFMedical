#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 BlurDir;
uniform float Radius;

out vec4 fragColor;

// Standard vanilla-style directional (separable) gaussian/triangular blur. Two chained passes with
// BlurDir = (1,0) then (0,1) yield a full 2D blur. Weights use a triangular falloff and are normalised by
// their accumulated total, so the result stays correctly exposed for ANY Radius (including a non-integer
// Radius fed by a smooth client-side fade, and Radius == 0 which collapses to the untouched centre texel).
void main() {
    vec4 blurred = vec4(0.0);
    float totalStrength = 0.0;
    for (float r = -Radius; r <= Radius; r += 1.0) {
        float strength = 1.0 - abs(r) / (Radius + 1.0);
        blurred += texture(DiffuseSampler, texCoord + oneTexel * r * BlurDir) * strength;
        totalStrength += strength;
    }
    fragColor = blurred / totalStrength;
}
