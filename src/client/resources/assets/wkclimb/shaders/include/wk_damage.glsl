// White Knuckle's damage look, measured against the game and read from its post shader
// ("Hidden/Dark Machine/Post Process WK"). Shared by the screen pass (post/damage) and the hands
// (core/hand), which the game draws in the world, under the same effect.
//
// The game works in linear light:
//  1. HSV saturation x _HSV.y, value kept: each pixel fades toward its brightest channel, so a
//     bright green turns white rather than grey (damageFX.hsv.y = 0.1 at full blend).
//  2. x (0.25 + 0.75 * _GAMMA), _GAMMA = brightness setting + damageFX.gamma (2) x blend. Passed
//     here relative to full health, so the picture only changes once hurt.
//  3. The UI "hurt" vignette on top: Color.Lerp(clear, (154, 7, 7, 208) / 255, amount), so colour
//     and opacity both scale with the amount, blended in linear light. Its image (a soft radial
//     falloff) is approximated by a fitted curve, stretched 400 x 200 UI units past the screen.

vec3 wk_to_linear(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
}

vec3 wk_to_srgb(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}

// Opacity of the vignette image at distance r from its centre (0.5 = middle of an edge).
float wk_vignette_alpha(float r) {
    return 0.0864 + 0.9136 * (1.0 - exp(-pow(r / 0.2872, 2.751)));
}

// The hurt vignette over a linear colour (step 3). uv: 0..1 across the screen; size: screen size in pixels.
vec3 wk_vignette_linear(vec3 c, vec2 uv, vec2 size, float vignette) {
    if (vignette <= 0.0) return c;
    // UI units per pixel, as ClimbCrosshair.uiUnit (the game's canvas scaler)
    float unit = min(1.0, size.x / 6000.0) * 3.375;
    if (floor(size.x / size.y) > 2.5) unit *= 0.6;
    vec2 t = (uv - 0.5) / (1.0 + vec2(400.0, 200.0) * unit / size);
    float a = 0.81569 * vignette * wk_vignette_alpha(length(t));
    vec3 tint = wk_to_linear(vec3(154.0, 7.0, 7.0) / 255.0 * vignette);
    return c * (1.0 - a) + tint * a;
}

// The hurt vignette alone, for what the game draws after its post-processing but under its UI
// (the screen blood on the Overlay Camera). color: sRGB.
vec3 wk_vignette(vec3 color, vec2 uv, vec2 size, float vignette) {
    return wk_to_srgb(wk_vignette_linear(wk_to_linear(color), uv, size, vignette));
}

// The whole look: grade, brightness, then the vignette. color: sRGB.
vec3 wk_damage(vec3 color, vec2 uv, vec2 size, float saturation, float bright, float vignette) {
    vec3 c = wk_to_linear(color);
    float value = max(c.r, max(c.g, c.b));
    c = clamp(mix(vec3(value), c, saturation) * bright, 0.0, 1.0);
    return wk_to_srgb(wk_vignette_linear(c, uv, size, vignette));
}
