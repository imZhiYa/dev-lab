# =============================================================================
# virtual-memory-demo —— 《🧠 虚拟内存》硬核验证套件（Makefile 备选路径，CI 主用）
# 布局约定：include/ 放头文件，src/ 放 .cpp，bin/ 出产物
# 用法：
#   make            编译全部 8 个实验到 bin/
#   make run        编译并按 Level 顺序依次公审（任一实验 FAIL 即中止）
#   make clean      清理 bin/
# CMake 是本地开发主路径（见 CMakeLists.txt / macos-vscode-kit）；本文件保证
# 零 CMake 环境（含 CI）也能一键验证
# =============================================================================
CXX      ?= c++                              # macOS：c++ 即 Apple clang++；Linux：c++ 即 g++
CPPFLAGS ?= -Iinclude
CXXFLAGS ?= -O2 -std=c++20 -Wall -Wextra

SRC_DIR  := src
BIN_DIR  := bin
SRCS := $(sort $(wildcard $(SRC_DIR)/vm*.cpp))
BINS := $(patsubst $(SRC_DIR)/%.cpp,$(BIN_DIR)/%,$(SRCS))

.PHONY: all run clean
all: $(BINS)

$(BIN_DIR)/%: $(SRC_DIR)/%.cpp include/vm_probe.h | $(BIN_DIR)
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -o $@ $<

$(BIN_DIR):
	mkdir -p $(BIN_DIR)

run: all
	@for b in $(BINS); do \
		echo ""; echo ">>> 公审 $$b"; \
		./$$b || { echo "❌ $$b FAIL"; exit 1; }; \
	done
	@echo ""; echo "✅ virtual-memory-demo 全部 8 个实验公审通过"

clean:
	rm -rf $(BIN_DIR)
