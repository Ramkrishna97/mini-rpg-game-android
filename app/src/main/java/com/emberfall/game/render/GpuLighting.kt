package com.emberfall.game.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader

/**
 * AGSL night-lighting shader (Android 13 / API 33+).
 *
 * A single full-screen fragment pass computes darkness with soft circular light
 * falloff for up to three sources (player torch + nearby campfires), plus a
 * warm tint inside the light pool. The shader returns premultiplied colour so
 * Canvas composites it correctly.
 *
 * On older devices [WorldRenderer] falls back to the pre-shader overlay.
 */
class GpuLighting {

    private val shader = RuntimeShader(AGSL)
    private val paint = Paint()

    /**
     * @param lights packed as x, y, radius for three lights, in screen pixels
     * @param ambient darkness alpha 0..1
     */
    fun draw(canvas: Canvas, width: Float, height: Float, ambient: Float, lights: FloatArray) {
        shader.setFloatUniform("uResolution", width, height)
        shader.setFloatUniform("uLightA", lights[0], lights[1])
        shader.setFloatUniform("uLightB", lights[3], lights[4])
        shader.setFloatUniform("uLightC", lights[6], lights[7])
        shader.setFloatUniform("uRadiusA", lights[2])
        shader.setFloatUniform("uRadiusB", lights[5])
        shader.setFloatUniform("uRadiusC", lights[8])
        shader.setFloatUniform("uAmbient", ambient)

        paint.shader = shader
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private companion object {
        const val AGSL = """
            uniform float2 uResolution;
            uniform float2 uLightA;
            uniform float2 uLightB;
            uniform float2 uLightC;
            uniform float uRadiusA;
            uniform float uRadiusB;
            uniform float uRadiusC;
            uniform float uAmbient;

            float lightMask(float2 p, float2 c, float r) {
                float d = distance(p, c);
                return 1.0 - smoothstep(r * 0.22, r, d);
            }

            half4 main(float2 fragCoord) {
                float mask = max(
                    lightMask(fragCoord, uLightA, uRadiusA),
                    max(lightMask(fragCoord, uLightB, uRadiusB),
                        lightMask(fragCoord, uLightC, uRadiusC))
                );
                mask = clamp(mask, 0.0, 1.0);

                float alpha = uAmbient * (1.0 - 0.92 * mask);
                half3 night = half3(0.055, 0.078, 0.19);
                half3 warm = half3(0.34, 0.26, 0.15);
                half3 tint = mix(night, warm, half(mask) * 0.65);

                // Smooth dithering hides 8-bit banding in the falloff.
                float dither = (fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
                half a = half(alpha);
                return half4(tint * a + half(dither), a);
            }
        """
    }
}
