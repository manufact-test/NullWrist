cmake_minimum_required(VERSION 3.22.1)

project(pebble_runtime LANGUAGES C CXX)

add_library(
    pebble_runtime
    SHARED
    pebble_runtime.cpp
)

target_compile_features(pebble_runtime PRIVATE cxx_std_17)
target_compile_options(pebble_runtime PRIVATE -Wall -Wextra -Werror)

find_library(log-lib log)

target_link_libraries(
    pebble_runtime
    ${log-lib}
)
