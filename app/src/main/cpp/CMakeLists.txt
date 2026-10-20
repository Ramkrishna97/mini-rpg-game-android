cmake_minimum_required(VERSION 3.22.1)
project(emberfall_vk LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(emberfall_vk SHARED
    jni_bridge.cpp
    VulkanRenderer.cpp
    LightingPass.cpp
)

target_compile_definitions(emberfall_vk PRIVATE VK_USE_PLATFORM_ANDROID_KHR)
target_compile_options(emberfall_vk PRIVATE -Wall -Wextra)

find_library(log-lib log REQUIRED)
find_library(android-lib android REQUIRED)
find_library(vulkan-lib vulkan REQUIRED)
target_link_libraries(emberfall_vk ${log-lib} ${android-lib} ${vulkan-lib})

# ---- Shaders: GLSL -> SPIR-V into the APK assets ---------------------------
# If glslc is not found, the prebuilt .spv files already in assets/shaders are used.
set(SHADER_SRC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../shaders")
set(SHADER_OUT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../assets/shaders")
file(MAKE_DIRECTORY "${SHADER_OUT_DIR}")

find_program(GLSLC_EXECUTABLE glslc
    HINTS "${CMAKE_ANDROID_NDK}/shader-tools/linux-x86_64"
          "${CMAKE_ANDROID_NDK}/shader-tools/darwin-x86_64"
          "${CMAKE_ANDROID_NDK}/shader-tools/windows-x86_64")

set(SHADER_NAMES sprite.vert sprite.frag lighting.vert lighting.frag)
if(GLSLC_EXECUTABLE)
    set(SHADER_OUTPUTS "")
    foreach(shader IN LISTS SHADER_NAMES)
        add_custom_command(
            OUTPUT "${SHADER_OUT_DIR}/${shader}.spv"
            COMMAND "${GLSLC_EXECUTABLE}" "${SHADER_SRC_DIR}/${shader}" -o "${SHADER_OUT_DIR}/${shader}.spv"
            DEPENDS "${SHADER_SRC_DIR}/${shader}"
            COMMENT "Compiling ${shader} to SPIR-V")
        list(APPEND SHADER_OUTPUTS "${SHADER_OUT_DIR}/${shader}.spv")
    endforeach()
    add_custom_target(emberfall_shaders DEPENDS ${SHADER_OUTPUTS})
    add_dependencies(emberfall_vk emberfall_shaders)
else()
    message(WARNING "glslc not found: using the prebuilt SPIR-V in app/src/main/assets/shaders")
endif()
