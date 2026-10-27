#version 150

uniform sampler2D DiffuseSampler;
uniform float Haze;
uniform float Time;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 o = Haze * 0.010 * vec2(sin(Time * 0.7), sin(Time * 0.53 + 1.0));
    vec3 c = 0.5 * (texture(DiffuseSampler, texCoord + o).rgb + texture(DiffuseSampler, texCoord - o).rgb);
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(c, vec3(luma), 0.75 * Haze);
    c += Haze * vec3(0.09, 0.045, 0.0);
    // Alpha must stay 1.0; see blood_desaturate.fsh.
    fragColor = vec4(c, 1.0);
}
