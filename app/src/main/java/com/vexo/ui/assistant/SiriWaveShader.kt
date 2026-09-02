package com.vexo.ui.assistant

/**
 * **Animation version 2.** AGSL ports of the Siri-style GLSL shaders.
 *
 * The first two deviations from the GLSL originals are forced by the target. The rest are forced by
 * the surface: the source was written for a square canvas, and VEXO draws the wave into a band as
 * wide as the screen, where several of the source's constants stop meaning what they meant.
 *
 * 1. **Alpha is derived from brightness.** The originals return `vec4(col, 1.0)` because they draw
 *    onto an opaque black canvas. VEXO's window is translucent and floats over whatever app is in
 *    front, so an opaque return would paint a black slab across the bottom of the screen. Alpha is
 *    taken from the brightest channel and the colour returned premultiplied, which is exactly what
 *    the version 1 orb did via its `extractAlpha`. Where the shader is black it is now transparent.
 *
 * 2. **`iAudio` is folded into the existing band levels.** VEXO is a voice assistant, so a purely
 *    time-driven waveform would be a functional regression from the orb it replaces. The wave shader
 *    already synthesises fake `low`/`mid`/`high` levels from sine functions; real microphone
 *    amplitude is combined with `max()`, so at `iAudio = 0` nothing in the idle animation depends on
 *    the microphone and speech only ever adds movement.
 *
 * 3. **The waveform is normalised by aspect ratio.** `sin(p.x * FREQ)` becomes `sin(xN * FREQ)`.
 *    `p.x` grows with the aspect ratio, so on a wide surface the sine fitted extra cycles in and the
 *    lens turned into a wiggly ribbon; `xN` is already divided by the aspect, so the same waveform
 *    stretches to whatever width it is given. At aspect 1 this is exactly the original.
 *
 * 4. **Horizontal and vertical scale are separate** — `SPAN` and `VSCALE` in place of one
 *    `WAVE_SCALE`, which in a wide band would otherwise set the span and the crest height together.
 *
 * 5. **The side falloff is the amplitude envelope, not a Gaussian**, so brightness and amplitude
 *    reach zero at the same place and the wave tapers to a point rather than to a straight line.
 *
 * 6. **Six chromatic samples** and `spectral(float)` walks a Midnight Blue → Deep Ocean Blue → Aqua Cyan
 *    palette for the VEXO OCEAN look. Six is enough for smooth blending without averaging to white.
 *
 * Each is explained at the constant it belongs to. `AMPLITUDE`, `FREQ` and `ABER_FREQ` are retuned
 * for the wider surface; everything else — the spectral split, the metaball fields, the settle
 * curves, the band fill — is transcribed unchanged. Only type names (`vec2` → `float2`,
 * `mat2` → `float2x2`) and the entry point (`mainImage` → `half4 main`) are adapted, because SkSL is
 * stricter than GLSL about constructing vectors from scalars.
 */

