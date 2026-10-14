#version 330

// White Knuckle's damage look over the world: damageFX grade + hurt vignette (see wk_damage.glsl).
// The values are rewritten every frame by the mod (ClimbDamage.updateColorGrade).

#moj_import <wkclimb:wk_damage.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform DamageConfig {
    float Saturation;
    float Bright;
    float Vignette;
};

out vec4 fragColor;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    fragColor = vec4(wk_damage(color, texCoord, OutSize, Saturation, Bright, Vignette), 1.0);
}
