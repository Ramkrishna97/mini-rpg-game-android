package com.emberfall.game.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader

/** API 33+ additive soft light fields. Canvas fallback is maintained in WorldRenderer. */
@android.annotation.TargetApi(33)
class GpuLighting {
    private val shader = RuntimeShader("""
        uniform float3 lights[32];
        uniform float ambient;
        half4 main(float2 p) {
            float illumination = 0.0;
            for (int i = 0; i < 32; i++) {
                float3 light = lights[i];
                illumination += 1.0 - smoothstep(light.z * 0.15, light.z, distance(p, light.xy));
            }
            float a = ambient * (1.0 - clamp(illumination, 0.0, 1.0));
            return half4(half3(0.055, 0.078, 0.19) * half(a), half(a));
        }
    """)
    private val paint = Paint().apply { this.shader = this@GpuLighting.shader }
    fun draw(canvas: Canvas, width: Float, height: Float, ambient: Float, lights: FloatArray) {
        shader.setFloatUniform("lights", lights)
        shader.setFloatUniform("ambient", ambient)
        canvas.drawRect(0f,0f,width,height,paint)
    }
}