/** The iOS voice waveform: chromatic, frequency-reactive. */
internal const val SIRI_WAVE_SHADER = """
uniform float2 iResolution;
uniform float iTime;
uniform float iAudio;

const float PI = 3.14159265359;
const float AMPLITUDE   = 0.40;
const float FREQ        = 2.2;
const float ABER_FREQ   = 2.0;
const float SPEED       = 2.0;
/*
 * Horizontal and vertical scale, split apart from the source's single `WAVE_SCALE = 0.6`, because
 * VEXO draws the wave into a wide band rather than the square the GLSL was written for. One scale
 * cannot serve both there: it sets the span *and* the amplitude at once.
 *
 * SPAN is in half-widths. xN runs to ±1/SPAN and the envelope forces amplitude to zero beyond
 * |xN| > 1.111, so 0.9 lands that zero exactly on the left and right edges — the wave spans the
 * whole band and tapers into nothing at both ends.
 *
 * VSCALE stays at the source value, which keeps the crest height and the line thickness in pixels
 * as they were. It also leaves headroom: the wave's loudest crest is 0.76 * VSCALE = 0.46 of the
 * half-height, inside the 0.6 where EDGE_MASK starts, so loud speech grows instead of clipping.
 */
const float SPAN        = 0.9;
const float VSCALE      = 0.6;
const float ABERRATION  = 2.8;
const float THICKNESS   = 3.5;
const float INTENSITY   = 2.2;
/*
 * Exponent on the amplitude envelope, used as the horizontal brightness falloff. The source used a
 * separate Gaussian, `exp(-(xN * 1.7)^2)`, which is wrong for a wide band in both directions: it
 * died out around 40 % of the width, well before the envelope it was meant to accompany, and where
 * it had not died the collapsing envelope left every coloured curve and the band fill sitting on
 * y = 0 — a straight bright line running out to both screen edges.
 *
 * Tying the falloff to the envelope makes brightness and amplitude vanish together, so the wave
 * tapers to a point instead of a line, with nothing to clip against.
 */
const float SIDE_FADE   = 1.5;
const float EDGE_MASK   = 0.4;
const float EDGE_INSET  = 0.0;
const float BAND_FILL   = 35000.0;
const float BAND_THICK  = 0.09;
const float SOFTNESS    = 3.0;
const float LOW_AMP     = 7.0;
const float LOW_INT     = 1.5;
const float MID_ABER    = 0.8;
const float MID_ABAMP   = 0.05;
const float MID_BAND    = 20.0;
const float MID_SOFT    = 0.4;
const float HIGH_ABER   = 0.5;
const float HIGH_ABAMP  = 0.06;
const float RESOLVED    = 1.0;
const float UNRES_SCALE = 0.14;

const float AUDIO_AMP = 0.35;

/*
 * Six chromatic samples. Fewer than eight keeps distinct colour bands visible instead of averaging
 * toward white; more than four gives smooth blending between them.
 */
const int SAMPLES = 6;

/*
 * VEXO OCEAN palette: Midnight Blue (#020D1A) → Deep Ocean Blue (#0B1E3A) → Aqua Cyan (#00E6FF).
 * Each colour is highly saturated so the chromatic aberration reads as vivid ocean bands, not a
 * faint wash. The weighted average is a deep ocean blue, which tints the bright core with cyan
 * instead of desaturating it.
 */
float3 spectral(float f) {
    float t = clamp(f, 0.0, 1.0) * 2.0;
    float3 c = mix(float3(0.008, 0.051, 0.102), float3(0.043, 0.118, 0.227), clamp(t, 0.0, 1.0));
    c = mix(c, float3(0.0, 0.902, 1.0), clamp(t - 1.0, 0.0, 1.0));
    return c;
}

half4 main(float2 fragCoord) {
    float2 R = iResolution;
    float aspect = R.x / R.y;
    float2 p = (fragCoord + float2(0.5)) * 2.0 / R - float2(1.0);
    p.x *= aspect;
    float yScreen = p.y;
    p /= float2(max(SPAN, 0.1), max(VSCALE, 0.1));

    float t = iTime;
    float audio = clamp(iAudio, 0.0, 1.0);
    float low  = max(clamp(0.45 + 0.45 * sin(t * 0.8) * sin(t * 0.37 + 1.0), 0.0, 1.0), audio);
    float mid  = max(clamp(0.40 + 0.40 * sin(t * 1.7 + 2.0) * sin(t * 0.53), 0.0, 1.0), audio);
    float high = max(clamp(0.30 + 0.30 * sin(t * 2.9 + 4.0) * sin(t * 0.71 + 2.0), 0.0, 1.0), audio);

    float res = clamp(RESOLVED, 0.0, 1.0);
    float drift = mod(t, 20.0 * PI) * SPEED;

    float xN = p.x / max(aspect, 1.0);
    float env = cos(PI * 0.5 * min(abs(0.9 * xN), 1.0));
    env *= env;

    float A1 = AMPLITUDE + 0.01 * low * LOW_AMP + audio * AUDIO_AMP;
    float A2 = A1 + mid * MID_ABAMP + high * HIGH_ABAMP;
    float AB = (ABERRATION + mid * MID_ABER + high * HIGH_ABER) * res;
    float th = mix(0.1, 0.01 * THICKNESS, res);
    float inten = mix(0.1, 0.01 * (INTENSITY + low * LOW_INT), res);
    float soft = 0.01 * res * max(0.0, SOFTNESS + mid * MID_SOFT);

    float dUnres = max(length(p) - mix(0.14, UNRES_SCALE, res), 0.0);
    float yMain = A1 * env * res * sin(xN * FREQ + drift);

    float bandFillTh = max(BAND_THICK, 0.0001);
    float bandAmt = 0.0001 * BAND_FILL * inten;
    float3 num = float3(0.0);
    float3 den = float3(0.0);
    for (int s = 0; s < SAMPLES; s++) {
        float f = float(s) / float(SAMPLES - 1);
        float3 hue = mix(float3(1.0), spectral(f), res);
        den += hue;
        float ab = mix(-AB, AB, f);
        float yL = A2 * env * res * sin(xN * ABER_FREQ + drift + ab);
        float d = mix(dUnres, abs(p.y - yL), res);
        float lor = mix(1.0 / (1.0 + (0.02 * d) * (0.02 * d)), 1.0, res);
        float line = inten / (sqrt(d * d + soft * soft) + th);
        float lo = min(yMain, yL);
        float hi = max(yMain, yL);
        float dBand = max(0.0, max(p.y - hi, lo - p.y));
        float band = bandAmt / (dBand + bandFillTh);
        num += hue * lor * (line + band);
    }
    float3 col = num / den;

    float dM = mix(dUnres, abs(p.y - yMain), res);
    float lorM = mix(1.0 / (1.0 + (0.02 * dM) * (0.02 * dM)), 1.0, res);
    float boost = (1.0 - res) * (14.0 * low + 4.0);
    col += float3(0.0, 0.902, 1.0) * inten * (lorM + boost) / (sqrt(dM * dM + soft * soft) + th);

    col = pow(max(col, float3(0.0)), float3(1.4));

    // Saturation boost: keeps chromatic bands vivid instead of fading to gray.
    float luma = dot(col, float3(0.299, 0.587, 0.114));
    col = max(mix(float3(luma), col, 1.0), float3(0.0));

    float emT = clamp((abs(yScreen) - 1.0 + EDGE_INSET) / (-max(EDGE_MASK, 0.0001)), 0.0, 1.0);
    float em = emT * emT * (3.0 - 2.0 * emT);
    float sides = pow(env, SIDE_FADE);
    col *= mix(1.0, em * sides, res);
    col *= res;

    // Premultiplied, with alpha from the brightest channel: black becomes transparent so the
    // waveform composites onto VEXO's translucent window instead of masking it.
    float a = clamp(max(max(col.r, col.g), col.b), 0.0, 1.0);
    return half4(half3(clamp(col, float3(0.0), float3(1.0))), half(a));
}
"""

