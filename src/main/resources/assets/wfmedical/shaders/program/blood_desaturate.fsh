#version 150

uniform sampler2D DiffuseSampler;
uniform float Saturation;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 c = texture(DiffuseSampler, texCoord);
    float luma = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));
    // Saturation 1.0 = full color, 0.0 = fully desaturated (grayscale).
    // Alpha MUST be 1.0, not c.a: the trailing minecraft:blit pass composites this texture with a srcalpha
    // blend over a target PostPass clears to transparent black, so the final colour = rgb * alpha. The scene's
    // framebuffer alpha is NOT meaningful here - the vanilla underwater overlay stamps it down to ~0.05, which
    // would multiply the whole desaturated image toward black. Forcing opaque alpha keeps the blit a pure copy.
    //This took 4 days to figure out btw
    fragColor = vec4(mix(vec3(luma), c.rgb, Saturation), 1.0);
}
