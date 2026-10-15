#version 330

// The screen blood (FX_Player_BloodSplatter_Screen). The game renders it with its Overlay Camera, after
// the post-processing but under the UI canvas, so the damage grade doesn't touch it while the hurt
// vignette is laid over it (wk_damage.glsl). The vignette is a per-pixel blend toward red, so applying
// it to the splat before it's drawn gives the same result as drawing the vignette over it.
// Vertex colour: r = vignette amount, a = the splat's opacity. The blood colour is BLOOD_TINT.

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <wkclimb:wk_damage.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// the particle colour, (135, 11, 19) / 255
const vec3 BLOOD_TINT = vec3(0.5294, 0.0431, 0.0745);

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    float alpha = texel.a * vertexColor.a;
    if (alpha == 0.0) {
        discard;
    }
    vec3 rgb = wk_vignette(texel.rgb * BLOOD_TINT, gl_FragCoord.xy / ScreenSize, ScreenSize, vertexColor.r);
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
