#version 330
#extension GL_ARB_separate_shader_objects : require

// White Knuckle's damage look over the world: damageFX grade + hurt vignette (see wk_damage.glsl).
// The values are rewritten every frame by the mod (ClimbDamage.updateColorGrade).

#include <wkclimb:wk_damage.glsl>

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform DamageConfig {
    float Saturation;
    float Bright;
    float Vignette;
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    fragColor = vec4(wk_damage(color, texCoord, OutSize, Saturation, Bright, Vignette), 1.0);
}
