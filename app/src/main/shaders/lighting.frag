#version 450
// Night darkness with soft light holes. Same formula as GpuLighting.kt's
// RuntimeShader, so the output matches the Canvas GPU path.
layout(set = 0, binding = 0) uniform LightBlock {
    vec4 lights[32];   // xy = screen pixel centre, z = radius in pixels
    vec4 params;       // x = darkness alpha, y = light count
} lb;

layout(location = 0) out vec4 outColor;

void main() {
    float illumination = 0.0;
    int count = int(lb.params.y);
    for (int i = 0; i < count; i++) {
        vec4 l = lb.lights[i];
        illumination += 1.0 - smoothstep(l.z * 0.15, l.z, distance(gl_FragCoord.xy, l.xy));
    }
    float a = lb.params.x * (1.0 - clamp(illumination, 0.0, 1.0));
    // Premultiplied output, blended with ONE / ONE_MINUS_SRC_ALPHA.
    outColor = vec4(vec3(0.055, 0.078, 0.19) * a, a);
}
