#version 150

uniform sampler2D DiffuseSampler;
uniform float Saturation;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 c = texture(DiffuseSampler, texCoord);
    float luma = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));
    // Saturation 1.0 = full color, 0.0 = fully desaturated (grayscale).
    fragColor = vec4(mix(vec3(luma), c.rgb, Saturation), c.a);
}
