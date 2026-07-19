cmake_minimum_required(VERSION 3.20)

project(
    VirtualMemoryLab
    VERSION 1.0.0
    LANGUAGES CXX
    DESCRIPTION "《虚拟内存》知识库的 8 个可执行验证实验"
)

# 默认 RelWithDebInfo（-O2 -g）：保留优化后的真实性能形态，又能下断点
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(
        CMAKE_BUILD_TYPE RelWithDebInfo
        CACHE STRING "构建类型" FORCE
    )
endif()

# 喂 IntelliSense：compile_commands.json（C/C++ 扩展直接读）
set(
    CMAKE_EXPORT_COMPILE_COMMANDS ON
)

# 根级收编时此开关同样生效；独立打开时也必须在此开启，add_test 才会注册
enable_testing()

# 8 个单文件实验：每个实验 = 一个可执行文件 + 一个 CTest 用例
# 命名即知识库《🧠 虚拟内存》的 Level 序号；
set(VM_EXPERIMENTS
    vm01_address_space_layout     # L1.1 地址空间四大件真实排序
    vm02_base_limit_translation   # L1.4 Base+Limit 翻译簿 & 段错误由来
    vm03_page_table_walk          # L2/L3 PTE 位段 + 四级页表 + 512GB→40KB
    vm04_demand_paging            # L2.4 按需调页三闸门（用 include/vm_probe.h）
    vm05_tlb_sensitivity          # L3.2 TLB 容量边界（工作集阶梯扫描）
    vm06_copy_on_write            # L4.4 fork+COW（用 include/vm_probe.h）
    vm07_page_replacement         # L4.1 FIFO/CLOCK/LRU 对决 + Bélády 异常
    vm08_ept_nested_walk          # L6.1 EPT/NPT 双层翻译最坏代价 20/24
)

foreach(exp IN LISTS VM_EXPERIMENTS)
    add_executable(
        ${exp}
        src/${exp}.cpp
    )

    target_include_directories(
        ${exp}
        PRIVATE
        include
    )

    target_compile_features(
        ${exp}
        PRIVATE
        cxx_std_20
    )

    target_compile_options(
        ${exp}
        PRIVATE
        -Wall
        -Wextra
    )

    add_test(
        NAME ${exp}
        COMMAND ${exp}
    )
endforeach()

# 按知识库 Level 打标签：`ctest -L L4` 只跑第 4 关的实验（配合 LEARNING_PATH 六段台阶）
set_tests_properties(vm01_address_space_layout   PROPERTIES LABELS "L1")
set_tests_properties(vm02_base_limit_translation PROPERTIES LABELS "L1")
set_tests_properties(vm03_page_table_walk        PROPERTIES LABELS "L2;L3")
set_tests_properties(vm04_demand_paging          PROPERTIES LABELS "L2")
set_tests_properties(vm05_tlb_sensitivity        PROPERTIES LABELS "L3")
set_tests_properties(vm06_copy_on_write          PROPERTIES LABELS "L4")
set_tests_properties(vm07_page_replacement       PROPERTIES LABELS "L4")
set_tests_properties(vm08_ept_nested_walk        PROPERTIES LABELS "L6")
