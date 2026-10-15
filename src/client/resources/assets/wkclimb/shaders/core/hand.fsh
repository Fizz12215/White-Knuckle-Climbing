#version 330

// First-person hand sprites, as White Knuckle's SHDR_Base_Inventory draws them with the default hand
// cosmetic (read from the game's shader and material data), then its damage look (wk_damage.glsl;
// the game's hands are in the world, under the grade and the hurt vignette).
//
// Vertex colour: r = saturation, g = brightness - 1, b = vignette amount (damage look);
// a = 128/255 + this hand's stamina used (1 - grip%) x 127/255.
//
// Stamina, all in linear light (Item_Hand_Material: _StaminaColor red, _StaminaLevels 8,
// _StaminaMaskRepeat 8, _StaminaMaskRepeatIntensity 0.5, _StaminaMaskTightness 1, no stamina
// textures, so the mask is black and the colour mask white):
//  - outline-mask pixels (value 0.4902, the cosmetic's outlineMaskColor) go from _OutlineColor grey
//    to _StaminaOutlineColor red, linearly with the amount;
//  - everything else is multiplied toward red by s = round(8a)/8 + ceil(8a) x 0.5/8 (stepped, full
//    red once just over 62.5% is used: 5/8 + 6/16 = 1);
//  - then x _Color (0.8113 grey).

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <wkclimb:wk_damage.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const float OUTLINE = 0.4902;
const vec3 RED = vec3(1.0, 0.0, 0.0);

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    if (texel.a == 0.0) {
        discard;
    }
    float used = clamp((vertexColor.a * 255.0 - 128.0) / 127.0, 0.0, 1.0);
    vec3 c = wk_to_linear(texel.rgb);
    if (abs(texel.r - OUTLINE) < 0.008) {
        c = mix(wk_to_linear(vec3(OUTLINE)), RED, used);
    } else {
        float s = used > 0.0001 ? min(1.0, roundEven(used * 8.0) / 8.0 + ceil(used * 8.0) * 0.5 / 8.0) : 0.0;
        c *= mix(vec3(1.0), RED, s);
    }
    c *= wk_to_linear(vec3(0.8113));
    vec3 rgb = wk_damage(wk_to_srgb(c), gl_FragCoord.xy / ScreenSize, ScreenSize,
            vertexColor.r, 1.0 + vertexColor.g, vertexColor.b);
    fragColor = vec4(rgb, texel.a) * ColorModulator;
}