/** Six metaball dots that merge, scatter and gather. */
internal const val SIRI_FLUID_DOTS_SHADER = """
uniform float2 iResolution;
uniform float iTime;
uniform float iAudio;

const float TAU = 6.28318530718;
const float NF = 6.0;
const float SMOOTH_K = 0.08;
const float INTENSITY  = 0.0025;
const float FALLOFF_P  = 1.35;
const float FADE_START = 0.02;
const float FADE_END   = 0.56;
const float ABERR = 0.005;
// vec3(0.0, 0.5, 1.0) * ABERR, folded to a literal so it stays a constant expression in SkSL.
const float3 SPECTRAL = float3(0.0, 0.0025, 0.005);
const float HUE_SPEED = 0.06;
const float COLOR_K   = 0.5;
const float SAT       = 0.01;
const float HUE_SPAN  = 0.667;
const float MERGE_PERIOD = 6.0;
const float T_MOVE   = 1.25;
const float STAGGER  = 0.33;
const float HOLD     = 0.0;
const float W = 4.6;
const float L = 3.2;
const float PIERCE  = 0.12;
const float RECOIL  = 0.035;
const float REC_LAG = 0.11;
const float GATHER_PERIOD = 12.0;
const float GATHER_START  = 9.2;
const float GATHER_HOLD   = 0.8;
const float GATHER_R      = 0.008;
const float GATHER_DIM    = 0.85;
const float GATHER_IN     = 1.8;
const float GATHER_IN_L   = 7.5;
const float BURST_W = 6.5;
const float BURST_L = 4.0;
const float CHARGE_T     = 0.30;
const float CHARGE_SHRK  = 0.18;
const float CHARGE_GLOW  = 0.35;
const float FLASH_GAIN   = 1.2;
const float FLASH_DECAY  = 7.0;

float hash11(float n) { return fract(sin(n * 127.1 + 311.7) * 43758.5453); }

float settleWL(float tau, float w, float l) {
    if (tau <= 0.0) return 0.0;
    return 1.0 - exp(-l * tau) * cos(w * tau);
}

float settle(float tau) { return settleWL(tau, W, L); }

float settleCrit(float tau, float l) {
    if (tau <= 0.0) return 0.0;
    return 1.0 - exp(-l * tau) * (1.0 + l * tau);
}

float smin(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}

float3 hue2rgb(float h) {
    h = fract(h);
    float r = clamp(abs(h * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    float g = clamp(2.0 - abs(h * 6.0 - 2.0), 0.0, 1.0);
    float b = clamp(2.0 - abs(h * 6.0 - 4.0), 0.0, 1.0);
    return float3(r, g, b);
}

float dotR(float fi, float seed, float t) {
    return 0.036 + 0.010 * sin(t * 1.3 + seed * TAU) + 0.005 * sin(t * 2.4 + fi * 1.3);
}

float dotSD(float2 p, float2 pos, float r, float t, float fi, float shapeDamp) {
    float2 d = p - pos;
    float sq = 0.075 * (0.5 + 0.5 * sin(t * 0.9 + fi * 2.0)) * shapeDamp;
    float ca = cos(t * 0.35 + fi);
    float sa = sin(t * 0.35 + fi);
    d = float2x2(ca, -sa, sa, ca) * d;
    d *= float2(1.0 + sq, 1.0 - sq);
    return length(d) - r;
}

float3 scene(float2 p, float t, float audio) {
    float k = floor(t / MERGE_PERIOD);
    float u = fract(t / MERGE_PERIOD);
    float te = u * MERGE_PERIOD;
    float tg = mod(t, GATHER_PERIOD);
    float g = settleCrit((tg - GATHER_START) * GATHER_IN, GATHER_IN_L)
            - settleWL(tg - GATHER_START - GATHER_HOLD, BURST_W, BURST_L);
    float gC = clamp(g, 0.0, 1.0);
    float tb = tg - (GATHER_START + GATHER_HOLD);
    float charge = smoothstep(-CHARGE_T, 0.0, min(tb, 0.0)) * gC;
    float flash = tb > 0.0 ? exp(-tb * FLASH_DECAY) : 0.0;
    // Speech brightens the cluster; at audio = 0 this is exactly the original expression.
    float gBright = mix(1.0, GATHER_DIM, gC)
        * (1.0 + CHARGE_GLOW * charge + FLASH_GAIN * flash + 0.6 * audio);
    float3 total3 = float3(100000.0);
    float3 cAcc = float3(0.0);
    float wAcc = 0.000001;
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float seed = hash11(fi);
        float ang = fi / NF * TAU + t * 0.35;
        float2 dir = float2(cos(ang), sin(ang));
        float R = 0.17 + 0.010 * sin(t * 1.0) + 0.007 * sin(t * 1.3 + seed * TAU);
        float pairId = mod(fi, 3.0);
        float moverLow = mod(k + pairId, 2.0);
        float isMover = (fi < 2.5) ? step(moverLow, 0.5) : step(0.5, moverLow);
        float goStart = pairId * STAGGER;
        float retStart = 3.0 * STAGGER + HOLD + pairId * STAGGER;
        float m = (settle(te - goStart) - settle(te - retStart)) * isMover;
        float rec = (settle(te - goStart - REC_LAG) - settle(te - retStart - REC_LAG))
            * (1.0 - isMover);
        float rSelf = dotR(fi, seed, t);
        rSelf = mix(rSelf, 0.036, gC);
        rSelf *= 1.0 - CHARGE_SHRK * charge;
        float fj = mod(fi + 3.0, 6.0);
        float rPart = dotR(fj, hash11(fj), t);
        float deep = -(R + RECOIL) - PIERCE * rPart;
        float radial = mix(R, deep, m) + RECOIL * rec;
        radial = mix(radial, GATHER_R, g);
        float2 pos = radial * dir;
        float sdR = dotSD(p - SPECTRAL.r * dir, pos, rSelf, t, fi, 1.0 - gC);
        float sdG = dotSD(p - SPECTRAL.g * dir, pos, rSelf, t, fi, 1.0 - gC);
        float sdB = dotSD(p - SPECTRAL.b * dir, pos, rSelf, t, fi, 1.0 - gC);
        total3 = float3(
            smin(total3.r, sdR, SMOOTH_K),
            smin(total3.g, sdG, SMOOTH_K),
            smin(total3.b, sdB, SMOOTH_K)
        );
        float hue = fract(fi / NF + t * HUE_SPEED) * HUE_SPAN;
        float3 dotCol = mix(float3(1.0), hue2rgb(hue), SAT);
        float w = exp(-sdG * COLOR_K);
        cAcc += w * dotCol;
        wAcc += w;
    }
    float3 sd3 = max(total3, float3(0.0)) + float3(0.0001);
    float3 core3 = clamp(float3(INTENSITY) / pow(sd3, float3(FALLOFF_P)), 0.0, 1.0);
    float3 edge3 = float3(1.0) - smoothstep(float3(FADE_START), float3(FADE_END), sd3);
    float3 bright = core3 * edge3 * gBright;
    return bright * (cAcc / wAcc);
}

half4 main(float2 fragCoord) {
    float2 res = iResolution;
    float2 p = (2.0 * fragCoord - res) / min(res.x, res.y);
    float t = iTime;
    float audio = clamp(iAudio, 0.0, 1.0);
    p /= 1.0 + 0.03 * sin(t * 1.0);
    float3 col = scene(p, t, audio);
    col *= 1.0 + 0.05 * sin(t * 1.0 + 1.0);
    col = pow(col, float3(1.0 / 1.2));
    col = min(col, float3(1.0));
    float n = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
    col += float3((n - 0.5) / 255.0);

    float a = clamp(max(max(col.r, col.g), col.b), 0.0, 1.0);
    return half4(half3(clamp(col, float3(0.0), float3(1.0))), half(a));
}
"""
